package projet.app.engine.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import projet.app.engine.ast.FormulaDefinition;
import projet.app.engine.parser.FormulaParser;
import projet.app.engine.registry.FieldRegistry;
import projet.app.engine.registry.JoinCatalog;
import projet.app.engine.validation.FormulaValidationService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedFormulaExpressionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FieldRegistry fieldRegistry = new FieldRegistry();
    private final FormulaValidationService validationService = new FormulaValidationService(new FormulaParser(), fieldRegistry);
    private final FormulaSqlCompiler compiler;

    AdvancedFormulaExpressionTest() {
        FilterBuilder filterBuilder = new FilterBuilder(fieldRegistry);
        AggregationBuilder aggregationBuilder = new AggregationBuilder(fieldRegistry, filterBuilder);
        ExpressionCompiler expressionCompiler = new ExpressionCompiler(fieldRegistry, aggregationBuilder);
        JoinResolver joinResolver = new JoinResolver(fieldRegistry, new JoinCatalog());
        SqlQueryBuilder sqlQueryBuilder = new SqlQueryBuilder();
        this.compiler = new FormulaSqlCompiler(expressionCompiler, joinResolver, filterBuilder, sqlQueryBuilder, fieldRegistry);
    }

    @Test
    void compilesStartsWithFilterAsLikePattern() throws Exception {
        CompiledSql compiledSql = compile("""
                {
                  "expression": {
                    "type": "AGGREGATION",
                    "function": "SUM",
                    "field": "soldeconvertie"
                  },
                  "where": {
                    "logic": "AND",
                    "conditions": [
                      {
                        "field": "raisonsoc",
                        "operator": "STARTS_WITH",
                        "value": "ACME"
                      }
                    ]
                  }
                }
                """);

        assertTrue(compiledSql.sql().contains("cl.raisonsoc LIKE ?"));
        assertEquals(List.of("ACME%"), compiledSql.parameters());
    }

    @Test
    void compilesMultiplyOfAggregationByConstant() throws Exception {
        CompiledSql compiledSql = compile("""
                {
                  "expression": {
                    "type": "MULTIPLY",
                    "left": {
                      "type": "AGGREGATION",
                      "function": "SUM",
                      "field": "soldeconvertie"
                    },
                    "right": {
                      "type": "VALUE",
                      "value": 0.15
                    }
                  }
                }
                """);

        assertTrue(compiledSql.sql().contains("(SUM(f.soldeconvertie) * ?)"));
        assertEquals(1, compiledSql.parameters().size());
        assertEquals(new BigDecimal("0.15"), compiledSql.parameters().get(0));
    }

    @Test
    void compilesSubtractOfTwoFilteredAggregations() throws Exception {
        CompiledSql compiledSql = compile("""
                {
                  "expression": {
                    "type": "SUBTRACT",
                    "left": {
                      "type": "AGGREGATION",
                      "function": "SUM",
                      "field": "soldeconvertie",
                      "filters": {
                        "logic": "AND",
                        "conditions": [
                          {
                            "field": "grpaffaire",
                            "operator": "=",
                            "value": "PERSONNEL"
                          }
                        ]
                      }
                    },
                    "right": {
                      "type": "AGGREGATION",
                      "function": "SUM",
                      "field": "soldeconvertie",
                      "filters": {
                        "logic": "AND",
                        "conditions": [
                          {
                            "field": "grpaffaire",
                            "operator": "=",
                            "value": "CORPORATE"
                          }
                        ]
                      }
                    }
                  }
                }
                """);

        assertTrue(compiledSql.sql().contains("(SUM(CASE WHEN"));
        assertTrue(compiledSql.sql().contains("THEN f.soldeconvertie END) - SUM(CASE WHEN"));
        assertEquals(List.of("PERSONNEL", "CORPORATE"), compiledSql.parameters());
    }

    private CompiledSql compile(String formulaJson) throws Exception {
        FormulaDefinition definition = validationService.validateAndParse(objectMapper.readTree(formulaJson));
        return compiler.compile(definition);
    }
}
