package projet.app.ratio.formula;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FilterNode extends ExpressionNode {

    private ExpressionNode input;
    private FilterConditionNode condition;

    public FilterNode(ExpressionNode input, FilterConditionNode condition) {
        super("FILTER");
        this.input = input;
        this.condition = condition;
    }
}
