package projet.app.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import projet.app.dto.BulkDeleteResponseDTO;
import projet.app.dto.RatioExecutionResponseDTO;
import projet.app.dto.RatioSimulationRequestDTO;
import projet.app.dto.RatioSimulationResponseDTO;
import projet.app.dto.RatiosConfigRequestDTO;
import projet.app.dto.RatiosConfigResponseDTO;
import projet.app.service.ratio.RatiosService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/ratios")
public class RatiosConfigController {

    private final RatiosService ratiosService;

    public RatiosConfigController(RatiosService ratiosService) {
        this.ratiosService = ratiosService;
    }

    @PostMapping
    public ResponseEntity<RatiosConfigResponseDTO> create(@Valid @RequestBody RatiosConfigRequestDTO request) {
        RatiosConfigResponseDTO response = ratiosService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<RatiosConfigResponseDTO>> list() {
        return ResponseEntity.ok(ratiosService.list());
    }

    @PostMapping("/simulate")
    public ResponseEntity<RatioSimulationResponseDTO> simulate(
            @Valid @RequestBody RatioSimulationRequestDTO request
    ) {
        return ResponseEntity.ok(ratiosService.simulate(request));
    }

    @GetMapping("/{code}")
    public ResponseEntity<RatiosConfigResponseDTO> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(ratiosService.getByCode(code));
    }

    @PutMapping("/{code}")
    public ResponseEntity<RatiosConfigResponseDTO> update(
            @PathVariable String code,
            @Valid @RequestBody RatiosConfigRequestDTO request
    ) {
        return ResponseEntity.ok(ratiosService.update(code, request));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        ratiosService.deleteByCode(code);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<BulkDeleteResponseDTO> deleteMany(@RequestBody List<String> codes) {
        return ResponseEntity.ok(ratiosService.deleteManyByCodes(codes));
    }

    @PostMapping("/{code}/execute/{date}")
    public ResponseEntity<RatioExecutionResponseDTO> executeAtDate(
            @PathVariable String code,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(ratiosService.executeByCodeAtDate(code, date));
    }
}
