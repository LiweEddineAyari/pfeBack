package projet.app.engine.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import projet.app.engine.ast.FormulaDefinition;
import projet.app.engine.parser.FormulaParser;
import projet.app.engine.registry.FieldRegistry;
import projet.app.engine.registry.JoinCatalog;
import projet.app.engine.validation.FormulaValidationService;
import projet.app.exception.FormulaValidationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderByLimitTopTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FieldRegistry fieldRegistry = new FieldRegistry();
    private final FormulaValidationService validationService = new FormulaValidationService(new FormulaParser(), fieldRegistry);
    private final FormulaSqlCompiler compiler;

    OrderByLimitTopTest() {
        FilterBuilder filterBuilder = new FilterBuilder(fieldRegistry);
        AggregationBuilder aggregationBuilder = new AggregationBuilder(fieldRegistry, filterBuilder);
        ExpressionCompiler expressionCompiler = new ExpressionCompiler(fieldRegistry, aggregationBuilder);
        JoinResolver joinResolver = new JoinResolver(fieldRegistry, new JoinCatalog());
        SqlQueryBuilder sqlQueryBuilder = new SqlQueryBuilder();
        this.compiler = new FormulaSqlCompiler(expressionCompiler, joinResolver, filterBuilder, sqlQueryBuilder, fieldRegistry);
    }

    @Test
    void compilesOrderByOnlyUsingValueAlias() throws Exception {
        CompiledSql compiledSql = compile("""
                {
                  "expression": {
                    "type": "AGGREGATION",
                    "function": "SUM",
                    "field": "soldeconvertie"
                  },
                  "orderBy": [
                    {"field": "value", "direction": "DESC"}
                  ]
                }
                """);

        assertTrue(compiledSql.sql().contains("ORDER BY value DESC"));
        assertNull(compiledSql.limit());
        assertNull(compiledSql.top());
    }

    @Test
    void compilesGroupByOrderByAndLimit() throws Exception {
        CompiledSql compiledSql = compile("""
                {
                  "expression": {
                    "type": "AGGREGATION",
                    "function": "SUM",
                    "field": "soldeconvertie"
                  },
                  "groupBy": ["idAgence"],
                  "orderBy": [
                    {"field": "idAgence", "direction": "ASC"},
                    {"field": "value", "direction": "DESC"}
                  ],
                  "limit": 10
                }
                """);

        assertTrue(compiledSql.sql().contains("GROUP BY f.id_agence ORDER BY f.id_agence ASC, value DESC LIMIT 10"));
        assertEquals(10, compiledSql.limit());
        assertNull(compiledSql.top());
    }

    @Test
    void compilesTopByUsingLimitClause() throws Exception {
        CompiledSql compiledSql = compile("""
                {
                  "expression": {
                    "type": "AGGREGATION",
                    "function": "SUM",
                    "field": "soldeconvertie"
                  },
                  "groupBy": ["idDevise"],
                  "orderBy": [
                    {"field": "value", "direction": "DESC"}
                  ],
                  "top": 5
                }
                """);

        assertTrue(compiledSql.sql().contains("ORDER BY value DESC LIMIT 5"));
        assertNull(compiledSql.limit());
        assertEquals(5, compiledSql.top());
    }

    @Test
    void rejectsLimitAndTopTogether() {
        FormulaValidationException exception = assertThrows(FormulaValidationException.class, () ->
                parseAndValidate("""
                        {
                          "expression": {
                            "type": "AGGREGATION",
                            "function": "SUM",
                            "field": "soldeconvertie"
                          },
                          "orderBy": [
                            {"field": "value", "direction": "DESC"}
                          ],
                          "limit": 5,
                          "top": 2
                        }
                        """)
        );

        assertTrue(exception.getErrors().contains("limit and top cannot both be defined"));
    }

    @Test
    void rejectsLimitWithoutOrderBy() {
        FormulaValidationException exception = assertThrows(FormulaValidationException.class, () ->
                parseAndValidate("""
                        {
                          "expression": {
                            "type": "AGGREGATION",
                            "function": "SUM",
                            "field": "soldeconvertie"
                          },
                          "limit": 5
                        }
                        """)
        );

        assertTrue(exception.getErrors().contains("orderBy is required when limit or top is provided"));
    }

    @Test
    void rejectsUnknownOrderByField() {
        FormulaValidationException exception = assertThrows(FormulaValidationException.class, () ->
                parseAndValidate("""
                        {
                          "expression": {
                            "type": "AGGREGATION",
                            "function": "SUM",
                            "field": "soldeconvertie"
                          },
                          "orderBy": [
                            {"field": "unknownField", "direction": "ASC"}
                          ]
                        }
                        """)
        );

        assertTrue(exception.getErrors().contains("orderBy[0]: unknown field unknownField"));
    }

    @Test
    void rejectsNonGroupedOrderByFieldWhenGroupByPresent() {
        FormulaValidationException exception = assertThrows(FormulaValidationException.class, () ->
                parseAndValidate("""
                        {
                          "expression": {
                            "type": "AGGREGATION",
                            "function": "SUM",
                            "field": "soldeconvertie"
                          },
                          "groupBy": ["idAgence"],
                          "orderBy": [
                            {"field": "idDevise", "direction": "ASC"}
                          ],
                          "limit": 10
                        }
                        """)
        );

        assertTrue(exception.getErrors().contains(
                "orderBy[0]: field idDevise must also be present in groupBy when grouping is used"
        ));
    }

    private CompiledSql compile(String formulaJson) throws Exception {
        FormulaDefinition definition = parseAndValidate(formulaJson);
        return compiler.compile(definition);
    }

    private FormulaDefinition parseAndValidate(String formulaJson) throws Exception {
        return validationService.validateAndParse(objectMapper.readTree(formulaJson));
    }
}
