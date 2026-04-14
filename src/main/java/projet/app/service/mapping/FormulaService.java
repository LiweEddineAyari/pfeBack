package projet.app.service.mapping;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.dto.FormulaExecutionResponseDTO;
import projet.app.dto.FormulaRequestDTO;
import projet.app.dto.FormulaSqlResponseDTO;
import projet.app.dto.ParameterConfigResponseDTO;

import java.util.List;

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
    public FormulaSqlResponseDTO compileByCode(String code) {
        return formulaEngineService.compileByCode(code);
    }

    @Transactional(readOnly = true)
    public FormulaExecutionResponseDTO executeByCode(String code) {
        return formulaEngineService.executeByCode(code);
    }

    @Transactional(readOnly = true)
    public List<String> getSupportedFields() {
        return formulaEngineService.getSupportedFields();
    }
}
