package projet.app.service.ratio;

import org.springframework.stereotype.Service;
import projet.app.exception.FormulaValidationException;
import projet.app.ratio.formula.BinaryNode;
import projet.app.ratio.formula.ConstantNode;
import projet.app.ratio.formula.ExpressionNode;
import projet.app.ratio.formula.ParamNode;
import projet.app.repository.mapping.ParameterConfigRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;

@Service
public class FormulaValidatorService {

    private static final Set<String> BINARY_TYPES = Set.of("ADD", "SUBTRACT", "MULTIPLY", "DIVIDE");
    private static final double EPSILON = 1e-12d;

    private final ParameterConfigRepository parameterConfigRepository;

    public FormulaValidatorService(ParameterConfigRepository parameterConfigRepository) {
        this.parameterConfigRepository = parameterConfigRepository;
    }

    public void validate(ExpressionNode root) {
        List<String> errors = new ArrayList<>();
        validateNode(root, "formula", errors);

        if (!errors.isEmpty()) {
            throw new FormulaValidationException(errors);
        }
    }

    public Set<String> collectReferencedParameterCodes(ExpressionNode root) {
        Set<String> references = new LinkedHashSet<>();
        collectParams(root, references);
        return references;
    }

    private void validateNode(ExpressionNode node, String path, List<String> errors) {
        if (node == null) {
            errors.add(path + ": node must not be null");
            return;
        }

        if (node.getType() == null || node.getType().isBlank()) {
            errors.add(path + ": type is required");
            return;
        }

        String type = normalizeType(node.getType());

        if ("PARAM".equals(type)) {
            validateParamNode(node, path, errors);
            return;
        }

        if ("CONSTANT".equals(type)) {
            validateConstantNode(node, path, errors);
            return;
        }

        if (BINARY_TYPES.contains(type)) {
            validateBinaryNode(node, type, path, errors);
            return;
        }

        errors.add(path + ": unsupported node type " + node.getType());
    }

    private void validateParamNode(ExpressionNode node, String path, List<String> errors) {
        if (!(node instanceof ParamNode paramNode)) {
            errors.add(path + ": PARAM node has invalid structure");
            return;
        }

        if (paramNode.getCode() == null || paramNode.getCode().isBlank()) {
            errors.add(path + ": PARAM.code is required");
            return;
        }

        String code = paramNode.getCode().trim();
        if (!parameterConfigRepository.existsByCode(code)) {
            errors.add(path + ": referenced PARAM code does not exist: " + code);
        }
    }

    private void validateConstantNode(ExpressionNode node, String path, List<String> errors) {
        if (!(node instanceof ConstantNode constantNode)) {
            errors.add(path + ": CONSTANT node has invalid structure");
            return;
        }

        if (constantNode.getValue() == null) {
            errors.add(path + ": CONSTANT.value is required");
            return;
        }

        if (constantNode.getValue().isNaN() || constantNode.getValue().isInfinite()) {
            errors.add(path + ": CONSTANT.value must be a finite number");
        }
    }

    private void validateBinaryNode(ExpressionNode node, String type, String path, List<String> errors) {
        if (!(node instanceof BinaryNode binaryNode)) {
            errors.add(path + ": " + type + " node has invalid structure");
            return;
        }

        if (binaryNode.getLeft() == null) {
            errors.add(path + ": left operand is required");
        } else {
            validateNode(binaryNode.getLeft(), path + ".left", errors);
        }

        if (binaryNode.getRight() == null) {
            errors.add(path + ": right operand is required");
        } else {
            validateNode(binaryNode.getRight(), path + ".right", errors);
        }

        if ("DIVIDE".equals(type) && binaryNode.getRight() != null && isDefinitiveZero(binaryNode.getRight())) {
            errors.add(path + ": division by zero risk detected on right operand");
        }
    }

    private void collectParams(ExpressionNode node, Set<String> references) {
        if (node == null) {
            return;
        }

        String type = normalizeType(node.getType());
        if ("PARAM".equals(type) && node instanceof ParamNode paramNode && paramNode.getCode() != null) {
            references.add(paramNode.getCode().trim());
            return;
        }

        if (BINARY_TYPES.contains(type) && node instanceof BinaryNode binaryNode) {
            collectParams(binaryNode.getLeft(), references);
            collectParams(binaryNode.getRight(), references);
        }
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isDefinitiveZero(ExpressionNode node) {
        OptionalDouble constantValue = evaluateConstantExpression(node);
        return constantValue.isPresent() && Math.abs(constantValue.getAsDouble()) < EPSILON;
    }

    private OptionalDouble evaluateConstantExpression(ExpressionNode node) {
        if (node == null || node.getType() == null) {
            return OptionalDouble.empty();
        }

        String type = normalizeType(node.getType());

        if ("CONSTANT".equals(type) && node instanceof ConstantNode constantNode && constantNode.getValue() != null) {
            return OptionalDouble.of(constantNode.getValue());
        }

        if (!BINARY_TYPES.contains(type) || !(node instanceof BinaryNode binaryNode)) {
            return OptionalDouble.empty();
        }

        OptionalDouble left = evaluateConstantExpression(binaryNode.getLeft());
        OptionalDouble right = evaluateConstantExpression(binaryNode.getRight());
        if (left.isEmpty() || right.isEmpty()) {
            return OptionalDouble.empty();
        }

        return switch (type) {
            case "ADD" -> OptionalDouble.of(left.getAsDouble() + right.getAsDouble());
            case "SUBTRACT" -> OptionalDouble.of(left.getAsDouble() - right.getAsDouble());
            case "MULTIPLY" -> OptionalDouble.of(left.getAsDouble() * right.getAsDouble());
            case "DIVIDE" -> {
                if (Math.abs(right.getAsDouble()) < EPSILON) {
                    yield OptionalDouble.empty();
                }
                yield OptionalDouble.of(left.getAsDouble() / right.getAsDouble());
            }
            default -> OptionalDouble.empty();
        };
    }
}
