package projet.app.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import projet.app.dto.FormulaExecutionResponseDTO;
import projet.app.dto.FormulaRequestDTO;
import projet.app.dto.FormulaSqlResponseDTO;
import projet.app.dto.ParameterConfigResponseDTO;
import projet.app.service.mapping.FormulaService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/parameters")
public class ParameterConfigController {

    private final FormulaService formulaService;

    public ParameterConfigController(FormulaService formulaService) {
        this.formulaService = formulaService;
    }

    @PostMapping
    public ResponseEntity<ParameterConfigResponseDTO> create(@Valid @RequestBody FormulaRequestDTO request) {
        ParameterConfigResponseDTO response = formulaService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{code}")
    public ResponseEntity<ParameterConfigResponseDTO> update(
            @PathVariable String code,
            @Valid @RequestBody FormulaRequestDTO request
    ) {
        return ResponseEntity.ok(formulaService.update(code, request));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ParameterConfigResponseDTO> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(formulaService.getByCode(code));
    }

    @GetMapping("/{code}/sql")
    public ResponseEntity<FormulaSqlResponseDTO> compile(@PathVariable String code) {
        return ResponseEntity.ok(formulaService.compileByCode(code));
    }

    @PostMapping("/{code}/execute")
    public ResponseEntity<FormulaExecutionResponseDTO> execute(@PathVariable String code) {
        return ResponseEntity.ok(formulaService.executeByCode(code));
    }

    @PostMapping("/{code}/execute/{date}")
    public ResponseEntity<FormulaExecutionResponseDTO> executeAtDate(
            @PathVariable String code,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(formulaService.executeByCode(code, date));
    }

    @GetMapping("/supported-fields")
    public ResponseEntity<Map<String, List<String>>> supportedFields() {
        return ResponseEntity.ok(Map.of("fields", formulaService.getSupportedFields()));
    }
}
