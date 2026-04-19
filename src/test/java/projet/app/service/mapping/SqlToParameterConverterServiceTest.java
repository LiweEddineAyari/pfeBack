package projet.app.service.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import projet.app.engine.ast.FormulaDefinition;
import projet.app.engine.compiler.AggregationBuilder;
import projet.app.engine.compiler.CompiledSql;
import projet.app.engine.compiler.ExpressionCompiler;
import projet.app.engine.compiler.FilterBuilder;
import projet.app.engine.compiler.FormulaSqlCompiler;
import projet.app.engine.compiler.JoinResolver;
import projet.app.engine.compiler.SqlQueryBuilder;
import projet.app.engine.parser.FormulaParser;
import projet.app.engine.registry.FieldRegistry;
import projet.app.engine.registry.JoinCatalog;
import projet.app.engine.validation.FormulaValidationService;
import projet.app.exception.InvalidSqlException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlToParameterConverterServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FieldRegistry fieldRegistry = new FieldRegistry();
    private final SqlToParameterConverterService converter = new SqlToParameterConverterService(fieldRegistry, objectMapper);
    private final FormulaValidationService validationService = new FormulaValidationService(new FormulaParser(), fieldRegistry);
    private final FormulaSqlCompiler compiler;

    SqlToParameterConverterServiceTest() {
        FilterBuilder filterBuilder = new FilterBuilder(fieldRegistry);
        AggregationBuilder aggregationBuilder = new AggregationBuilder(fieldRegistry, filterBuilder);
        ExpressionCompiler expressionCompiler = new ExpressionCompiler(fieldRegistry, aggregationBuilder);
        JoinResolver joinResolver = new JoinResolver(fieldRegistry, new JoinCatalog());
        SqlQueryBuilder sqlQueryBuilder = new SqlQueryBuilder();
        this.compiler = new FormulaSqlCompiler(expressionCompiler, joinResolver, filterBuilder, sqlQueryBuilder, fieldRegistry);
    }

    @Test
    void convertsSqlToFormulaAndCompilesWithExistingEngine() {
        JsonNode formula = converter.convertToFormula("""
                SELECT SUM(f.soldeconvertie)
                FROM datamart.fact_balance f
                JOIN datamart.dim_client dc ON f.id_client = dc.idtiers
                WHERE dc.grpaffaire = 'PERSONNEL' AND f.id_agence IN (101, 102)
                GROUP BY f.id_agence
                ORDER BY SUM(f.soldeconvertie) DESC
                LIMIT 10
                """);

        assertEquals("AGGREGATION", formula.get("expression").get("type").asText());
        assertEquals("SUM", formula.get("expression").get("function").asText());
        assertEquals("soldeconvertie", formula.get("expression").get("field").asText());

        assertEquals("idAgence", formula.get("groupBy").get(0).asText());
        assertEquals("value", formula.get("orderBy").get(0).get("field").asText());
        assertEquals("DESC", formula.get("orderBy").get(0).get("direction").asText());
        assertEquals(10, formula.get("limit").asInt());

        FormulaDefinition definition = validationService.validateAndParse(formula);
        CompiledSql compiledSql = compiler.compile(definition);

        assertTrue(compiledSql.sql().contains("SUM(f.soldeconvertie) AS value"));
        assertTrue(compiledSql.sql().contains("GROUP BY f.id_agence"));
        assertTrue(compiledSql.sql().contains("ORDER BY value DESC"));
        assertTrue(compiledSql.sql().contains("LIMIT 10"));
        assertEquals(List.of("PERSONNEL", 101L, 102L), compiledSql.parameters());
    }

    @Test
    void rejectsSubqueries() {
        InvalidSqlException ex = assertThrows(InvalidSqlException.class, () ->
                converter.convertToFormula("""
                        SELECT SUM(soldeconvertie)
                        FROM fact_balance f
                        WHERE f.id_client IN (SELECT idtiers FROM dim_client)
                        """)
        );

        assertEquals("Unsupported SQL structure", ex.getMessage());
        assertTrue(ex.getDetails().contains("Subqueries are not allowed"));
    }

    @Test
    void rejectsMultipleAggregationsInSelect() {
        InvalidSqlException ex = assertThrows(InvalidSqlException.class, () ->
                converter.convertToFormula("""
                        SELECT SUM(soldeconvertie), AVG(soldeconvertie)
                        FROM fact_balance f
                        """)
        );

        assertTrue(ex.getDetails().contains("SELECT must contain exactly one aggregation expression"));
    }
}
