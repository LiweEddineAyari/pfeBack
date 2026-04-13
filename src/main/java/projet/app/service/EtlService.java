package projet.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import projet.app.dto.ColumnMeta;
import projet.app.dto.DbConnectionRequest;
import projet.app.dto.IngestionType;
import projet.app.dto.LoadFromDbRequest;
import projet.app.dto.LoadFromDbResponse;
import projet.app.dto.LoadRequest;
import projet.app.dto.SourceTableDetailsResponse;
import projet.app.entity.staging.MappingConfig;
import projet.app.service.connector.ConnectorFactory;
import projet.app.service.connector.DynamicConnectionFactory;
import projet.app.service.mapping.MappingConfigService;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * ETL service for database-to-database ingestion driven by mapping.mapping_config.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtlService {

    private final ConnectorFactory connectorFactory;
    private final DynamicConnectionFactory dynamicConnectionFactory;
    private final MappingConfigService mappingConfigService;
    private final JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)?$");
    private static final Pattern COLUMN_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    private static final Pattern SCHEMA_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    private static final String TARGET_TIERS = "staging.stg_tiers_raw";
    private static final String TARGET_CONTRAT = "staging.stg_contrat_raw";
    private static final String TARGET_COMPTA = "staging.stg_compta_raw";

    public List<SourceTableDetailsResponse> getSourceTablesWithColumns(DbConnectionRequest request) {
        validateConnectionRequest(request);
        String schemaFilter = normalizeSchemaFilter(request.getSchema());

        try (Connection connection = dynamicConnectionFactory.createConnection(request)) {
            DatabaseMetaData metaData = connection.getMetaData();
            List<SourceTableDetailsResponse> tables = new ArrayList<>();

            try (ResultSet tableRs = metaData.getTables(connection.getCatalog(), schemaFilter, "%", new String[]{"TABLE"})) {
                while (tableRs.next()) {
                    String schema = tableRs.getString("TABLE_SCHEM");
                    String tableName = tableRs.getString("TABLE_NAME");

                    if (schemaFilter == null && isSystemSchema(schema)) {
                        continue;
                    }

                    List<ColumnMeta> columns = new ArrayList<>();
                    try (ResultSet columnRs = metaData.getColumns(connection.getCatalog(), schema, tableName, "%")) {
                        while (columnRs.next()) {
                            String nullable = columnRs.getString("IS_NULLABLE");
                            columns.add(new ColumnMeta(
                                    columnRs.getString("COLUMN_NAME"),
                                    columnRs.getString("TYPE_NAME"),
                                    "YES".equalsIgnoreCase(nullable)
                            ));
                        }
                    }

                    tables.add(SourceTableDetailsResponse.builder()
                            .schema(schema)
                            .tableName(tableName)
                            .qualifiedTable(buildQualifiedTable(schema, tableName))
                            .columns(columns)
                            .build());
                }
            }

            tables.sort(Comparator.comparing(SourceTableDetailsResponse::getQualifiedTable, String.CASE_INSENSITIVE_ORDER));
            return tables;
        } catch (SQLException e) {
            throw new IllegalArgumentException("Failed to fetch source tables and columns", e);
        }
    }

    public LoadFromDbResponse loadFromDatabase(LoadFromDbRequest request) {
        validateLoadRequest(request);
        validateExternalConnection(request.getConnection());
        ensureStagingTablesExist();

        List<MappingConfig> mappings = mappingConfigService.findByConfigGroupNumber(request.getConfigGroupNumber());
        if (mappings.isEmpty()) {
            throw new IllegalArgumentException("No mapping configuration found for configGroupNumber: " + request.getConfigGroupNumber());
        }

        Map<String, TableLoadPlan> plansByTarget = buildLoadPlans(mappings);
        requireAllTargets(plansByTarget);
        validateDateForComptaIfNeeded(request.getDateBal(), plansByTarget);

        int totalLoadedRows = 0;
        Map<String, Map<String, Object>> tableResults = new LinkedHashMap<>();

        for (String targetTable : List.of(TARGET_TIERS, TARGET_CONTRAT, TARGET_COMPTA)) {
            TableLoadPlan plan = plansByTarget.get(targetTable);

            LoadRequest connectorRequest = buildConnectorRequest(request, plan);
            long beforeCount = countRows(plan.targetTable());

            connectorFactory.getConnector(connectorRequest.getConnection().getDbType()).loadData(connectorRequest);

            long afterCount = countRows(plan.targetTable());
            int loadedRows = (int) Math.max(0L, afterCount - beforeCount);
            totalLoadedRows += loadedRows;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sourceTable", plan.sourceTable());
            result.put("targetTable", plan.targetTable());
            result.put("rowCount", loadedRows);
            result.put("mappedColumns", new LinkedHashMap<>(plan.columnMapping()));
            tableResults.put(plan.type().name(), result);

            log.info(
                    "Load completed for {}: source={}, target={}, rows={}",
                    plan.type(),
                    plan.sourceTable(),
                    plan.targetTable(),
                    loadedRows
            );
        }

        return LoadFromDbResponse.builder()
                .status("COMPLETED")
                .configGroupNumber(request.getConfigGroupNumber())
                .rowCount(totalLoadedRows)
                .sourceTable("MULTI")
                .targetTable("MULTI")
                .mappingUsed("mapping_config")
                .mappedColumns(Map.of())
                .tableResults(tableResults)
                .build();
    }

    private Map<String, TableLoadPlan> buildLoadPlans(List<MappingConfig> mappings) {
        Map<String, List<MappingConfig>> groupedByTarget = new LinkedHashMap<>();

        for (MappingConfig mapping : mappings) {
            String normalizedTarget = normalizeTargetTable(mapping.getTableTarget());
            groupedByTarget.computeIfAbsent(normalizedTarget, ignored -> new ArrayList<>()).add(mapping);
        }

        Map<String, TableLoadPlan> plans = new LinkedHashMap<>();

        for (Map.Entry<String, List<MappingConfig>> entry : groupedByTarget.entrySet()) {
            String targetTable = entry.getKey();
            List<MappingConfig> groupMappings = entry.getValue();

            IngestionType type = resolveTypeByTarget(targetTable);
            String sourceTable = requireTableName(groupMappings.get(0).getTableSource(), "source table");

            Map<String, String> columnMapping = new LinkedHashMap<>();
            for (MappingConfig mapping : groupMappings) {
                String rowSourceTable = requireTableName(mapping.getTableSource(), "source table");
                if (!rowSourceTable.equalsIgnoreCase(sourceTable)) {
                    throw new IllegalArgumentException(
                            "Inconsistent source tables for target " + targetTable + " in config group"
                    );
                }

                String sourceColumn = requireColumnName(mapping.getColumnSource(), "source column");
                String targetColumn = requireColumnName(mapping.getColumnTarget(), "target column");

                String previous = columnMapping.putIfAbsent(sourceColumn, targetColumn);
                if (previous != null && !previous.equalsIgnoreCase(targetColumn)) {
                    throw new IllegalArgumentException(
                            "Conflicting mapping for source column " + sourceColumn + " in target " + targetTable
                    );
                }
            }

            if (columnMapping.isEmpty()) {
                throw new IllegalArgumentException("No column mappings found for target " + targetTable);
            }

            plans.put(targetTable, new TableLoadPlan(type, sourceTable, targetTable, columnMapping));
        }

        return plans;
    }

    private void requireAllTargets(Map<String, TableLoadPlan> plansByTarget) {
        List<String> missingTargets = new ArrayList<>();

        if (!plansByTarget.containsKey(TARGET_TIERS)) {
            missingTargets.add(TARGET_TIERS);
        }
        if (!plansByTarget.containsKey(TARGET_CONTRAT)) {
            missingTargets.add(TARGET_CONTRAT);
        }
        if (!plansByTarget.containsKey(TARGET_COMPTA)) {
            missingTargets.add(TARGET_COMPTA);
        }

        if (!missingTargets.isEmpty()) {
            throw new IllegalArgumentException(
                    "Mapping config group must include mappings for all targets (TIERS, CONTRAT, COMPTA). Missing: "
                            + String.join(", ", missingTargets)
            );
        }
    }

    private void validateLoadRequest(LoadFromDbRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.getConfigGroupNumber() == null || request.getConfigGroupNumber() <= 0) {
            throw new IllegalArgumentException("configGroupNumber is required and must be > 0");
        }
        validateConnectionRequest(request.getConnection());
    }

    private void validateConnectionRequest(DbConnectionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("connection is required");
        }
        if (request.getDbType() == null) {
            throw new IllegalArgumentException("dbType is required");
        }
        if (isBlank(request.getHost()) || isBlank(request.getDatabase())
                || isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            throw new IllegalArgumentException("host, database, username, and password are required");
        }
        if (request.getPort() <= 0 || request.getPort() > 65535) {
            throw new IllegalArgumentException("Invalid port");
        }
        if (!isBlank(request.getSchema()) && !SCHEMA_NAME_PATTERN.matcher(request.getSchema().trim()).matches()) {
            throw new IllegalArgumentException("Invalid schema name");
        }
    }

    private void validateDateForComptaIfNeeded(String dateBal, Map<String, TableLoadPlan> plansByTarget) {
        if (!plansByTarget.containsKey(TARGET_COMPTA)) {
            return;
        }
        if (isBlank(dateBal)) {
            throw new IllegalArgumentException("date_bal is required when loading COMPTA. Format: dd/MM/yyyy");
        }
        try {
            LocalDate.parse(dateBal, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date_bal format. Must be dd/MM/yyyy");
        }
    }

    private LoadRequest buildConnectorRequest(LoadFromDbRequest request, TableLoadPlan plan) {
        DbConnectionRequest sourceConnection = new DbConnectionRequest();
        sourceConnection.setHost(request.getConnection().getHost());
        sourceConnection.setPort(request.getConnection().getPort());
        sourceConnection.setDatabase(request.getConnection().getDatabase());
        sourceConnection.setDbType(request.getConnection().getDbType());
        sourceConnection.setUsername(request.getConnection().getUsername());
        sourceConnection.setPassword(request.getConnection().getPassword());
        sourceConnection.setTable(plan.sourceTable());

        LoadRequest connectorRequest = new LoadRequest();
        connectorRequest.setConnection(sourceConnection);
        connectorRequest.setType(plan.type());
        connectorRequest.setDateBal(request.getDateBal());
        connectorRequest.setTargetTable(plan.targetTable());
        connectorRequest.setMapping(new LinkedHashMap<>(plan.columnMapping()));

        return connectorRequest;
    }

    private IngestionType resolveTypeByTarget(String targetTable) {
        if (TARGET_TIERS.equalsIgnoreCase(targetTable)) {
            return IngestionType.TIERS;
        }
        if (TARGET_CONTRAT.equalsIgnoreCase(targetTable)) {
            return IngestionType.CONTRAT;
        }
        if (TARGET_COMPTA.equalsIgnoreCase(targetTable)) {
            return IngestionType.COMPTA;
        }
        throw new IllegalArgumentException("Unsupported target table for ingestion type resolution: " + targetTable);
    }

    private String normalizeTargetTable(String tableTarget) {
        String normalized = requireTableName(tableTarget, "target table").toLowerCase();

        if ("stg_tiers_raw".equals(normalized) || TARGET_TIERS.equals(normalized)) {
            return TARGET_TIERS;
        }
        if ("stg_contrat_raw".equals(normalized) || TARGET_CONTRAT.equals(normalized)) {
            return TARGET_CONTRAT;
        }
        if ("stg_compta_raw".equals(normalized) || TARGET_COMPTA.equals(normalized)) {
            return TARGET_COMPTA;
        }

        throw new IllegalArgumentException(
                "Unsupported target table in mapping_config: " + tableTarget
                        + ". Allowed targets: stg_tiers_raw, stg_contrat_raw, stg_compta_raw"
        );
    }

    private String requireTableName(String tableName, String label) {
        if (isBlank(tableName) || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Invalid " + label + " name: " + tableName);
        }
        return tableName.trim();
    }

    private String requireColumnName(String columnName, String label) {
        if (isBlank(columnName) || !COLUMN_NAME_PATTERN.matcher(columnName.trim()).matches()) {
            throw new IllegalArgumentException("Invalid " + label + " name: " + columnName);
        }
        return columnName.trim();
    }

    private void validateExternalConnection(DbConnectionRequest request) {
        try (Connection ignored = dynamicConnectionFactory.createConnection(request)) {
            // Open and close to validate runtime credentials and network reachability.
        } catch (SQLException e) {
            throw new IllegalArgumentException("Failed to connect to source database", e);
        }
    }

    private long countRows(String tableName) {
        requireTableName(tableName, "target table");
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return result == null ? 0L : result;
    }

    private String buildQualifiedTable(String schema, String tableName) {
        if (isBlank(schema)) {
            return tableName;
        }
        return schema + "." + tableName;
    }

    private String normalizeSchemaFilter(String schema) {
        if (isBlank(schema)) {
            return null;
        }
        return schema.trim();
    }

    private boolean isSystemSchema(String schema) {
        if (isBlank(schema)) {
            return false;
        }

        String value = schema.toLowerCase();
        return value.startsWith("pg_")
                || "information_schema".equals(value)
                || "mysql".equals(value)
                || "performance_schema".equals(value)
                || "sys".equals(value)
                || "system".equals(value)
                || "xdb".equals(value)
                || "sysaux".equals(value);
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
            CREATE TABLE IF NOT EXISTS staging.staging_tiers (
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
            CREATE TABLE IF NOT EXISTS staging.staging_contrat (
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
            CREATE TABLE IF NOT EXISTS staging.staging_compta (
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

    private record TableLoadPlan(
            IngestionType type,
            String sourceTable,
            String targetTable,
            Map<String, String> columnMapping
    ) {
    }
}
