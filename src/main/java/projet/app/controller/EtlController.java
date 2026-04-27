package projet.app.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import projet.app.dto.LoadFromDbRequest;
import projet.app.dto.LoadFromDbResponse;
import projet.app.dto.SourceMetadataRequest;
import projet.app.dto.SourceTableDetailsResponse;
import projet.app.service.datamart.ComptaDatamartService;
import projet.app.service.datamart.ContratDatamartService;
import projet.app.service.datamart.TiersDatamartService;
import projet.app.service.EtlService;
import projet.app.service.quality.ComptaDataQualityService;
import projet.app.service.quality.ContratDataQualityService;
import projet.app.service.quality.TiersDataQualityService;
import projet.app.service.transform.contrat.ContratTransformService;
import projet.app.service.transform.tiers.TiersTransformService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final JdbcTemplate jdbcTemplate;

    /**
     * Load TIERS, CONTRAT and COMPTA staging tables in one call.
        * Mapping is resolved from mapping.mapping_config by configGroupNumber.
     */
    @PostMapping("/load-from-db")
    public ResponseEntity<?> loadFromDatabase(@Valid @RequestBody LoadFromDbRequest request) {
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
     * Fetch source database tables and their columns using dynamic connection settings.
        * Optionally provide connection.schema to restrict results to one schema.
     * POST /api/etl/source/tables-columns
     */
    @PostMapping("/source/tables-columns")
    public ResponseEntity<?> fetchSourceTablesAndColumns(@Valid @RequestBody SourceMetadataRequest request) {
        try {
            List<SourceTableDetailsResponse> tables = etlService.getSourceTablesWithColumns(request.getConnection());
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "tableCount", tables.size(),
                    "tables", tables
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error fetching source tables and columns: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", "Failed to fetch source tables and columns"
            ));
        }
    }

    /**
     * Execute quality + transform pipeline in strict order:
     * 1. TIERS quality, then TIERS transform
     * 2. CONTRAT quality, then CONTRAT transform
     * 3. COMPTA quality
     */
    @PostMapping("/quality_transform")
    public ResponseEntity<?> runQualityTransformPipeline() {
        log.info("Starting merged quality/transform pipeline");

        long pipelineStart = System.currentTimeMillis();
        Map<String, Object> tableResults = new LinkedHashMap<>();
        String lastCompletedStep = "none";

        try {
            // TIERS pipeline
            long tiersStart = System.currentTimeMillis();
            TiersDataQualityService.DataQualityResult tiersQuality = tiersDataQualityService.cleanStagingTable();
            lastCompletedStep = "TIERS.quality";

            int tiersTransformed = tiersTransformService.transformStagingTable();
            lastCompletedStep = "TIERS.transform";

            Map<String, Object> tiersResult = new LinkedHashMap<>();
            tiersResult.put("quality", Map.of(
                "nullCheckDeleted", tiersQuality.getNullCheckDeletedCount(),
                "duplicateDeleted", tiersQuality.getDuplicateDeletedCount(),
                "typeCheckDeleted", tiersQuality.getTypeCheckDeletedCount(),
                "totalDeleted", tiersQuality.getTotalDeletedCount()
            ));
            tiersResult.put("transform", Map.of(
                "rowsTransformed", tiersTransformed
            ));
            tiersResult.put("durationMs", System.currentTimeMillis() - tiersStart);
            tableResults.put("TIERS", tiersResult);

            // CONTRAT pipeline
            long contratStart = System.currentTimeMillis();
            ContratDataQualityService.DataQualityResult contratQuality = contratDataQualityService.cleanStagingTable();
            lastCompletedStep = "CONTRAT.quality";

            int contratTransformed = contratTransformService.transformStagingTable();
            lastCompletedStep = "CONTRAT.transform";

            Map<String, Object> contratResult = new LinkedHashMap<>();
            contratResult.put("quality", Map.of(
                "nullCheckDeleted", contratQuality.getNullCheckDeletedCount(),
                "duplicateDeleted", contratQuality.getDuplicateDeletedCount(),
                "typeCheckDeleted", contratQuality.getTypeCheckDeletedCount(),
                "totalDeleted", contratQuality.getTotalDeletedCount()
            ));
            contratResult.put("transform", Map.of(
                "rowsTransformed", contratTransformed
            ));
            contratResult.put("durationMs", System.currentTimeMillis() - contratStart);
            tableResults.put("CONTRAT", contratResult);

            // COMPTA pipeline
            long comptaStart = System.currentTimeMillis();
            ComptaDataQualityService.DataQualityResult comptaQuality = comptaDataQualityService.cleanStagingTable();
            lastCompletedStep = "COMPTA.quality";

            Map<String, Object> comptaResult = new LinkedHashMap<>();
            comptaResult.put("quality", Map.of(
                "nullCheckCount", comptaQuality.getNullCheckCount(),
                "duplicateCount", comptaQuality.getDuplicateCount(),
                "typeCheckCount", comptaQuality.getTypeCheckCount(),
                "balanceSum", comptaQuality.getBalanceSum(),
                "contratRelationCheck", comptaQuality.getContratRelationCheck(),
                "tiersRelationCheck", comptaQuality.getTiersRelationCheck(),
                "totalIssues", comptaQuality.getTotalIssues()
            ));
            comptaResult.put("durationMs", System.currentTimeMillis() - comptaStart);
            tableResults.put("COMPTA", comptaResult);

            return ResponseEntity.ok(Map.of(
                "status", "COMPLETED",
                "sequence", List.of(
                    "TIERS.quality",
                    "TIERS.transform",
                    "CONTRAT.quality",
                    "CONTRAT.transform",
                    "COMPTA.quality"
                ),
                "totalDurationMs", System.currentTimeMillis() - pipelineStart,
                "tables", tableResults
            ));

        } catch (Exception e) {
            log.error("Error during merged quality/transform pipeline after step {}: {}", lastCompletedStep, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "ERROR",
                "lastCompletedStep", lastCompletedStep,
                "totalDurationMs", System.currentTimeMillis() - pipelineStart,
                "tables", tableResults,
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch Rule 1 list for TIERS:
     * rows where required columns are NULL.
     * GET /api/etl/quality/tiers/null-check/list
     */
    @GetMapping("/quality/tiers/null-check/list")
    public ResponseEntity<?> fetchTiersNullCheckList() {
        try {
            List<Map<String, Object>> rows = tiersDataQualityService.fetchNullCheckList();
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "rule", "nullCheck",
                    "count", rows.size(),
                    "rows", rows
            ));
        } catch (Exception e) {
            log.error("Error fetching TIERS null-check list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch Rule 2 list for TIERS:
     * duplicate rows by idtiers (keeping first occurrence).
     * GET /api/etl/quality/tiers/duplicate/list
     */
    @GetMapping("/quality/tiers/duplicate/list")
    public ResponseEntity<?> fetchTiersDuplicateList() {
        try {
            List<Map<String, Object>> rows = tiersDataQualityService.fetchDuplicateList();
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "rule", "duplicate",
                    "count", rows.size(),
                    "rows", rows
            ));
        } catch (Exception e) {
            log.error("Error fetching TIERS duplicate list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch Rule 3 list for TIERS:
     * rows with invalid data types.
     * GET /api/etl/quality/tiers/type-check/list
     */
    @GetMapping("/quality/tiers/type-check/list")
    public ResponseEntity<?> fetchTiersTypeCheckList() {
        try {
            List<Map<String, Object>> rows = tiersDataQualityService.fetchTypeCheckList();
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "rule", "typeCheck",
                    "count", rows.size(),
                    "rows", rows
            ));
        } catch (Exception e) {
            log.error("Error fetching TIERS type-check list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch Rule 1 list for CONTRAT:
     * rows where required columns are NULL.
     * GET /api/etl/quality/contrat/null-check/list
     */
    @GetMapping("/quality/contrat/null-check/list")
    public ResponseEntity<?> fetchContratNullCheckList() {
        try {
            List<Map<String, Object>> rows = contratDataQualityService.fetchNullCheckList();
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "rule", "nullCheck",
                    "count", rows.size(),
                    "rows", rows
            ));
        } catch (Exception e) {
            log.error("Error fetching CONTRAT null-check list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch Rule 2 list for CONTRAT:
     * duplicate rows by idcontrat (keeping first occurrence).
     * GET /api/etl/quality/contrat/duplicate/list
     */
    @GetMapping("/quality/contrat/duplicate/list")
    public ResponseEntity<?> fetchContratDuplicateList() {
        try {
            List<Map<String, Object>> rows = contratDataQualityService.fetchDuplicateList();
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "rule", "duplicate",
                    "count", rows.size(),
                    "rows", rows
            ));
        } catch (Exception e) {
            log.error("Error fetching CONTRAT duplicate list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch Rule 3 list for CONTRAT:
     * rows with invalid data types.
     * GET /api/etl/quality/contrat/type-check/list
     */
    @GetMapping("/quality/contrat/type-check/list")
    public ResponseEntity<?> fetchContratTypeCheckList() {
        try {
            List<Map<String, Object>> rows = contratDataQualityService.fetchTypeCheckList();
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "rule", "typeCheck",
                    "count", rows.size(),
                    "rows", rows
            ));
        } catch (Exception e) {
            log.error("Error fetching CONTRAT type-check list: {}", e.getMessage(), e);
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
     * Calculate and compare soldeconvertie sums between staging and fact_balance.
     * GET /api/etl/quality/compta/balance-sum
     */
    @GetMapping("/quality/compta/balance-sum")
    public ResponseEntity<?> fetchComptaBalanceSums() {
        try {
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "sums", comptaDataQualityService.calculateBalanceSumsFromStagingAndFact()
            ));
        } catch (Exception e) {
            log.error("Error calculating COMPTA balance sums: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Execute datamart load pipeline in strict order:
     * 1. TIERS datamart load
     * 2. CONTRAT datamart load
     * 3. COMPTA datamart load
     * POST /api/etl/datamart
     */
    @PostMapping("/datamart")
    public ResponseEntity<?> loadDatamartPipeline() {
        log.info("Starting merged datamart load pipeline");

        long pipelineStart = System.currentTimeMillis();
        Map<String, Object> tableResults = new LinkedHashMap<>();
        String lastCompletedStep = "none";

        try {
            long tiersStart = System.currentTimeMillis();
            TiersDatamartService.LoadResult tiersLoad = tiersDatamartService.loadTiersDatamart();
            lastCompletedStep = "TIERS.datamart";

            Map<String, Object> tiersResult = new LinkedHashMap<>();
            tiersResult.put("subDimResidenceRows", tiersLoad.getSubDimResidenceRows());
            tiersResult.put("subDimAgentecoRows", tiersLoad.getSubDimAgentecoRows());
            tiersResult.put("subDimDouteuxRows", tiersLoad.getSubDimDouteuxRows());
            tiersResult.put("subDimGrpaffaireRows", tiersLoad.getSubDimGrpaffaireRows());
            tiersResult.put("subDimSectionactiviteRows", tiersLoad.getSubDimSectionactiviteRows());
            tiersResult.put("dimClientRows", tiersLoad.getDimClientRows());
            tiersResult.put("durationMs", System.currentTimeMillis() - tiersStart);
            tableResults.put("TIERS", tiersResult);

            long contratStart = System.currentTimeMillis();
            ContratDatamartService.LoadResult contratLoad = contratDatamartService.loadContratDatamart();
            lastCompletedStep = "CONTRAT.datamart";

            Map<String, Object> contratResult = new LinkedHashMap<>();
            contratResult.put("subDimAgenceRows", contratLoad.getSubDimAgenceRows());
            contratResult.put("subDimDeviseRows", contratLoad.getSubDimDeviseRows());
            contratResult.put("subDimObjetfinanceRows", contratLoad.getSubDimObjetfinanceRows());
            contratResult.put("subDimTypcontratRows", contratLoad.getSubDimTypcontratRows());
            contratResult.put("subDimDateRows", contratLoad.getSubDimDateRows());
            contratResult.put("dimContratRows", contratLoad.getDimContratRows());
            contratResult.put("durationMs", System.currentTimeMillis() - contratStart);
            tableResults.put("CONTRAT", contratResult);

            long comptaStart = System.currentTimeMillis();
            ComptaDatamartService.LoadResult comptaLoad = comptaDatamartService.loadComptaDatamart();
            lastCompletedStep = "COMPTA.datamart";

            Map<String, Object> comptaResult = new LinkedHashMap<>();
            comptaResult.put("subDimAgenceRows", comptaLoad.getSubDimAgenceRows());
            comptaResult.put("subDimDeviseRows", comptaLoad.getSubDimDeviseRows());
            comptaResult.put("subDimChapitreRows", comptaLoad.getSubDimChapitreRows());
            comptaResult.put("subDimCompteRows", comptaLoad.getSubDimCompteRows());
            comptaResult.put("subDimDateRows", comptaLoad.getSubDimDateRows());
            comptaResult.put("factBalanceRows", comptaLoad.getFactBalanceRows());
            comptaResult.put("durationMs", System.currentTimeMillis() - comptaStart);
            tableResults.put("COMPTA", comptaResult);

            etlService.resetStagingSchema();

            Map<String, Object> actualCounts = new LinkedHashMap<>();
            actualCounts.put("dim_client", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM datamart.dim_client", Long.class));
            actualCounts.put("dim_contrat", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM datamart.dim_contrat", Long.class));
            actualCounts.put("fact_balance", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM datamart.fact_balance", Long.class));
            log.info("Datamart actual DB counts after pipeline: {}", actualCounts);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "COMPLETED");
            response.put("sequence", List.of("TIERS.datamart", "CONTRAT.datamart", "COMPTA.datamart"));
            response.put("totalDurationMs", System.currentTimeMillis() - pipelineStart);
            response.put("tables", tableResults);
            response.put("actualDbCounts", actualCounts);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Throwable root = rootCause(e);
            log.error("Error during merged datamart pipeline after step {}: {}", lastCompletedStep, root.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "lastCompletedStep", lastCompletedStep,
                    "totalDurationMs", System.currentTimeMillis() - pipelineStart,
                    "tables", tableResults,
                    "exception", root.getClass().getName(),
                    "message", root.getMessage() != null ? root.getMessage() : "Unknown error"
            ));
        }
    }

    private Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
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
