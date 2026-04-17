package projet.app.ratio.formula;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AggregateNode extends ExpressionNode {

    private String function;
    private ExpressionNode input;

    public AggregateNode(String function, ExpressionNode input) {
        super("AGGREGATE");
        this.function = function;
        this.input = input;
    }
}
