package projet.app.service.ratio;

import org.springframework.stereotype.Service;
import projet.app.dto.FormulaSqlResponseDTO;
import projet.app.ratio.formula.BinaryNode;
import projet.app.ratio.formula.ConstantNode;
import projet.app.ratio.formula.ExpressionNode;
import projet.app.ratio.formula.ParamNode;
import projet.app.service.mapping.FormulaService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class FormulaSqlBuilderService {

    private final FormulaService formulaService;

    public FormulaSqlBuilderService(FormulaService formulaService) {
        this.formulaService = formulaService;
    }

    public String build(ExpressionNode node) {
        return build(node, null);
    }

    public String build(ExpressionNode node, LocalDate referenceDate) {
        return build(node, new HashMap<>(), referenceDate);
    }

    private String build(ExpressionNode node, Map<String, String> parameterSqlCache, LocalDate referenceDate) {
        if (node == null || node.getType() == null || node.getType().isBlank()) {
            throw new IllegalArgumentException("Invalid formula node: missing type");
        }

        String type = node.getType().trim().toUpperCase(Locale.ROOT);

        if ("PARAM".equals(type)) {
            return buildParamSql(node, parameterSqlCache, referenceDate);
        }

        if ("CONSTANT".equals(type)) {
            return buildConstantSql(node);
        }

        if (!(node instanceof BinaryNode binaryNode)) {
            throw new IllegalArgumentException("Invalid binary node structure for type: " + type);
        }

        String leftSql = build(binaryNode.getLeft(), parameterSqlCache, referenceDate);
        String rightSql = build(binaryNode.getRight(), parameterSqlCache, referenceDate);

        String operator = switch (type) {
            case "ADD" -> "+";
            case "SUBTRACT" -> "-";
            case "MULTIPLY" -> "*";
            case "DIVIDE" -> "/";
            default -> throw new IllegalArgumentException("Unsupported formula node type: " + node.getType());
        };

        return "(" + leftSql + " " + operator + " " + rightSql + ")";
    }

    private String buildParamSql(
            ExpressionNode node,
            Map<String, String> parameterSqlCache,
            LocalDate referenceDate
    ) {
        if (!(node instanceof ParamNode paramNode) || paramNode.getCode() == null || paramNode.getCode().isBlank()) {
            throw new IllegalArgumentException("PARAM node requires a non-empty code");
        }

        String parameterCode = paramNode.getCode().trim();
        if (parameterSqlCache.containsKey(parameterCode)) {
            return parameterSqlCache.get(parameterCode);
        }

        FormulaSqlResponseDTO parameterSql = formulaService.compileByCode(parameterCode, referenceDate);
        String sql = "(" + parameterSql.getSql() + ")";
        parameterSqlCache.put(parameterCode, sql);
        return sql;
    }

    private String buildConstantSql(ExpressionNode node) {
        if (!(node instanceof ConstantNode constantNode) || constantNode.getValue() == null) {
            throw new IllegalArgumentException("CONSTANT node requires a numeric value");
        }

        return BigDecimal.valueOf(constantNode.getValue())
                .stripTrailingZeros()
                .toPlainString();
    }
}
