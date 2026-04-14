package projet.app.service.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import projet.app.engine.ast.FormulaDefinition;
import projet.app.engine.validation.FormulaValidationService;

@Service
public class ValidationService {

    private final FormulaValidationService formulaValidationService;

    public ValidationService(FormulaValidationService formulaValidationService) {
        this.formulaValidationService = formulaValidationService;
    }

    public FormulaDefinition validateAndParse(JsonNode formulaJson) {
        return formulaValidationService.validateAndParse(formulaJson);
    }
}
