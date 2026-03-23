package projet.app.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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

        String fileName = file.getOriginalFilename().toLowerCase();
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
            Path tempFile = tempDir.resolve(file.getOriginalFilename());
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            String typeToUse = fileType != null ? fileType.toUpperCase() : "SQL";
            projet.app.service.EtlService.ProcessResult processResult = etlService.processFile(tempFile, typeToUse, dateBal, columnMapping);
            int rowCount = processResult.getRowCount();
            Map<String, String> resolvedMapping = processResult.getResolvedMapping();

            String format = isSqlFile ? "SQL" : (isJsonFile ? "JSON" : "EXCEL");
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "rowCount", rowCount,
                    "file", file.getOriginalFilename(),
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
     * Health check.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
