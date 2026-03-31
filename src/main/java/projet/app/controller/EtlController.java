package projet.app.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import projet.app.dto.DbConnectionRequest;
import projet.app.dto.LoadFromDbResponse;
import projet.app.dto.LoadRequest;
import projet.app.service.datamart.ComptaDatamartService;
import projet.app.service.datamart.ContratDatamartService;
import projet.app.service.datamart.TiersDatamartService;
import projet.app.service.EtlService;
import projet.app.service.quality.ComptaDataQualityService;
import projet.app.service.quality.ContratDataQualityService;
import projet.app.service.quality.TiersDataQualityService;
import projet.app.service.transform.contrat.ContratTransformService;
import projet.app.service.transform.tiers.TiersTransformService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Simple REST API for ETL extraction.
 */
@Slf4j
@RestController
@RequestMapping("/api/etl")
@RequiredArgsConstructor
public class EtlController {

    private final EtlService etlService;
    private final TiersDataQualityService tiersDataQualityService;
    private final ContratDataQualityService contratDataQualityService;
    private final TiersTransformService tiersTransformService;
    private final ContratTransformService contratTransformService;
    private final ComptaDataQualityService comptaDataQualityService;
    private final TiersDatamartService tiersDatamartService;
    private final ContratDatamartService contratDatamartService;
    private final ComptaDatamartService comptaDatamartService;

    @PostMapping("/columns")
    public ResponseEntity<?> getSourceColumns(@Valid @RequestBody DbConnectionRequest request) {
        try {
            return ResponseEntity.ok(etlService.getSourceColumns(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error fetching source columns: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", "Failed to fetch source columns"
            ));
        }
    }

