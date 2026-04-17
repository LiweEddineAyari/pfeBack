package projet.app.ratio.formula;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BinaryNode extends ExpressionNode {

    private ExpressionNode left;
    private ExpressionNode right;

    public BinaryNode(String type, ExpressionNode left, ExpressionNode right) {
        super(type);
        this.left = left;
        this.right = right;
    }
}
