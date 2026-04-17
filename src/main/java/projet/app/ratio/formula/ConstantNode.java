package projet.app.ratio.formula;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConstantNode extends ExpressionNode {

    private Double value;

    public ConstantNode(Double value) {
        super("CONSTANT");
        this.value = value;
    }
}
