package projet.app.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import projet.app.dto.stresstest.StressTestDiagnosticsResponseDTO;
import projet.app.dto.stresstest.StressTestRequestDTO;
import projet.app.dto.stresstest.StressTestResponseDTO;
import projet.app.service.stresstest.StressTestService;

import java.time.LocalDate;

@RestController
@RequestMapping("/stress-test")
public class StressTestController {

    private final StressTestService stressTestService;

    public StressTestController(StressTestService stressTestService) {
        this.stressTestService = stressTestService;
    }

    @PostMapping("/simulate")
    public ResponseEntity<StressTestResponseDTO> simulate(@Valid @RequestBody StressTestRequestDTO request) {
        return ResponseEntity.ok(stressTestService.simulate(request));
    }

    /**
     * Diagnostics endpoint: returns the most recent available reference dates in {@code fact_balance}
     * together with, optionally, the row count for a candidate {@code referenceDate}. Use this to
     * pick a valid {@code referenceDate} before calling {@code /stress-test/simulate} when you are
     * unsure whether data has been extracted for a given day.
     */
    @GetMapping("/diagnostics")
    public ResponseEntity<StressTestDiagnosticsResponseDTO> diagnostics(
            @RequestParam(name = "referenceDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate
    ) {
        return ResponseEntity.ok(stressTestService.diagnostics(referenceDate));
    }
}
