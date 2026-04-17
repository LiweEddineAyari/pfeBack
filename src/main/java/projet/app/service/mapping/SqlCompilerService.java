package projet.app.service.mapping;

import org.springframework.stereotype.Service;
import projet.app.engine.ast.FormulaDefinition;
import projet.app.engine.compiler.CompiledSql;
import projet.app.engine.compiler.FormulaSqlCompiler;

import java.time.LocalDate;

@Service
public class SqlCompilerService {

    private final FormulaSqlCompiler formulaSqlCompiler;

    public SqlCompilerService(FormulaSqlCompiler formulaSqlCompiler) {
        this.formulaSqlCompiler = formulaSqlCompiler;
    }

    public CompiledSql compile(FormulaDefinition definition) {
        return formulaSqlCompiler.compile(definition);
    }

    public CompiledSql compile(FormulaDefinition definition, LocalDate referenceDate) {
        return formulaSqlCompiler.compile(definition, referenceDate);
    }
}
