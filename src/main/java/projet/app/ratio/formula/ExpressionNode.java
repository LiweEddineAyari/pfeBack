package projet.app.ratio.formula;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ParamNode.class, name = "PARAM"),
        @JsonSubTypes.Type(value = ConstantNode.class, name = "CONSTANT"),
        @JsonSubTypes.Type(value = BinaryNode.class, name = "ADD"),
        @JsonSubTypes.Type(value = BinaryNode.class, name = "SUBTRACT"),
        @JsonSubTypes.Type(value = BinaryNode.class, name = "MULTIPLY"),
    @JsonSubTypes.Type(value = BinaryNode.class, name = "DIVIDE"),
    @JsonSubTypes.Type(value = AggregateNode.class, name = "AGGREGATE"),
    @JsonSubTypes.Type(value = FilterNode.class, name = "FILTER")
})
public abstract class ExpressionNode {

    private String type;
}
