package projet.app.ratio.formula;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ParamNode extends ExpressionNode {

    private String code;

    public ParamNode(String code) {
        super("PARAM");
        this.code = code;
    }
}
