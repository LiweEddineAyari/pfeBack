package projet.app.engine.ast;

import projet.app.engine.enums.FormulaNodeType;
import projet.app.engine.visitor.FormulaAstVisitor;

public interface FormulaNode {

    FormulaNodeType type();

    <T> T accept(FormulaAstVisitor<T> visitor);
}
