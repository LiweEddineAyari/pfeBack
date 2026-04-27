package projet.app.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import projet.app.dto.DashboardCreateRequestDTO;
import projet.app.dto.DashboardGroupedByRatioResponseDTO;
import projet.app.dto.DashboardRowResponseDTO;
import projet.app.dto.DashboardSimulateAllResponseDTO;
import projet.app.service.dashboard.DashboardService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @PostMapping
    public ResponseEntity<DashboardRowResponseDTO> create(@Valid @RequestBody DashboardCreateRequestDTO request) {
        DashboardRowResponseDTO response = dashboardService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<DashboardRowResponseDTO>> list(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(dashboardService.list(date));
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<List<DashboardRowResponseDTO>> listByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(dashboardService.list(date));
    }

    /**
     * Return the distinct reference dates available in datamart.fact_balance.
     * GET /dashboard/dates
     */
    /**
     * Values grouped by ratio code: each ratio maps ISO dates to stored values.
     * Example JSON shape: {@code { "ratios": { "R1": { "2024-01-31": 1.2, "2024-02-29": 3.4 }, "R2": { ... } } }}
     * GET /dashboard/grouped-by-ratio
     */
    @GetMapping("/grouped-by-ratio")
    public ResponseEntity<DashboardGroupedByRatioResponseDTO> listGroupedByRatio() {
        return ResponseEntity.ok(dashboardService.listGroupedByRatio());
    }

    @GetMapping("/dates")
    public ResponseEntity<?> getAvailableDates() {
        try {
            return ResponseEntity.ok(dashboardService.getAvailableDates());
        } catch (Exception e) {
            log.error("Error fetching available dates: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Simulate every active ratio on every date available in fact_balance
     * and persist the results in the dashboard table.
     * Already-existing (ratio, date) pairs are skipped — idempotent.
     * POST /dashboard/simulate-all
     */
    @PostMapping("/simulate-all")
    public ResponseEntity<?> simulateAll() {
        try {
            DashboardSimulateAllResponseDTO result = dashboardService.simulateAll();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error during simulate-all: {}", e.getMessage(), e);
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "exception", root.getClass().getName(),
                    "message", root.getMessage() != null ? root.getMessage() : "Unknown error"
            ));
        }
    }
}