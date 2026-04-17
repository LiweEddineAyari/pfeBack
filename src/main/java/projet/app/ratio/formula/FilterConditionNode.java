package projet.app.ratio.formula;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FilterConditionNode {

    private String operator;
    private Double value;
    private ExpressionNode expression;
}
