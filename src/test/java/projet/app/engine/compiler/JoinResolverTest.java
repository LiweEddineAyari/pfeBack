package projet.app.engine.compiler;

import org.junit.jupiter.api.Test;
import projet.app.engine.ast.AggregationNode;
import projet.app.engine.ast.FilterConditionNode;
import projet.app.engine.ast.FilterGroupNode;
import projet.app.engine.ast.FormulaDefinition;
import projet.app.engine.ast.OrderByNode;
import projet.app.engine.enums.AggregationFunction;
import projet.app.engine.enums.FilterLogic;
import projet.app.engine.enums.FilterOperator;
import projet.app.engine.enums.SortDirection;
import projet.app.engine.registry.JoinCatalog;
import projet.app.engine.registry.JoinResolution;
import projet.app.engine.registry.FieldRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JoinResolverTest {

    private final JoinResolver joinResolver = new JoinResolver(new FieldRegistry(), new JoinCatalog());

    @Test
    void resolvesOnlyDimContratForContratActifFilter() {
        FormulaDefinition definition = new FormulaDefinition(
                new AggregationNode(AggregationFunction.SUM, "soldeconvertie", null, null, false),
                new FilterGroupNode(
                        FilterLogic.AND,
                        List.of(new FilterConditionNode("dc.actif", FilterOperator.EQ, 1)),
                        List.of()
                ),
                List.of()
        );

        JoinResolution resolution = joinResolver.resolve(definition);

        assertEquals(
                List.of("LEFT JOIN datamart.dim_contrat dc ON f.id_contrat = dc.id"),
                resolution.joinClauses()
        );
    }

    @Test
    void resolvesMinimalClientChainForDouteuxFilter() {
        FormulaDefinition definition = new FormulaDefinition(
                new AggregationNode(AggregationFunction.SUM, "soldeconvertie", null, null, false),
                new FilterGroupNode(
                        FilterLogic.AND,
                        List.of(new FilterConditionNode("douteux", FilterOperator.EQ, 1)),
                        List.of()
                ),
                List.of()
        );

        JoinResolution resolution = joinResolver.resolve(definition);

        assertEquals(
                List.of(
                        "LEFT JOIN datamart.dim_client cl ON f.id_client = cl.idtiers",
                        "LEFT JOIN datamart.sub_dim_douteux dout ON cl.id_douteux = dout.id"
                ),
                resolution.joinClauses()
        );
    }

    @Test
    void resolvesOnlyRequiredMixedChains() {
        FormulaDefinition definition = new FormulaDefinition(
                new AggregationNode(AggregationFunction.SUM, "soldeconvertie", null, null, false),
                new FilterGroupNode(
                        FilterLogic.AND,
                        List.of(
                                new FilterConditionNode("douteux", FilterOperator.EQ, 1),
                                new FilterConditionNode("dimContrat.subDimAgence.numagence", FilterOperator.GT, 100)
                        ),
                        List.of()
                ),
                List.of()
        );

        JoinResolution resolution = joinResolver.resolve(definition);

        assertEquals(
                List.of(
                        "LEFT JOIN datamart.dim_client cl ON f.id_client = cl.idtiers",
                        "LEFT JOIN datamart.sub_dim_douteux dout ON cl.id_douteux = dout.id",
                        "LEFT JOIN datamart.dim_contrat dc ON f.id_contrat = dc.id",
                        "LEFT JOIN datamart.sub_dim_agence agc ON dc.id_agence = agc.id"
                ),
                resolution.joinClauses()
        );
    }

    @Test
    void resolvesJoinFromOrderByField() {
        FormulaDefinition definition = new FormulaDefinition(
                new AggregationNode(AggregationFunction.SUM, "soldeconvertie", null, null, false),
                null,
                List.of(),
                List.of(new OrderByNode("douteux", SortDirection.DESC)),
                null,
                null
        );

        JoinResolution resolution = joinResolver.resolve(definition);

        assertEquals(
                List.of(
                        "LEFT JOIN datamart.dim_client cl ON f.id_client = cl.idtiers",
                        "LEFT JOIN datamart.sub_dim_douteux dout ON cl.id_douteux = dout.id"
                ),
                resolution.joinClauses()
        );
    }
}
