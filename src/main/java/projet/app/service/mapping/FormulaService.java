package projet.app.service.mapping;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.dto.BulkDeleteResponseDTO;
import projet.app.dto.FormulaExecutionResponseDTO;
import projet.app.dto.FormulaRequestDTO;
import projet.app.dto.FormulaSqlResponseDTO;
import projet.app.dto.ParameterConfigResponseDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class FormulaService {

    private final FormulaEngineService formulaEngineService;

    public FormulaService(FormulaEngineService formulaEngineService) {
        this.formulaEngineService = formulaEngineService;
    }

    @Transactional
    public ParameterConfigResponseDTO create(FormulaRequestDTO request) {
        return formulaEngineService.create(request);
    }

    @Transactional
    public ParameterConfigResponseDTO update(String code, FormulaRequestDTO request) {
        return formulaEngineService.update(code, request);
    }

    @Transactional(readOnly = true)
    public ParameterConfigResponseDTO getByCode(String code) {
        return formulaEngineService.getByCode(code);
    }

    @Transactional(readOnly = true)
    public ParameterConfigResponseDTO getById(Long id) {
        return formulaEngineService.getById(id);
    }

    @Transactional(readOnly = true)
    public List<ParameterConfigResponseDTO> list() {
        return formulaEngineService.list();
    }

    @Transactional(readOnly = true)
    public FormulaSqlResponseDTO compileByCode(String code) {
        return formulaEngineService.compileByCode(code);
    }

    @Transactional(readOnly = true)
    public FormulaSqlResponseDTO compileByCode(String code, LocalDate referenceDate) {
        return formulaEngineService.compileByCode(code, referenceDate);
    }

    @Transactional(readOnly = true)
    public FormulaExecutionResponseDTO executeByCode(String code) {
        return formulaEngineService.executeByCode(code);
    }

    @Transactional(readOnly = true)
    public FormulaExecutionResponseDTO executeByCode(String code, LocalDate referenceDate) {
        return formulaEngineService.executeByCode(code, referenceDate);
    }

    @Transactional(readOnly = true)
    public List<String> getSupportedFields() {
        return formulaEngineService.getSupportedFields();
    }

    @Transactional(readOnly = true)
    public Map<String, List<String>> getSupportedFieldsGroupedByTable() {
        return formulaEngineService.getSupportedFieldsGroupedByTable();
    }

    @Transactional
    public void deleteByCode(String code) {
        formulaEngineService.deleteByCode(code);
    }

    @Transactional
    public BulkDeleteResponseDTO deleteManyByCodes(List<String> codes) {
        return formulaEngineService.deleteManyByCodes(codes);
    }
}