    @PostMapping("/load-from-db")
    public ResponseEntity<?> loadFromDatabase(@Valid @RequestBody LoadRequest request) {
        try {
            LoadFromDbResponse response = etlService.loadFromDatabase(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error loading data from dynamic source: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", "Failed to load data from source database"
            ));
        }
    }

    /**
     * Process a file via multipart form upload.
     * POST /api/etl/process
     * Form data: file (Excel or SQL file), type (TIERS, CONTRAT, or COMPTA - required for Excel, ignored for SQL)
     */
    @PostMapping("/process")
    public ResponseEntity<?> processFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false) String fileType,
            @RequestParam(value = "date_bal", required = false) String dateBal,
            @RequestParam(value = "mapping", required = false) String mappingJson) {
        
        log.info("Processing uploaded file: {}, type: {}, date_bal: {}", file.getOriginalFilename(), fileType, dateBal);

        Map<String, String> columnMapping = new HashMap<>(); // key = fileColumn, value = dbColumn

        if (mappingJson != null && !mappingJson.isBlank()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                List<Map<String, String>> mappingList = mapper.readValue(
                    mappingJson,
                    new TypeReference<List<Map<String, String>>>() {}
                );
                // Flatten list of single-entry maps into one map
                for (Map<String, String> entry : mappingList) {
                    columnMapping.putAll(entry);
                }
                log.info("Received column mapping: {}", columnMapping);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Invalid mapping JSON format. Expected: [{\"fileColumn\":\"dbColumn\"},...] - " + e.getMessage()
                ));
            }
        }

        String originalFileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String fileName = originalFileName.toLowerCase();
        boolean isSqlFile = fileName.endsWith(".sql");
        boolean isExcelFile = fileName.endsWith(".xlsx") || fileName.endsWith(".xls");
        boolean isJsonFile = fileName.endsWith(".json");

        // Validate file type
        if (!isSqlFile && !isExcelFile && !isJsonFile) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Invalid file format. Must be .xlsx, .xls, .sql, or .json"
            ));
        }

        // Type validation:
        // - SQL files: type is optional (will be auto-detected from table name in INSERT statements)
        // - Excel/JSON files: type is required (TIERS, CONTRAT, or COMPTA)
        if (isExcelFile || isJsonFile) {
            if (fileType == null || fileType.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "ERROR",
                        "message", "Type parameter is required for Excel/JSON files. Must be TIERS, CONTRAT, or COMPTA"
                ));
            }
            String upperType = fileType.toUpperCase();
            if (!upperType.equals("TIERS") && !upperType.equals("CONTRAT") && !upperType.equals("COMPTA")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "ERROR",
                        "message", "Invalid type. Must be TIERS, CONTRAT, or COMPTA"
                ));
            }
            // For COMPTA, date_bal is required and must be dd/MM/yyyy
            if (upperType.equals("COMPTA")) {
                if (dateBal == null || dateBal.isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "status", "ERROR",
                            "message", "date_bal parameter is required for COMPTA files. Format: dd/MM/yyyy"
                    ));
                }
                try {
                    LocalDate.parse(dateBal, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                } catch (DateTimeParseException e) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "status", "ERROR",
                            "message", "Invalid date_bal format. Must be dd/MM/yyyy"
                    ));
                }
            }
        }
        
        // For SQL files with explicit type, validate it
        if (isSqlFile && fileType != null && !fileType.isBlank()) {
            String upperType = fileType.toUpperCase();
            if (!upperType.equals("TIERS") && !upperType.equals("CONTRAT") && !upperType.equals("COMPTA")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "ERROR",
                        "message", "Invalid type. Must be TIERS, CONTRAT, or COMPTA"
                ));
            }
            // For COMPTA, date_bal is required and must be dd/MM/yyyy
            if (upperType.equals("COMPTA")) {
                if (dateBal == null || dateBal.isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "status", "ERROR",
                            "message", "date_bal parameter is required for COMPTA files. Format: dd/MM/yyyy"
                    ));
                }
                try {
                    LocalDate.parse(dateBal, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                } catch (DateTimeParseException e) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "status", "ERROR",
                            "message", "Invalid date_bal format. Must be dd/MM/yyyy"
                    ));
                }
            }
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "File is empty"
            ));
        }

        try {
            // Save uploaded file to temp location
            Path tempDir = Files.createTempDirectory("etl-upload");
            Path tempFile = tempDir.resolve(originalFileName.isBlank() ? "upload.tmp" : originalFileName);
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            String typeToUse = fileType != null ? fileType.toUpperCase() : "SQL";
            projet.app.service.EtlService.ProcessResult processResult = etlService.processFile(tempFile, typeToUse, dateBal, columnMapping);
            int rowCount = processResult.getRowCount();
            Map<String, String> resolvedMapping = processResult.getResolvedMapping();

            String format = isSqlFile ? "SQL" : (isJsonFile ? "JSON" : "EXCEL");
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "rowCount", rowCount,
                    "file", originalFileName,
                    "format", format,
                    "mappingUsed", columnMapping.isEmpty() ? "auto" : "explicit+auto",
                    "mappedColumns", resolvedMapping != null ? resolvedMapping : Map.of()
            ));

        } catch (Exception e) {
            log.error("Error processing file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Execute data quality checks and cleaning on stg_tiers_raw table.
     * POST /api/etl/quality/tiers
     */
    @PostMapping("/quality/tiers")
    public ResponseEntity<?> cleanTiersStaging() {
        log.info("Starting data quality checks on stg_tiers_raw");
        
        try {
            TiersDataQualityService.DataQualityResult result = tiersDataQualityService.cleanStagingTable();
            
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "nullCheckDeleted", result.getNullCheckDeletedCount(),
                    "duplicateDeleted", result.getDuplicateDeletedCount(),
                    "typeCheckDeleted", result.getTypeCheckDeletedCount(),
                    "totalDeleted", result.getTotalDeletedCount()
            ));
            
        } catch (Exception e) {
            log.error("Error during data quality checks: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Execute data quality checks and cleaning on stg_contrat_raw table.
     * POST /api/etl/quality/contrat
     */
    @PostMapping("/quality/contrat")
    public ResponseEntity<?> cleanContratStaging() {
        log.info("Starting data quality checks on stg_contrat_raw");

        try {
            ContratDataQualityService.DataQualityResult result = contratDataQualityService.cleanStagingTable();

            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "nullCheckDeleted", result.getNullCheckDeletedCount(),
                    "duplicateDeleted", result.getDuplicateDeletedCount(),
                    "typeCheckDeleted", result.getTypeCheckDeletedCount(),
                    "totalDeleted", result.getTotalDeletedCount()
            ));

        } catch (Exception e) {
            log.error("Error during contrat data quality checks: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Execute data quality checks on stg_compta_raw table.
     * Counts issues without deleting rows.
     * POST /api/etl/quality/compta
     */
    @PostMapping("/quality/compta")
    public ResponseEntity<?> checkComptaStaging() {
        log.info("Starting data quality checks on stg_compta_raw");

        try {
            ComptaDataQualityService.DataQualityResult result = comptaDataQualityService.cleanStagingTable();

            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "nullCheckCount", result.getNullCheckCount(),
                    "duplicateCount", result.getDuplicateCount(),
                    "typeCheckCount", result.getTypeCheckCount(),
                    "balanceSum", result.getBalanceSum(),
                    "contratRelationCheck", result.getContratRelationCheck(),
                    "tiersRelationCheck", result.getTiersRelationCheck(),
                    "totalIssues", result.getTotalIssues()
            ));

        } catch (Exception e) {
            log.error("Error during compta data quality checks: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch Rule 1 list for COMPTA:
     * rows where any required column is NULL.
     * GET /api/etl/quality/compta/null-check/list
     */
    @GetMapping("/quality/compta/null-check/list")
    public ResponseEntity<?> fetchComptaNullCheckList() {
        try {
            List<Map<String, Object>> rows = comptaDataQualityService.fetchNullCheckList();
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "rule", "nullCheck",
                    "count", rows.size(),
                    "rows", rows
            ));
        } catch (Exception e) {
            log.error("Error fetching COMPTA null-check list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch Rule 2 list for COMPTA:
     * duplicate rows by chapitre, compte, idtiers (keeping first occurrence).
     * GET /api/etl/quality/compta/duplicate/list
     */
    @GetMapping("/quality/compta/duplicate/list")
    public ResponseEntity<?> fetchComptaDuplicateList() {
        try {
            List<Map<String, Object>> rows = comptaDataQualityService.fetchDuplicateList();
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "rule", "duplicate",
                    "count", rows.size(),
                    "rows", rows
            ));
        } catch (Exception e) {
            log.error("Error fetching COMPTA duplicate list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch Rule 3 list for COMPTA:
     * rows with invalid data types.
     * GET /api/etl/quality/compta/type-check/list
     */
    @GetMapping("/quality/compta/type-check/list")
    public ResponseEntity<?> fetchComptaTypeCheckList() {
        try {
            List<Map<String, Object>> rows = comptaDataQualityService.fetchTypeCheckList();
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "rule", "typeCheck",
                    "count", rows.size(),
                    "rows", rows
            ));
        } catch (Exception e) {
            log.error("Error fetching COMPTA type-check list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch Rule 5a list for COMPTA:
     * rows where idcontrat does not exist in stg_contrat_raw.
     * GET /api/etl/quality/compta/contrat-relation-check/list
     */
    @GetMapping("/quality/compta/contrat-relation-check/list")
    public ResponseEntity<?> fetchComptaContratRelationCheckList() {
        try {
            List<Map<String, Object>> rows = comptaDataQualityService.fetchContratRelationCheckList();
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "rule", "contratRelationCheck",
                    "count", rows.size(),
                    "rows", rows
            ));
        } catch (Exception e) {
            log.error("Error fetching COMPTA contrat-relation-check list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch Rule 5b list for COMPTA:
     * rows where idtiers does not exist in stg_tiers_raw.
     * GET /api/etl/quality/compta/tiers-relation-check/list
     */
    @GetMapping("/quality/compta/tiers-relation-check/list")
    public ResponseEntity<?> fetchComptaTiersRelationCheckList() {
        try {
            List<Map<String, Object>> rows = comptaDataQualityService.fetchTiersRelationCheckList();
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "rule", "tiersRelationCheck",
                    "count", rows.size(),
                    "rows", rows
            ));
        } catch (Exception e) {
            log.error("Error fetching COMPTA tiers-relation-check list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Execute transformations on stg_tiers_raw table.
     * POST /api/etl/transform/tiers
     */
    @PostMapping("/transform/tiers")
    public ResponseEntity<?> transformTiersStaging() {
        log.info("Starting transformation of stg_tiers_raw");

        try {
            int count = tiersTransformService.transformStagingTable();

            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "rowsTransformed", count
            ));

        } catch (Exception e) {
            log.error("Error during transformation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Execute transformations on stg_contrat_raw table.
     * POST /api/etl/transform/contrat
     */
    @PostMapping("/transform/contrat")
    public ResponseEntity<?> transformContratStaging() {
        log.info("Starting transformation of stg_contrat_raw");

        try {
            int count = contratTransformService.transformStagingTable();

            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "rowsTransformed", count
            ));

        } catch (Exception e) {
            log.error("Error during contrat transformation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Load TIERS datamart dimensions from staging.stg_tiers_raw.
     * POST /api/etl/datamart/tiers
     */
    @PostMapping("/datamart/tiers")
    public ResponseEntity<?> loadTiersDatamart() {
        log.info("Starting datamart load for TIERS");

        try {
            TiersDatamartService.LoadResult result = tiersDatamartService.loadTiersDatamart();

            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "subDimResidenceRows", result.getSubDimResidenceRows(),
                    "subDimAgentecoRows", result.getSubDimAgentecoRows(),
                    "subDimDouteuxRows", result.getSubDimDouteuxRows(),
                    "subDimGrpaffaireRows", result.getSubDimGrpaffaireRows(),
                    "subDimSectionactiviteRows", result.getSubDimSectionactiviteRows(),
                    "dimClientRows", result.getDimClientRows()
            ));

        } catch (Exception e) {
            log.error("Error during TIERS datamart load: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch paginated DIM CLIENT list with joins on TIERS dimensions.
     * GET /api/etl/datamart/tiers/client/list?page=0&size=20
     */
    @GetMapping("/datamart/tiers/client/list")
    public ResponseEntity<?> fetchClientDatamartList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(tiersDatamartService.fetchClientList(page, size));
        } catch (Exception e) {
            log.error("Error fetching datamart dim_client list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch paginated SUB DIM RESIDENCE list.
     * GET /api/etl/datamart/tiers/residence/list?page=0&size=20
     */
    @GetMapping("/datamart/tiers/residence/list")
    public ResponseEntity<?> fetchResidenceDatamartList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(tiersDatamartService.fetchResidenceList(page, size));
        } catch (Exception e) {
            log.error("Error fetching datamart sub_dim_residence list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch paginated SUB DIM AGENTECO list.
     * GET /api/etl/datamart/tiers/agenteco/list?page=0&size=20
     */
    @GetMapping("/datamart/tiers/agenteco/list")
    public ResponseEntity<?> fetchAgentecoDatamartList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(tiersDatamartService.fetchAgentecoList(page, size));
        } catch (Exception e) {
            log.error("Error fetching datamart sub_dim_agenteco list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch paginated SUB DIM DOUTEUX list.
     * GET /api/etl/datamart/tiers/douteux/list?page=0&size=20
     */
    @GetMapping("/datamart/tiers/douteux/list")
    public ResponseEntity<?> fetchDouteuxDatamartList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(tiersDatamartService.fetchDouteuxList(page, size));
        } catch (Exception e) {
            log.error("Error fetching datamart sub_dim_douteux list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch paginated SUB DIM GRP AFFAIRE list.
     * GET /api/etl/datamart/tiers/grpaffaire/list?page=0&size=20
     */
    @GetMapping("/datamart/tiers/grpaffaire/list")
    public ResponseEntity<?> fetchGrpAffaireDatamartList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(tiersDatamartService.fetchGrpAffaireList(page, size));
        } catch (Exception e) {
            log.error("Error fetching datamart sub_dim_grpaffaire list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch paginated SUB DIM SECTION ACTIVITE list.
     * GET /api/etl/datamart/tiers/sectionactivite/list?page=0&size=20
     */
    @GetMapping("/datamart/tiers/sectionactivite/list")
    public ResponseEntity<?> fetchSectionActiviteDatamartList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(tiersDatamartService.fetchSectionActiviteList(page, size));
        } catch (Exception e) {
            log.error("Error fetching datamart sub_dim_sectionactivite list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Load CONTRAT datamart dimensions from staging.stg_contrat_raw.
     * POST /api/etl/datamart/contrat
     */
    @PostMapping("/datamart/contrat")
    public ResponseEntity<?> loadContratDatamart() {
        log.info("Starting datamart load for CONTRAT");

        try {
            ContratDatamartService.LoadResult result = contratDatamartService.loadContratDatamart();

            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "subDimAgenceRows", result.getSubDimAgenceRows(),
                    "subDimDeviseRows", result.getSubDimDeviseRows(),
                    "subDimObjetfinanceRows", result.getSubDimObjetfinanceRows(),
                    "subDimTypcontratRows", result.getSubDimTypcontratRows(),
                    "subDimDateRows", result.getSubDimDateRows(),
                    "dimContratRows", result.getDimContratRows()
            ));

        } catch (Exception e) {
            log.error("Error during CONTRAT datamart load: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch paginated DIM CONTRAT list with joins on CONTRAT dimensions and client.
     * GET /api/etl/datamart/contrat/list?page=0&size=20
     */
    @GetMapping("/datamart/contrat/list")
    public ResponseEntity<?> fetchContratDatamartList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(contratDatamartService.fetchContratList(page, size));
        } catch (Exception e) {
            log.error("Error fetching datamart dim_contrat list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch paginated SUB DIM AGENCE list.
     * GET /api/etl/datamart/contrat/agence/list?page=0&size=20
     */
    @GetMapping("/datamart/contrat/agence/list")
    public ResponseEntity<?> fetchAgenceDatamartList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(contratDatamartService.fetchAgenceList(page, size));
        } catch (Exception e) {
            log.error("Error fetching datamart sub_dim_agence list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch paginated SUB DIM DEVISE list.
     * GET /api/etl/datamart/contrat/devise/list?page=0&size=20
     */
    @GetMapping("/datamart/contrat/devise/list")
    public ResponseEntity<?> fetchDeviseDatamartList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(contratDatamartService.fetchDeviseList(page, size));
        } catch (Exception e) {
            log.error("Error fetching datamart sub_dim_devise list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch paginated SUB DIM OBJET FINANCE list.
     * GET /api/etl/datamart/contrat/objetfinance/list?page=0&size=20
     */
    @GetMapping("/datamart/contrat/objetfinance/list")
    public ResponseEntity<?> fetchObjetFinanceDatamartList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(contratDatamartService.fetchObjetFinanceList(page, size));
        } catch (Exception e) {
            log.error("Error fetching datamart sub_dim_objetfinance list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch paginated SUB DIM TYPE CONTRAT list.
     * GET /api/etl/datamart/contrat/typecontrat/list?page=0&size=20
     */
    @GetMapping("/datamart/contrat/typecontrat/list")
    public ResponseEntity<?> fetchTypeContratDatamartList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(contratDatamartService.fetchTypeContratList(page, size));
        } catch (Exception e) {
            log.error("Error fetching datamart sub_dim_typcontrat list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch paginated SUB DIM DATE list.
     * GET /api/etl/datamart/contrat/date/list?page=0&size=20
     */
    @GetMapping("/datamart/contrat/date/list")
    public ResponseEntity<?> fetchDateDatamartList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(contratDatamartService.fetchDateList(page, size));
        } catch (Exception e) {
            log.error("Error fetching datamart sub_dim_date list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Load COMPTA datamart fact and dimensions from staging.stg_compta_raw.
     * POST /api/etl/datamart/compta
     */
    @PostMapping("/datamart/compta")
    public ResponseEntity<?> loadComptaDatamart() {
        log.info("Starting datamart load for COMPTA");

        try {
            ComptaDatamartService.LoadResult result = comptaDatamartService.loadComptaDatamart();

            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "subDimAgenceRows", result.getSubDimAgenceRows(),
                    "subDimDeviseRows", result.getSubDimDeviseRows(),
                    "subDimChapitreRows", result.getSubDimChapitreRows(),
                    "subDimCompteRows", result.getSubDimCompteRows(),
                    "subDimDateRows", result.getSubDimDateRows(),
                    "factBalanceRows", result.getFactBalanceRows()
            ));

        } catch (Exception e) {
            log.error("Error during COMPTA datamart load: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch paginated FACT BALANCE list with joins on COMPTA dimensions.
     * GET /api/etl/datamart/compta/balance/list?page=0&size=20
     */
    @GetMapping("/datamart/compta/balance/list")
    public ResponseEntity<?> fetchBalanceDatamartList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(comptaDatamartService.fetchBalanceList(page, size));
        } catch (Exception e) {
            log.error("Error fetching datamart fact_balance list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch paginated SUB DIM COMPTE list.
     * GET /api/etl/datamart/compta/compte/list?page=0&size=20
     */
    @GetMapping("/datamart/compta/compte/list")
    public ResponseEntity<?> fetchCompteDatamartList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(comptaDatamartService.fetchCompteList(page, size));
        } catch (Exception e) {
            log.error("Error fetching datamart sub_dim_compte list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch paginated SUB DIM CHAPITRE list.
     * GET /api/etl/datamart/compta/chapitre/list?page=0&size=20
     */
    @GetMapping("/datamart/compta/chapitre/list")
    public ResponseEntity<?> fetchChapitreDatamartList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(comptaDatamartService.fetchChapitreList(page, size));
        } catch (Exception e) {
            log.error("Error fetching datamart sub_dim_chapitre list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Health check.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
