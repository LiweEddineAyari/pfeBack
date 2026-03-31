package projet.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.dto.ColumnMeta;
import projet.app.dto.DbConnectionRequest;
import projet.app.dto.ExcelRowDto;
import projet.app.dto.IngestionType;
import projet.app.dto.LoadFromDbResponse;
import projet.app.dto.LoadRequest;
import projet.app.entity.staging.StgComptaRaw;
import projet.app.entity.staging.StgContratRaw;
import projet.app.entity.staging.StgTiersRaw;
import projet.app.repository.staging.StgComptaRawRepository;
import projet.app.repository.staging.StgContratRawRepository;
import projet.app.repository.staging.StgTiersRawRepository;
import projet.app.service.connector.ConnectorFactory;
import projet.app.service.connector.DynamicConnectionFactory;
import projet.app.service.excel.ExcelReaderService;
import projet.app.service.json.JsonReaderService;
import projet.app.service.mapping.ColumnMappingService;
import projet.app.service.sql.SqlFileService;
import projet.app.service.sql.SqlReaderService;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Simple ETL service - reads Excel or SQL files and saves to PostgreSQL staging tables.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtlService {

    private final ExcelReaderService excelReaderService;
    private final JsonReaderService jsonReaderService;
    private final SqlFileService sqlFileService;
    private final SqlReaderService sqlReaderService;
    private final ColumnMappingService columnMappingService;
    private final ConnectorFactory connectorFactory;
    private final DynamicConnectionFactory dynamicConnectionFactory;
    private final JdbcTemplate jdbcTemplate;
    private final StgTiersRawRepository tiersRepository;
    private final StgContratRawRepository contratRepository;
    private final StgComptaRawRepository comptaRepository;

    private static final int BATCH_SIZE = 100;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)?$");
    private static final String TARGET_TIERS = "staging.stg_tiers_raw";
    private static final String TARGET_CONTRAT = "staging.stg_contrat_raw";
    private static final String TARGET_COMPTA = "staging.stg_compta_raw";

    public static class ProcessResult {
        private final int rowCount;
        private final Map<String, String> resolvedMapping;

        public ProcessResult(int rowCount, Map<String, String> resolvedMapping) {
            this.rowCount = rowCount;
            this.resolvedMapping = resolvedMapping;
        }

        public int getRowCount() {
            return rowCount;
        }

        public Map<String, String> getResolvedMapping() {
            return resolvedMapping;
        }
    }

    public List<ColumnMeta> getSourceColumns(DbConnectionRequest request) {
        validateConnectionRequest(request);
        validateTableName(request.getTable(), "source table");
        validateExternalConnection(request);
        return connectorFactory.getConnector(request.getDbType()).getColumns(request);
    }

    public LoadFromDbResponse loadFromDatabase(LoadRequest request) {
        validateLoadRequest(request);
        ensureStagingTablesExist();

        String targetTable = resolveTargetTable(request.getType());
        request.setTargetTable(targetTable);

        List<ColumnMeta> sourceColumns = getSourceColumns(request.getConnection());
        Map<String, String> effectiveMapping = resolveDbLoadMapping(request, sourceColumns);
        request.setMapping(effectiveMapping);

        long beforeCount = countRows(targetTable);

        connectorFactory.getConnector(request.getConnection().getDbType()).loadData(request);

        long afterCount = countRows(targetTable);
        int loadedRows = (int) Math.max(0L, afterCount - beforeCount);

        return LoadFromDbResponse.builder()
                .status("COMPLETED")
                .rowCount(loadedRows)
                .sourceTable(request.getConnection().getTable())
                .targetTable(targetTable)
            .mappingUsed(resolveMappingMode(request.getMapping(), effectiveMapping))
            .mappedColumns(new LinkedHashMap<>(effectiveMapping))
                .build();
    }

    /**
     * Process a file (Excel, JSON, or SQL) and save to staging table.
     * SQL files are now parsed into rows and go through column mapping like other formats.
     */
    @Transactional
    public ProcessResult processFile(Path filePath, String fileType, String dateBal, Map<String, String> columnMapping) {
        ensureStagingTablesExist();
        log.info("Processing file: {}, type: {}, date_bal: {}", filePath, fileType, dateBal);
        
        String fileName = filePath.getFileName().toString().toLowerCase();
        String batchId = java.util.UUID.randomUUID().toString();
        List<ExcelRowDto> rows;
        String resolvedFileType = fileType;
        
        // ── STEP 1: Read file into rows ──────────────────
        if (fileName.endsWith(".sql")) {
            // NEW: parse SQL into rows for mapping support
            try {
                rows = sqlReaderService.readSqlFile(filePath, batchId);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse SQL file: " + filePath, e);
            }
            
            if (rows.isEmpty()) {
                log.warn("SQL parser yielded 0 rows. Falling back to direct SQL execution (no mapping).");
                return new ProcessResult(sqlFileService.executeSqlFile(filePath), Map.of());
            }
            
            // For SQL, auto-detect type from table name if not provided or if "SQL" is passed
            if (resolvedFileType == null || resolvedFileType.isBlank() || "SQL".equalsIgnoreCase(resolvedFileType)) {
                try {
                    resolvedFileType = detectTypeFromSql(filePath);
                    log.info("Auto-detected type from SQL: {}", resolvedFileType);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to detect type from SQL file: " + filePath, e);
                }
            }
            
        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            rows = excelReaderService.readExcelFile(filePath, batchId);
            
        } else if (fileName.endsWith(".json")) {
            rows = jsonReaderService.readJsonFile(filePath, batchId);
            
        } else {
            throw new IllegalArgumentException("Unsupported file format. Use .xlsx, .xls, .sql, or .json");
        }
        
        if (rows.isEmpty()) {
            log.warn("No data rows found in file: {}", filePath);
            return new ProcessResult(0, Map.of());
        }
        
        // ── STEP 2: Resolve and apply column mapping ─────
        List<String> fileColumns = new ArrayList<>(rows.get(0).getData().keySet());
        
        Map<String, String> resolvedMapping = columnMappingService.resolveMapping(
            fileColumns, fileType, columnMapping);
        
        List<ExcelRowDto> mappedRows = rows.stream().map(row -> {
            Map<String, String> mappedData = columnMappingService.applyMapping(
                row.getData(), resolvedMapping, fileType);
            return row.toBuilder().data(mappedData).build();
        }).collect(Collectors.toList());
        
        log.info("Mapped {} rows for type {}", mappedRows.size(), fileType);
        
        // ── STEP 3: Insert into staging ──────────────────
        int savedCount = switch (fileType.toUpperCase()) {
            case "TIERS" -> saveTiersData(mappedRows);
            case "CONTRAT" -> saveContratData(mappedRows);
            case "COMPTA" -> saveComptaData(mappedRows, dateBal);
            default -> throw new IllegalArgumentException("Unknown file type: " + fileType);
        };
        
        log.info("Saved {} records", savedCount);
        return new ProcessResult(savedCount, resolvedMapping);
    }

    /**
     * Auto-detect entity type from SQL table name.
     * INSERT INTO staging.stg_tiers_raw → TIERS
     */
    private String detectTypeFromSql(Path filePath) throws IOException {
        String content = Files.readString(filePath).toLowerCase();
        if (content.contains("stg_tiers")) return "TIERS";
        if (content.contains("stg_contrat")) return "CONTRAT";
        if (content.contains("stg_compta")) return "COMPTA";
        return "TIERS"; // default fallback
    }

    private int saveTiersData(List<ExcelRowDto> rows) {
        List<StgTiersRaw> entities = new ArrayList<>();
        
        for (ExcelRowDto row : rows) {
            Map<String, String> data = row.getData();
            
            StgTiersRaw entity = StgTiersRaw.builder()
                    .idtiers(getValue(data, "idtiers"))
                    .nomprenom(getValue(data, "nomprenom"))
                    .raisonsoc(getValue(data, "raisonsoc"))
                    .residence(getValue(data, "residence"))
                    .agenteco(getValue(data, "agenteco"))
                    .sectionactivite(getValue(data, "sectionactivite"))
                    .chiffreaffaires(getValue(data, "chiffreaffaires"))
                    .nationalite(getValue(data, "nationalite"))
                    .douteux(getValue(data, "douteux"))
                    .datdouteux(getValue(data, "datdouteux"))
                    .grpaffaires(parseInteger(getValue(data, "grpaffaires")))
                    .nomgrpaffaires(getValue(data, "nomgrpaffaires"))
                    .residencenum(parseInteger(getValue(data, "residencenum")))
                    .build();
            
            entities.add(entity);
            
            if (entities.size() >= BATCH_SIZE) {
                tiersRepository.saveAll(entities);
                entities.clear();
            }
        }
        
        if (!entities.isEmpty()) {
            tiersRepository.saveAll(entities);
        }
        
        return rows.size();
    }

    private int saveContratData(List<ExcelRowDto> rows) {
        List<StgContratRaw> entities = new ArrayList<>();
        
        for (ExcelRowDto row : rows) {
            Map<String, String> data = row.getData();
            
            StgContratRaw entity = StgContratRaw.builder()
                    .agence(getValue(data, "agence"))
                    .devise(getValue(data, "devise"))
                    .idcontrat(getValue(data, "idcontrat"))
                    .ancienneteimpaye(getValue(data, "ancienneteimpaye"))
                    .objetfinance(getValue(data, "objetfinance"))
                    .typcontrat(getValue(data, "typcontrat"))
                    .datouv(getValue(data, "datouv"))
                    .datech(getValue(data, "datech"))
                    .idtiers(getValue(data, "idtiers"))
                    .tauxcontrat(getValue(data, "tauxcontrat"))
                    .actif(getValue(data, "actif"))
                    .build();
            
            entities.add(entity);
            
            if (entities.size() >= BATCH_SIZE) {
                contratRepository.saveAll(entities);
                entities.clear();
            }
        }
        
        if (!entities.isEmpty()) {
            contratRepository.saveAll(entities);
        }
        
        return rows.size();
    }

    private int saveComptaData(List<ExcelRowDto> rows, String dateBal) {
        List<StgComptaRaw> entities = new ArrayList<>();
        
        for (ExcelRowDto row : rows) {
            Map<String, String> data = row.getData();
            
            StgComptaRaw entity = StgComptaRaw.builder()
                    .agence(getValue(data, "agence"))
                    .devise(getValue(data, "devise"))
                    .compte(getValue(data, "compte"))
                    .chapitre(getValue(data, "chapitre"))
                    .libellecompte(getValue(data, "libellecompte"))
                    .idtiers(getValue(data, "idtiers"))
                    .soldeorigine(getValue(data, "soldeorigine"))
                    .soldeconvertie(getValue(data, "soldeconvertie"))
                    .devisebbnq(getValue(data, "devisebbnq"))
                    .cumulmvtdb(getValue(data, "cumulmvtdb"))
                    .cumulmvtcr(getValue(data, "cumulmvtcr"))
                    .soldeinitdebmois(getValue(data, "soldeinitdebmois"))
                    .idcontrat(getValue(data, "idcontrat"))
                    .amount(getValue(data, "amount"))
                    .actif(getValue(data, "actif"))
                    .dateBal(dateBal)
                    .build();
            
            entities.add(entity);
            
            if (entities.size() >= BATCH_SIZE) {
                comptaRepository.saveAll(entities);
                entities.clear();
            }
        }
        
        if (!entities.isEmpty()) {
            comptaRepository.saveAll(entities);
        }
        
        return rows.size();
    }

    private String getValue(Map<String, String> data, String key) {
        String value = data.getOrDefault(key, null);
        return cleanNumericString(value);
    }

    /**
     * Clean numeric strings by removing trailing ".0" that Excel adds to integer values.
     * For example: "1232.0" becomes "1232"
     */
    private String cleanNumericString(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        // Remove trailing .0 from numeric values (Excel reads integers as doubles)
        if (value.matches("^-?\\d+\\.0$")) {
            return value.substring(0, value.length() - 2);
        }
        return value;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String cleaned = cleanNumericString(value);
            return Integer.parseInt(cleaned.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void validateLoadRequest(LoadRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        validateConnectionRequest(request.getConnection());
        validateTypeAndDate(request.getType(), request.getDateBal());
        validateTableName(request.getConnection().getTable(), "source table");

        if (request.getMapping() != null) {
            for (Map.Entry<String, String> entry : request.getMapping().entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    throw new IllegalArgumentException("Invalid mapping: source column cannot be empty");
                }
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    throw new IllegalArgumentException("Invalid mapping: target column cannot be empty");
                }
            }
        }
    }

    private Map<String, String> resolveDbLoadMapping(LoadRequest request, List<ColumnMeta> sourceColumns) {
        List<String> sourceColumnNames = sourceColumns.stream()
                .map(ColumnMeta::getColumnName)
                .collect(Collectors.toList());

        Map<String, String> explicitMapping = request.getMapping() == null ? Map.of() : request.getMapping();
        Map<String, String> resolved = columnMappingService.resolveMapping(
                sourceColumnNames,
                request.getType().name(),
                explicitMapping
        );

        List<String> targetSchemaColumns = columnMappingService.getSchemaColumns(request.getType().name());
        Map<String, String> effective = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : resolved.entrySet()) {
            String targetColumn = entry.getValue();
            if (targetColumn != null && targetSchemaColumns.contains(targetColumn.toLowerCase())) {
                effective.put(entry.getKey(), targetColumn.toLowerCase());
            }
        }

        if (effective.isEmpty()) {
            throw new IllegalArgumentException("Invalid mapping: no source columns map to supported target columns for type " + request.getType());
        }

        return effective;
    }

    private String resolveMappingMode(Map<String, String> requestMapping, Map<String, String> effectiveMapping) {
        if (requestMapping == null || requestMapping.isEmpty()) {
            return "auto";
        }
        if (requestMapping.size() < effectiveMapping.size()) {
            return "explicit+auto";
        }
        return "explicit";
    }

    private void validateConnectionRequest(DbConnectionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("connection is required");
        }
        if (request.getDbType() == null) {
            throw new IllegalArgumentException("dbType is required");
        }
        if (isBlank(request.getHost()) || isBlank(request.getDatabase())
                || isBlank(request.getUsername()) || isBlank(request.getPassword())
                || isBlank(request.getTable())) {
            throw new IllegalArgumentException("host, database, username, password, and table are required");
        }
        if (request.getPort() <= 0 || request.getPort() > 65535) {
            throw new IllegalArgumentException("Invalid port");
        }
    }

    private void validateTypeAndDate(IngestionType type, String dateBal) {
        if (type == null) {
            throw new IllegalArgumentException("type is required and must be TIERS, CONTRAT, or COMPTA");
        }
        if (type == IngestionType.COMPTA) {
            if (isBlank(dateBal)) {
                throw new IllegalArgumentException("date_bal is required for COMPTA. Format: dd/MM/yyyy");
            }
            try {
                LocalDate.parse(dateBal, DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid date_bal format. Must be dd/MM/yyyy");
            }
        }
    }

    private void validateExternalConnection(DbConnectionRequest request) {
        try (Connection ignored = dynamicConnectionFactory.createConnection(request)) {
            // Open and close to validate runtime credentials and network reachability.
        } catch (SQLException e) {
            throw new IllegalArgumentException("Failed to connect to source database", e);
        }
    }

    private String resolveTargetTable(IngestionType type) {
        return switch (type) {
            case TIERS -> TARGET_TIERS;
            case CONTRAT -> TARGET_CONTRAT;
            case COMPTA -> TARGET_COMPTA;
        };
    }

    private long countRows(String tableName) {
        validateTableName(tableName, "target table");
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return result == null ? 0L : result;
    }

    private void validateTableName(String tableName, String label) {
        if (isBlank(tableName) || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Invalid " + label + " name");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Ensure staging schema/tables exist before ingestion.
     * This protects ETL writes when Hibernate DDL is disabled or skipped.
     */
    private void ensureStagingTablesExist() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS staging");

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS staging.stg_tiers_raw (
                id BIGSERIAL PRIMARY KEY,
                idtiers TEXT,
                nomprenom TEXT,
                raisonsoc TEXT,
                residence TEXT,
                agenteco TEXT,
                sectionactivite TEXT,
                chiffreaffaires TEXT,
                nationalite TEXT,
                douteux TEXT,
                datdouteux TEXT,
                grpaffaires INTEGER,
                nomgrpaffaires TEXT,
                residencenum INTEGER
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS staging.stg_contrat_raw (
                id BIGSERIAL PRIMARY KEY,
                idcontrat TEXT,
                agence TEXT,
                devise TEXT,
                ancienneteimpaye TEXT,
                objetfinance TEXT,
                typcontrat TEXT,
                datouv TEXT,
                datech TEXT,
                idtiers TEXT,
                tauxcontrat TEXT,
                actif TEXT
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS staging.stg_compta_raw (
                id BIGSERIAL PRIMARY KEY,
                agence TEXT,
                devise TEXT,
                compte TEXT,
                chapitre TEXT,
                libellecompte TEXT,
                idtiers TEXT,
                soldeorigine TEXT,
                soldeconvertie TEXT,
                devisebbnq TEXT,
                cumulmvtdb TEXT,
                cumulmvtcr TEXT,
                soldeinitdebmois TEXT,
                idcontrat TEXT,
                amount TEXT,
                actif TEXT,
                date_bal TEXT
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS staging.data_quality_result_compta (
                id BIGSERIAL PRIMARY KEY,
                null_check_count INTEGER,
                duplicate_count INTEGER,
                type_check_count INTEGER,
                balance_sum BIGINT,
                contrat_relation_check INTEGER,
                tiers_relation_check INTEGER,
                total_issues INTEGER,
                status TEXT,
                executed_at TIMESTAMP
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS staging.data_quality_result_contrat (
                id BIGSERIAL PRIMARY KEY,
                null_check_deleted INTEGER,
                duplicate_deleted INTEGER,
                type_check_deleted INTEGER,
                total_deleted INTEGER,
                status TEXT,
                executed_at TIMESTAMP
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS staging.data_quality_result_tiers (
                id BIGSERIAL PRIMARY KEY,
                null_check_deleted INTEGER,
                duplicate_deleted INTEGER,
                type_check_deleted INTEGER,
                total_deleted INTEGER,
                status TEXT,
                executed_at TIMESTAMP
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS staging_tiers (
                id BIGSERIAL PRIMARY KEY,
                idtiers TEXT,
                nomprenom TEXT,
                raisonsoc TEXT,
                residence TEXT,
                agenteco TEXT,
                sectionactivite TEXT,
                chiffreaffaires TEXT,
                nationalite TEXT,
                douteux TEXT,
                datdouteux TEXT,
                grpaffaires INTEGER,
                nomgrpaffaires TEXT,
                residencenum INTEGER
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS staging_contrat (
                id BIGSERIAL PRIMARY KEY,
                idcontrat TEXT,
                agence TEXT,
                devise TEXT,
                ancienneteimpaye TEXT,
                objetfinance TEXT,
                typcontrat TEXT,
                datouv TEXT,
                datech TEXT,
                idtiers TEXT,
                tauxcontrat TEXT,
                actif TEXT
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS staging_compta (
                id BIGSERIAL PRIMARY KEY,
                agence TEXT,
                devise TEXT,
                compte TEXT,
                chapitre TEXT,
                libellecompte TEXT,
                idtiers TEXT,
                soldeorigine TEXT,
                soldeconvertie TEXT,
                devisebbnq TEXT,
                cumulmvtdb TEXT,
                cumulmvtcr TEXT,
                soldeinitdebmois TEXT,
                idcontrat TEXT,
                amount TEXT,
                actif TEXT,
                date_bal DATE
            )
            """);
    }
}
