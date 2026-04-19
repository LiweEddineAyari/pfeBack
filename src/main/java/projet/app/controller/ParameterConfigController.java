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
import projet.app.dto.FormulaExecutionResponseDTO;
import projet.app.dto.FormulaRequestDTO;
import projet.app.dto.FormulaSqlResponseDTO;
import projet.app.dto.ParameterConfigResponseDTO;
import projet.app.dto.SupportedFieldsResponseDTO;
import projet.app.service.mapping.FormulaService;

import java.time.LocalDate;
import java.util.List;

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

    @GetMapping
    public ResponseEntity<List<ParameterConfigResponseDTO>> list() {
        return ResponseEntity.ok(formulaService.list());
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

    @GetMapping("/id/{id}")
    public ResponseEntity<ParameterConfigResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(formulaService.getById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ParameterConfigResponseDTO> getByCodeExplicit(@PathVariable String code) {
        return ResponseEntity.ok(formulaService.getByCode(code));
    }

    @DeleteMapping("/code/{code}")
    public ResponseEntity<Void> deleteByCode(@PathVariable String code) {
        formulaService.deleteByCode(code);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<BulkDeleteResponseDTO> deleteMany(@RequestBody List<String> codes) {
        return ResponseEntity.ok(formulaService.deleteManyByCodes(codes));
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
    public ResponseEntity<SupportedFieldsResponseDTO> supportedFields() {
        return ResponseEntity.ok(SupportedFieldsResponseDTO.builder()
                .fields(formulaService.getSupportedFields())
                .fieldsByTable(formulaService.getSupportedFieldsGroupedByTable())
                .build());
    }
}
