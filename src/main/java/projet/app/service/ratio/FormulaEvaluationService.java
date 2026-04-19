package projet.app.service.ratio;

import org.springframework.stereotype.Service;
import projet.app.dto.FormulaExecutionResponseDTO;
import projet.app.ratio.formula.BinaryNode;
import projet.app.ratio.formula.ConstantNode;
import projet.app.ratio.formula.ExpressionNode;
import projet.app.ratio.formula.ParamNode;
import projet.app.service.mapping.FormulaService;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class FormulaEvaluationService {

    private static final double EPSILON = 1e-12d;

    private final FormulaService formulaService;

    public FormulaEvaluationService(FormulaService formulaService) {
        this.formulaService = formulaService;
    }

    public double evaluate(ExpressionNode node) {
        return evaluate(node, null);
    }

    public double evaluate(ExpressionNode node, LocalDate referenceDate) {
        return evaluate(node, new LinkedHashMap<>(), referenceDate);
    }

    public RatioFormulaExecutionResult evaluateAtDate(ExpressionNode node, LocalDate referenceDate) {
        Map<String, Double> parameterCache = new LinkedHashMap<>();
        double value = evaluate(node, parameterCache, referenceDate);
        return new RatioFormulaExecutionResult(value, new LinkedHashMap<>(parameterCache));
    }

    private double evaluate(ExpressionNode node, Map<String, Double> parameterCache, LocalDate referenceDate) {
        if (node == null || node.getType() == null || node.getType().isBlank()) {
            throw new IllegalArgumentException("Invalid formula node: missing type");
        }

        String type = node.getType().trim().toUpperCase(Locale.ROOT);

        if ("PARAM".equals(type)) {
            return evaluateParamNode(node, parameterCache, referenceDate);
        }

        if ("CONSTANT".equals(type)) {
            return evaluateConstantNode(node);
        }

        if (!(node instanceof BinaryNode binaryNode)) {
            throw new IllegalArgumentException("Invalid binary node structure for type: " + type);
        }

        double left = evaluate(binaryNode.getLeft(), parameterCache, referenceDate);
        double right = evaluate(binaryNode.getRight(), parameterCache, referenceDate);

        return switch (type) {
            case "ADD" -> left + right;
            case "SUBTRACT" -> left - right;
            case "MULTIPLY" -> left * right;
            case "DIVIDE" -> {
                if (Math.abs(right) < EPSILON) {
                    throw new IllegalArgumentException("Division by zero is not allowed");
                }
                yield left / right;
            }
            default -> throw new IllegalArgumentException("Unsupported formula node type: " + node.getType());
        };
    }

    private double evaluateParamNode(
            ExpressionNode node,
            Map<String, Double> parameterCache,
            LocalDate referenceDate
    ) {
        if (!(node instanceof ParamNode paramNode) || paramNode.getCode() == null || paramNode.getCode().isBlank()) {
            throw new IllegalArgumentException("PARAM node requires a non-empty code");
        }

        String parameterCode = paramNode.getCode().trim();
        if (parameterCache.containsKey(parameterCode)) {
            return parameterCache.get(parameterCode);
        }

        FormulaExecutionResponseDTO execution = formulaService.executeByCode(parameterCode, referenceDate);
        Object rawValue = execution.getValue();

        double value;
        if (rawValue == null) {
            value = 0d;
        } else if (rawValue instanceof Number number) {
            value = number.doubleValue();
        } else if (rawValue instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (normalized.isEmpty()) {
                value = 0d;
            } else {
            try {
                value = Double.parseDouble(normalized);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Parameter " + parameterCode + " returned a non-numeric string value");
            }
            }
        } else {
            throw new IllegalArgumentException("Parameter " + parameterCode + " returned a non-numeric value");
        }

        parameterCache.put(parameterCode, value);
        return value;
    }

    private double evaluateConstantNode(ExpressionNode node) {
        if (!(node instanceof ConstantNode constantNode) || constantNode.getValue() == null) {
            throw new IllegalArgumentException("CONSTANT node requires a numeric value");
        }
        return constantNode.getValue();
    }
}
