package projet.app.service.ratio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import projet.app.exception.FormulaValidationException;
import projet.app.ratio.formula.ExpressionNode;

import java.util.List;

@Service
public class RatioFormulaMapper {

    private final ObjectMapper objectMapper;

    public RatioFormulaMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ExpressionNode toExpressionNode(JsonNode formulaNode) {
        if (formulaNode == null || formulaNode.isNull() || formulaNode.isMissingNode()) {
            throw new FormulaValidationException(List.of("formula must not be null"));
        }

        try {
            return objectMapper.treeToValue(formulaNode, ExpressionNode.class);
        } catch (JsonProcessingException ex) {
            throw new FormulaValidationException(List.of("Invalid ratio formula JSON: " + ex.getOriginalMessage()));
        }
    }

    public JsonNode toJsonNode(ExpressionNode expressionNode) {
        return objectMapper.valueToTree(expressionNode);
    }
}
