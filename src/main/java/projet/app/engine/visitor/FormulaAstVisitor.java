package projet.app.engine.visitor;

import projet.app.engine.ast.AggregationNode;
import projet.app.engine.ast.BinaryOperationNode;
import projet.app.engine.ast.FieldNode;
import projet.app.engine.ast.ValueNode;

public interface FormulaAstVisitor<T> {

    T visitField(FieldNode node);

    T visitValue(ValueNode node);

    T visitAggregation(AggregationNode node);

    T visitBinary(BinaryOperationNode node);
}
