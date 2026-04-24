package projet.app.service.stresstest;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.dto.stresstest.BalanceAdjustmentDTO;
import projet.app.dto.stresstest.ParameterAdjustmentDTO;
import projet.app.dto.stresstest.ParameterImpactDTO;
import projet.app.dto.stresstest.RatioImpactDTO;
import projet.app.dto.stresstest.StressTestDiagnosticsResponseDTO;
import projet.app.dto.stresstest.StressTestMethod;
import projet.app.dto.stresstest.StressTestRequestDTO;
import projet.app.dto.stresstest.StressTestResponseDTO;
import projet.app.engine.ast.FilterGroupNode;
import projet.app.engine.ast.FormulaDefinition;
import projet.app.engine.parser.FormulaParser;
import projet.app.engine.registry.FieldDefinition;
import projet.app.engine.registry.FieldRegistry;
import projet.app.engine.stresstest.DependencyAnalyzer;
import projet.app.engine.stresstest.InMemoryFilterMatcher;
import projet.app.engine.stresstest.InMemoryFormulaEvaluator;
import projet.app.engine.stresstest.InMemoryRow;
import projet.app.engine.stresstest.InMemoryRowLoader;
import projet.app.engine.validation.FormulaValidationService;
import projet.app.entity.dashboard.DashboardEntry;
import projet.app.entity.mapping.ParameterConfig;
import projet.app.entity.mapping.RatiosConfig;
import projet.app.exception.StressTestException;
import projet.app.ratio.formula.BinaryNode;
import projet.app.ratio.formula.ConstantNode;
import projet.app.ratio.formula.ExpressionNode;
import projet.app.ratio.formula.ParamNode;
import projet.app.repository.dashboard.DashboardRepository;
import projet.app.repository.mapping.ParameterConfigRepository;
import projet.app.repository.mapping.RatiosConfigRepository;
import projet.app.service.ratio.FormulaValidatorService;
import projet.app.service.ratio.RatioFormulaMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Orchestrates in-memory stress-test simulations for balance adjustments and parameter overrides.
 *
 * <p>The service never writes to any repository and never mutates entities loaded from the database.
 * All work is performed on a materialised copy of {@code fact_balance} joined with its dimensions.</p>
 *
 * <p>Algorithm:
 * <ol>
 *     <li>Load original rows and the parameter/ratio catalogs for the requested scope.</li>
 *     <li>Determine the set of affected fields/parameters/ratios.</li>
 *     <li>Evaluate all baseline parameter values on the original rows.</li>
 *     <li>Apply overrides (balance mutation or parameter-level override) to obtain simulated values.</li>
 *     <li>Evaluate baseline and simulated ratio values using the respective parameter maps.</li>
 *     <li>Compute impact percentages and return a rich response.</li>
 * </ol></p>
 */
@Service
public class StressTestService {

    private static final Logger log = LoggerFactory.getLogger(StressTestService.class);

    private static final double EPSILON = 1e-6d;
    private static final double ZERO_EPSILON = 1e-12d;

    /**
     * Minimum absolute delta (|simulated - original|) required for a parameter or ratio to be
     * considered <em>changed</em> and therefore included in the response. Values below this
     * threshold are filtered out as floating-point noise even when the dependency graph marked
     * them as impacted.
     */
    private static final double CHANGE_EPSILON = 1e-9d;

    private static final Set<String> ALLOWED_BALANCE_FIELDS = Set.of(
            "soldeorigine",
            "soldeconvertie",
            "cumulmvtdb",
            "cumulmvtcr",
            "soldeinitdebmois",
            "amount"
    );

    private final InMemoryRowLoader rowLoader;
    private final InMemoryFilterMatcher filterMatcher;
    private final InMemoryFormulaEvaluator formulaEvaluator;
    private final FormulaValidationService formulaValidationService;
    private final FormulaParser formulaParser;
    private final FieldRegistry fieldRegistry;
    private final DependencyAnalyzer dependencyAnalyzer;
    private final ParameterConfigRepository parameterConfigRepository;
    private final RatiosConfigRepository ratiosConfigRepository;
    private final DashboardRepository dashboardRepository;
    private final RatioFormulaMapper ratioFormulaMapper;
    private final FormulaValidatorService ratioFormulaValidatorService;

    public StressTestService(
            InMemoryRowLoader rowLoader,
            InMemoryFilterMatcher filterMatcher,
            InMemoryFormulaEvaluator formulaEvaluator,
            FormulaValidationService formulaValidationService,
            FormulaParser formulaParser,
            FieldRegistry fieldRegistry,
            DependencyAnalyzer dependencyAnalyzer,
            ParameterConfigRepository parameterConfigRepository,
            RatiosConfigRepository ratiosConfigRepository,
            DashboardRepository dashboardRepository,
            RatioFormulaMapper ratioFormulaMapper,
            FormulaValidatorService ratioFormulaValidatorService
    ) {
        this.rowLoader = rowLoader;
        this.filterMatcher = filterMatcher;
        this.formulaEvaluator = formulaEvaluator;
        this.formulaValidationService = formulaValidationService;
        this.formulaParser = formulaParser;
        this.fieldRegistry = fieldRegistry;
        this.dependencyAnalyzer = dependencyAnalyzer;
        this.parameterConfigRepository = parameterConfigRepository;
        this.ratiosConfigRepository = ratiosConfigRepository;
        this.dashboardRepository = dashboardRepository;
        this.ratioFormulaMapper = ratioFormulaMapper;
        this.ratioFormulaValidatorService = ratioFormulaValidatorService;
    }

    @Transactional(readOnly = true)
    public StressTestResponseDTO simulate(StressTestRequestDTO request) {
        validateRequest(request);

        LocalDate referenceDate = request.getReferenceDate();

        log.info("==============================================================");
        log.info("[stress-test] START simulate | method={} referenceDate={}",
                request.getMethod(), referenceDate);

        List<ParameterConfig> parametersInScope = selectParameters(request.getParameterCodes());
        List<RatiosConfig> ratiosInScope = selectRatios(request.getRatioCodes());

        log.info("[stress-test] scope | parameters={} ratios={}",
                parametersInScope.size(), ratiosInScope.size());

        List<InMemoryRow> originalRows = rowLoader.load(referenceDate);
        log.info("[stress-test] loaded {} fact rows for referenceDate={}",
                originalRows.size(), referenceDate);

        if (originalRows.isEmpty()) {
            List<LocalDate> suggestions = rowLoader.sampleAvailableDates(5);
            String details = suggestions.isEmpty()
                    ? "No dates available in fact_balance."
                    : "Available dates: " + suggestions;
            log.warn("[stress-test] no rows for referenceDate={} -> {}", referenceDate, details);
            throw new StressTestException(
                    "NO_DATA_FOR_DATE",
                    "No fact_balance rows found for referenceDate=" + referenceDate,
                    List.of(details)
            );
        }

        logSampleRow(originalRows);

        List<InMemoryRow> simulatedRows = deepCopyRows(originalRows);

        Set<String> affectedFields = new LinkedHashSet<>();
        int factRowsImpacted = 0;

        if (request.getMethod() == StressTestMethod.BALANCE) {
            BalanceApplicationResult result = applyBalanceAdjustments(
                    request.getBalanceAdjustments(),
                    simulatedRows
            );
            affectedFields.addAll(result.affectedFields);
            factRowsImpacted = result.impactedRowCount;
            log.info("[stress-test] balance adjustments applied | impactedRows={} affectedFields={} positiveDelta={} negativeDelta={}",
                    factRowsImpacted, affectedFields, result.positiveDelta.toPlainString(), result.negativeDelta.toPlainString());
            validateBalanceConstraint(result);
        }

        Map<String, Set<String>> fieldToParameters = dependencyAnalyzer.buildFieldToParameterMap(parametersInScope);
        Map<String, Set<String>> parameterToRatios = dependencyAnalyzer.buildParameterToRatioMap(ratiosInScope);

        Set<String> affectedParameters = new LinkedHashSet<>();
        if (request.getMethod() == StressTestMethod.BALANCE) {
            affectedParameters.addAll(
                    dependencyAnalyzer.computeAffectedParameters(affectedFields, fieldToParameters)
            );
        } else {
            for (ParameterAdjustmentDTO adjustment : safeList(request.getParameterAdjustments())) {
                affectedParameters.add(adjustment.getCode().trim());
            }
        }

        Set<String> affectedRatios = dependencyAnalyzer.computeAffectedRatios(
                affectedParameters,
                parameterToRatios
        );

        log.info("[stress-test] dependency analysis | affectedFields={} affectedParameters={} affectedRatios={}",
                affectedFields, affectedParameters, affectedRatios);

        Map<String, ParameterEvaluation> baselineParameters = evaluateAllParameters(parametersInScope, originalRows);
        logParameterValues("baseline", baselineParameters);

        Map<String, ParameterEvaluation> simulatedParameters = buildSimulatedParameters(
                request,
                parametersInScope,
                originalRows,
                simulatedRows,
                baselineParameters,
                affectedFields
        );
        logParameterValues("simulated", simulatedParameters);

        List<ParameterImpactDTO> parameterImpacts = buildParameterImpacts(
                parametersInScope,
                baselineParameters,
                simulatedParameters,
                affectedParameters
        );

        List<RatioImpactDTO> ratioImpacts = buildRatioImpacts(
                ratiosInScope,
                baselineParameters,
                simulatedParameters,
                referenceDate,
                affectedRatios
        );

        // Drop entries with no numerical movement so the response only carries meaningful
        // changes, and rebuild {@code affectedParameters}/{@code affectedRatios} from the
        // surviving codes (dependency-only matches that didn't produce a delta are excluded).
        List<ParameterImpactDTO> changedParameters = parameterImpacts.stream()
                .filter(p -> Boolean.TRUE.equals(p.getChanged()))
                .toList();
        List<RatioImpactDTO> changedRatios = ratioImpacts.stream()
                .filter(r -> Boolean.TRUE.equals(r.getChanged()))
                .toList();

        Set<String> changedParameterCodes = new LinkedHashSet<>();
        changedParameters.forEach(p -> changedParameterCodes.add(p.getCode()));

        Set<String> changedRatioCodes = new LinkedHashSet<>();
        changedRatios.forEach(r -> changedRatioCodes.add(r.getCode()));

        log.info("[stress-test] DONE simulate | factRowsLoaded={} factRowsImpacted={} parameters(changed/total)={}/{} ratios(changed/total)={}/{}",
                originalRows.size(), factRowsImpacted,
                changedParameters.size(), parameterImpacts.size(),
                changedRatios.size(), ratioImpacts.size());
        log.info("==============================================================");

        return StressTestResponseDTO.builder()
                .method(request.getMethod())
                .referenceDate(referenceDate)
                .factRowsLoaded(originalRows.size())
                .factRowsImpacted(factRowsImpacted)
                .affectedFields(affectedFields)
                .affectedParameters(changedParameterCodes)
                .affectedRatios(changedRatioCodes)
                .parameters(changedParameters)
                .ratios(changedRatios)
                .build();
    }

    private void logSampleRow(List<InMemoryRow> rows) {
        if (rows.isEmpty() || !log.isInfoEnabled()) {
            return;
        }
        InMemoryRow first = rows.get(0);
        log.info("[stress-test] sample row | numcompte={} soldeconvertie={} soldeorigine={} cumulmvtdb={} cumulmvtcr={} dateValue={} actifFact={} actifContrat={}",
                first.get("subDimCompte.numcompte"),
                first.get("soldeconvertie"),
                first.get("soldeorigine"),
                first.get("cumulmvtdb"),
                first.get("cumulmvtcr"),
                first.get("subDimDate.dateValue"),
                first.get("actif"),
                first.get("dimContrat.actif"));
    }

    private void logParameterValues(String phase, Map<String, ParameterEvaluation> values) {
        if (!log.isInfoEnabled() || values.isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, ParameterEvaluation> entry : values.entrySet()) {
            if (count++ > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue().value);
        }
        log.info("[stress-test] {} parameter values | {}", phase, builder);
    }

    @Transactional(readOnly = true)
    public StressTestDiagnosticsResponseDTO diagnostics(LocalDate referenceDate) {
        List<LocalDate> available = rowLoader.sampleAvailableDates(10);
        long totalRows = rowLoader.countRowsForDate(null);
        long rowsForDate = referenceDate == null ? 0L : rowLoader.countRowsForDate(referenceDate);

        log.info("[stress-test] diagnostics | referenceDate={} rowsForDate={} totalRows={} availableDates={}",
                referenceDate, rowsForDate, totalRows, available);

        return StressTestDiagnosticsResponseDTO.builder()
                .referenceDate(referenceDate)
                .rowCountForReferenceDate(rowsForDate)
                .availableReferenceDates(available)
                .totalFactRows(totalRows)
                .build();
    }

    // ---------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------

    private void validateRequest(StressTestRequestDTO request) {
        if (request.getMethod() == null) {
            throw new StressTestException(
                    "INVALID_REQUEST",
                    "method is required (BALANCE or PARAMETER)"
            );
        }
        if (request.getReferenceDate() == null) {
            throw new StressTestException(
                    "INVALID_REQUEST",
                    "referenceDate is required"
            );
        }

        if (request.getMethod() == StressTestMethod.BALANCE) {
            if (request.getBalanceAdjustments() == null || request.getBalanceAdjustments().isEmpty()) {
                throw new StressTestException(
                        "INVALID_REQUEST",
                        "balanceAdjustments must contain at least one entry when method=BALANCE"
                );
            }
        } else if (request.getMethod() == StressTestMethod.PARAMETER) {
            if (request.getParameterAdjustments() == null || request.getParameterAdjustments().isEmpty()) {
                throw new StressTestException(
                        "INVALID_REQUEST",
                        "parameterAdjustments must contain at least one entry when method=PARAMETER"
                );
            }
        }
    }

    // ---------------------------------------------------------------------
    // Scope selection
    // ---------------------------------------------------------------------

    private List<ParameterConfig> selectParameters(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return parameterConfigRepository.findAll().stream()
                    .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                    .toList();
        }
        List<String> normalized = codes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .toList();
        List<ParameterConfig> resolved = parameterConfigRepository.findAllByCodeIn(normalized);
        Set<String> found = new LinkedHashSet<>();
        resolved.forEach(p -> found.add(p.getCode()));
        List<String> missing = normalized.stream().filter(code -> !found.contains(code)).toList();
        if (!missing.isEmpty()) {
            throw new StressTestException(
                    "UNKNOWN_PARAMETER",
                    "One or more parameter codes do not exist",
                    missing
            );
        }
        return resolved;
    }

    private List<RatiosConfig> selectRatios(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return ratiosConfigRepository.findAll().stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                    .toList();
        }
        List<String> normalized = codes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .toList();
        List<RatiosConfig> resolved = ratiosConfigRepository.findAllByCodeIn(normalized);
        Set<String> found = new LinkedHashSet<>();
        resolved.forEach(r -> found.add(r.getCode()));
        List<String> missing = normalized.stream().filter(code -> !found.contains(code)).toList();
        if (!missing.isEmpty()) {
            throw new StressTestException(
                    "UNKNOWN_RATIO",
                    "One or more ratio codes do not exist",
                    missing
            );
        }
        return resolved;
    }

    // ---------------------------------------------------------------------
    // Balance adjustments
    // ---------------------------------------------------------------------

    private BalanceApplicationResult applyBalanceAdjustments(
            List<BalanceAdjustmentDTO> adjustments,
            List<InMemoryRow> simulatedRows
    ) {
        BalanceApplicationResult result = new BalanceApplicationResult();
        Set<Integer> impactedRowIndexes = new LinkedHashSet<>();

        for (int adjIndex = 0; adjIndex < adjustments.size(); adjIndex++) {
            BalanceAdjustmentDTO adjustment = adjustments.get(adjIndex);
            String path = "balanceAdjustments[" + adjIndex + "]";

            if (adjustment.getOperation() == null) {
                throw new StressTestException("INVALID_OPERATION", path + ": operation is required");
            }
            if (adjustment.getField() == null || adjustment.getField().isBlank()) {
                throw new StressTestException("INVALID_OPERATION", path + ": field is required");
            }
            if (adjustment.getValue() == null) {
                throw new StressTestException("INVALID_OPERATION", path + ": value is required");
            }

            FieldDefinition fieldDefinition;
            try {
                fieldDefinition = fieldRegistry.resolve(adjustment.getField());
            } catch (RuntimeException ex) {
                throw new StressTestException(
                        "UNKNOWN_FIELD",
                        path + ": unknown balance field " + adjustment.getField()
                );
            }

            String canonicalField = fieldDefinition.fieldName().toLowerCase(Locale.ROOT);
            if (!ALLOWED_BALANCE_FIELDS.contains(canonicalField)) {
                throw new StressTestException(
                        "INVALID_OPERATION",
                        path + ": field " + adjustment.getField() + " is not adjustable (allowed: "
                                + ALLOWED_BALANCE_FIELDS + ")"
                );
            }

            FilterGroupNode filter = parseAdjustmentFilter(adjustment.getFilters(), path);
            BigDecimal value = adjustment.getValue();

            int matchedForThisAdjustment = 0;
            Object firstMatchBefore = null;
            Object firstMatchAfter = null;
            Object firstMatchKey = null;

            for (int rowIdx = 0; rowIdx < simulatedRows.size(); rowIdx++) {
                InMemoryRow row = simulatedRows.get(rowIdx);
                if (filter != null && !filter.isEmpty() && !filterMatcher.matches(filter, row)) {
                    continue;
                }

                Object currentRaw = row.get(canonicalField);
                BigDecimal current = toBigDecimal(currentRaw);
                if (current == null) {
                    current = BigDecimal.ZERO;
                }

                BigDecimal next = switch (adjustment.getOperation()) {
                    case ADD -> current.add(value);
                    case SUBTRACT -> current.subtract(value);
                    case SET -> value;
                };

                BigDecimal delta = next.subtract(current);
                row.set(canonicalField, next);
                impactedRowIndexes.add(rowIdx);
                matchedForThisAdjustment++;

                if (matchedForThisAdjustment == 1) {
                    firstMatchBefore = current;
                    firstMatchAfter = next;
                    firstMatchKey = row.get("subDimCompte.numcompte");
                }

                if (delta.signum() > 0) {
                    result.positiveDelta = result.positiveDelta.add(delta);
                } else if (delta.signum() < 0) {
                    result.negativeDelta = result.negativeDelta.add(delta.abs());
                }
            }

            log.info("[stress-test] adjustment {}#{} | op={} field={} value={} matchedRows={} firstMatch(numcompte={}, before={}, after={})",
                    path, adjIndex, adjustment.getOperation(), canonicalField, value,
                    matchedForThisAdjustment, firstMatchKey, firstMatchBefore, firstMatchAfter);

            result.affectedFields.add(canonicalField);
        }

        result.impactedRowCount = impactedRowIndexes.size();
        return result;
    }

    private FilterGroupNode parseAdjustmentFilter(JsonNode filtersJson, String path) {
        if (filtersJson == null || filtersJson.isNull() || filtersJson.isMissingNode()) {
            return null;
        }

        com.fasterxml.jackson.databind.node.ObjectNode wrapper =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        wrapper.set("type", com.fasterxml.jackson.databind.node.TextNode.valueOf("VALUE"));
        wrapper.set("value", com.fasterxml.jackson.databind.node.LongNode.valueOf(1));
        wrapper.set("filter", filtersJson);

        try {
            FormulaDefinition definition = formulaParser.parse(wrapper);
            FilterGroupNode group = definition.whereFilter();
            if (group == null) {
                return null;
            }
            validateFilterFields(group, path);
            return group;
        } catch (StressTestException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new StressTestException(
                    "INVALID_OPERATION",
                    path + ".filters: " + ex.getMessage()
            );
        }
    }

    private void validateFilterFields(FilterGroupNode group, String path) {
        if (group == null || group.isEmpty()) {
            return;
        }
        group.conditions().forEach(condition -> {
            if (!fieldRegistry.exists(condition.field())) {
                throw new StressTestException(
                        "UNKNOWN_FIELD",
                        path + ".filters: unknown filter field " + condition.field()
                );
            }
        });
        group.groups().forEach(nested -> validateFilterFields(nested, path));
    }

    private void validateBalanceConstraint(BalanceApplicationResult result) {
        BigDecimal positive = result.positiveDelta.setScale(6, RoundingMode.HALF_UP);
        BigDecimal negative = result.negativeDelta.setScale(6, RoundingMode.HALF_UP);

        BigDecimal diff = positive.subtract(negative).abs();
        if (diff.compareTo(BigDecimal.valueOf(EPSILON)) > 0) {
            throw new StressTestException(
                    "UNBALANCED_SIMULATION",
                    "Total positive adjustments must equal total negative adjustments",
                    List.of(
                            "positive=" + positive.toPlainString(),
                            "negative=" + negative.toPlainString(),
                            "difference=" + diff.toPlainString()
                    )
            );
        }
    }

    // ---------------------------------------------------------------------
    // Parameter evaluation
    // ---------------------------------------------------------------------

    private Map<String, ParameterEvaluation> evaluateAllParameters(
            List<ParameterConfig> parameters,
            List<InMemoryRow> rows
    ) {
        Map<String, ParameterEvaluation> result = new LinkedHashMap<>();
        for (ParameterConfig parameter : parameters) {
            try {
                FormulaDefinition definition = formulaValidationService.validateAndParse(parameter.getFormulaJson());
                double value = formulaEvaluator.evaluate(definition, rows);
                result.put(parameter.getCode(), new ParameterEvaluation(value, definition));
            } catch (RuntimeException ex) {
                // Parameter with a broken formula: record NaN so it surfaces in the response.
                result.put(parameter.getCode(), new ParameterEvaluation(Double.NaN, null));
            }
        }
        return result;
    }

    private Map<String, ParameterEvaluation> buildSimulatedParameters(
            StressTestRequestDTO request,
            List<ParameterConfig> parameters,
            List<InMemoryRow> originalRows,
            List<InMemoryRow> simulatedRows,
            Map<String, ParameterEvaluation> baselineParameters,
            Set<String> affectedFields
    ) {
        Map<String, ParameterEvaluation> simulated = new LinkedHashMap<>(baselineParameters);

        if (request.getMethod() == StressTestMethod.BALANCE) {
            for (ParameterConfig parameter : parameters) {
                Set<String> fields = dependencyAnalyzer.fieldsUsedByParameter(parameter);
                if (Collections.disjoint(fields, affectedFields)) {
                    continue;
                }
                ParameterEvaluation baseline = baselineParameters.get(parameter.getCode());
                if (baseline == null || baseline.definition == null) {
                    continue;
                }
                double value = formulaEvaluator.evaluate(baseline.definition, simulatedRows);
                simulated.put(parameter.getCode(), new ParameterEvaluation(value, baseline.definition));
            }
            return simulated;
        }

        Map<String, ParameterConfig> parameterByCode = new HashMap<>();
        parameters.forEach(p -> parameterByCode.put(p.getCode(), p));

        for (int index = 0; index < request.getParameterAdjustments().size(); index++) {
            ParameterAdjustmentDTO adjustment = request.getParameterAdjustments().get(index);
            String path = "parameterAdjustments[" + index + "]";

            if (adjustment.getOperation() == null) {
                throw new StressTestException("INVALID_OPERATION", path + ": operation is required");
            }
            if (adjustment.getCode() == null || adjustment.getCode().isBlank()) {
                throw new StressTestException("INVALID_OPERATION", path + ": code is required");
            }

            String code = adjustment.getCode().trim();
            ParameterConfig parameter = parameterByCode.get(code);
            if (parameter == null) {
                parameter = parameterConfigRepository.findByCode(code).orElseThrow(
                        () -> new StressTestException("UNKNOWN_PARAMETER", "Unknown parameter code: " + code)
                );
                parameterByCode.put(code, parameter);
                if (!baselineParameters.containsKey(code)) {
                    try {
                        FormulaDefinition definition = formulaValidationService.validateAndParse(parameter.getFormulaJson());
                        double value = formulaEvaluator.evaluate(definition, originalRows);
                        baselineParameters.put(code, new ParameterEvaluation(value, definition));
                    } catch (RuntimeException ex) {
                        baselineParameters.put(code, new ParameterEvaluation(Double.NaN, null));
                    }
                    simulated.put(code, baselineParameters.get(code));
                }
            }

            ParameterEvaluation baselineEval = baselineParameters.get(code);
            double baseline = baselineEval == null ? 0d : baselineEval.value;

            double simulatedValue;
            FormulaDefinition simulatedDefinition = baselineEval == null ? null : baselineEval.definition;

            switch (adjustment.getOperation()) {
                case MULTIPLY -> {
                    requireScalar(adjustment, path);
                    simulatedValue = baseline * adjustment.getValue();
                }
                case ADD -> {
                    requireScalar(adjustment, path);
                    simulatedValue = baseline + adjustment.getValue();
                }
                case REPLACE -> {
                    requireScalar(adjustment, path);
                    simulatedValue = adjustment.getValue();
                }
                case MODIFY_FORMULA -> {
                    if (adjustment.getFormula() == null || adjustment.getFormula().isNull()) {
                        throw new StressTestException(
                                "INVALID_OPERATION",
                                path + ": formula is required for MODIFY_FORMULA"
                        );
                    }
                    FormulaDefinition newDefinition;
                    try {
                        newDefinition = formulaValidationService.validateAndParse(adjustment.getFormula());
                    } catch (RuntimeException ex) {
                        throw new StressTestException(
                                "INVALID_OPERATION",
                                path + ".formula: " + ex.getMessage()
                        );
                    }
                    simulatedValue = formulaEvaluator.evaluate(newDefinition, originalRows);
                    simulatedDefinition = newDefinition;
                }
                default -> throw new StressTestException(
                        "INVALID_OPERATION",
                        path + ": unsupported operation " + adjustment.getOperation()
                );
            }

            simulated.put(code, new ParameterEvaluation(simulatedValue, simulatedDefinition));
        }

        return simulated;
    }

    private void requireScalar(ParameterAdjustmentDTO adjustment, String path) {
        if (adjustment.getValue() == null) {
            throw new StressTestException(
                    "INVALID_OPERATION",
                    path + ": value is required for operation " + adjustment.getOperation()
            );
        }
    }

    // ---------------------------------------------------------------------
    // Ratio evaluation
    // ---------------------------------------------------------------------

    private List<RatioImpactDTO> buildRatioImpacts(
            List<RatiosConfig> ratios,
            Map<String, ParameterEvaluation> baselineParameters,
            Map<String, ParameterEvaluation> simulatedParameters,
            LocalDate referenceDate,
            Set<String> affectedRatios
    ) {
        Map<String, Double> baselineScalar = toScalarMap(baselineParameters);
        Map<String, Double> simulatedScalar = toScalarMap(simulatedParameters);

        Map<Long, Double> dashboardByRatioId = loadDashboardValues(ratios, referenceDate);

        Map<String, Double> baselineRatioLog = new LinkedHashMap<>();
        Map<String, Double> simulatedRatioLog = new LinkedHashMap<>();

        List<RatioImpactDTO> impacts = new ArrayList<>(ratios.size());
        for (RatiosConfig ratio : ratios) {
            double baseline;
            double simulated;
            ExpressionNode expression;
            try {
                expression = ratioFormulaMapper.toExpressionNode(ratio.getFormula());
                baseline = evaluateRatio(expression, baselineScalar);
                simulated = evaluateRatio(expression, simulatedScalar);
            } catch (RuntimeException ex) {
                impacts.add(RatioImpactDTO.builder()
                        .code(ratio.getCode())
                        .label(ratio.getLabel())
                        .original(Double.NaN)
                        .simulated(Double.NaN)
                        .dashboardValue(dashboardByRatioId.get(ratio.getId()))
                        .delta(Double.NaN)
                        .impactPercent(Double.NaN)
                        .impacted(affectedRatios.contains(ratio.getCode()))
                        .changed(false)
                        .build());
                continue;
            }

            // IMPORTANT: compare freshly-computed baseline vs freshly-computed simulated so that
            // {@code delta} reflects ONLY the simulation effect (and not any drift between the
            // stored dashboard value and the current in-memory computation). The dashboardValue
            // remains available in the response as a sanity reference.
            Double dashboardValue = dashboardByRatioId.get(ratio.getId());
            double delta = simulated - baseline;
            Double impactPercent = Math.abs(baseline) < ZERO_EPSILON ? null : (delta / baseline) * 100d;
            boolean changed = Math.abs(delta) > CHANGE_EPSILON;

            baselineRatioLog.put(ratio.getCode(), baseline);
            simulatedRatioLog.put(ratio.getCode(), simulated);

            impacts.add(RatioImpactDTO.builder()
                    .code(ratio.getCode())
                    .label(ratio.getLabel())
                    .original(baseline)
                    .simulated(simulated)
                    .dashboardValue(dashboardValue)
                    .delta(delta)
                    .impactPercent(impactPercent)
                    .impacted(affectedRatios.contains(ratio.getCode()))
                    .changed(changed)
                    .build());
        }

        logRatioValues("baseline", baselineRatioLog);
        logRatioValues("simulated", simulatedRatioLog);

        return impacts;
    }

    private void logRatioValues(String phase, Map<String, Double> values) {
        if (!log.isInfoEnabled() || values.isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            if (count++ > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        log.info("[stress-test] {} ratio values | {}", phase, builder);
    }

    private double evaluateRatio(ExpressionNode node, Map<String, Double> parameterValues) {
        if (node == null) {
            return 0d;
        }
        String type = node.getType() == null ? "" : node.getType().trim().toUpperCase(Locale.ROOT);

        if ("PARAM".equals(type)) {
            if (!(node instanceof ParamNode paramNode) || paramNode.getCode() == null) {
                throw new StressTestException("INVALID_OPERATION", "Invalid ratio PARAM node");
            }
            Double value = parameterValues.get(paramNode.getCode().trim());
            return value == null ? 0d : value;
        }
        if ("CONSTANT".equals(type)) {
            if (!(node instanceof ConstantNode constantNode) || constantNode.getValue() == null) {
                throw new StressTestException("INVALID_OPERATION", "Invalid ratio CONSTANT node");
            }
            return constantNode.getValue();
        }
        if (!(node instanceof BinaryNode binaryNode)) {
            throw new StressTestException("INVALID_OPERATION", "Invalid ratio node: " + type);
        }

        double left = evaluateRatio(binaryNode.getLeft(), parameterValues);
        double right = evaluateRatio(binaryNode.getRight(), parameterValues);

        return switch (type) {
            case "ADD" -> left + right;
            case "SUBTRACT" -> left - right;
            case "MULTIPLY" -> left * right;
            case "DIVIDE" -> Math.abs(right) < ZERO_EPSILON ? 0d : left / right;
            default -> throw new StressTestException("INVALID_OPERATION", "Unsupported ratio node type: " + type);
        };
    }

    private Map<Long, Double> loadDashboardValues(List<RatiosConfig> ratios, LocalDate referenceDate) {
        if (ratios.isEmpty() || referenceDate == null) {
            return Map.of();
        }
        List<DashboardEntry> rows = dashboardRepository.findByReferenceDateOrderByIdAsc(referenceDate);
        Map<Long, Double> byId = new HashMap<>();
        for (DashboardEntry entry : rows) {
            byId.put(entry.getIdRatios(), entry.getRatiosValue());
        }
        return byId;
    }

    // ---------------------------------------------------------------------
    // Response builders
    // ---------------------------------------------------------------------

    private List<ParameterImpactDTO> buildParameterImpacts(
            List<ParameterConfig> parameters,
            Map<String, ParameterEvaluation> baseline,
            Map<String, ParameterEvaluation> simulated,
            Set<String> affectedParameters
    ) {
        List<ParameterImpactDTO> impacts = new ArrayList<>(parameters.size());
        Set<String> emitted = new LinkedHashSet<>();

        for (ParameterConfig parameter : parameters) {
            String code = parameter.getCode();
            emitted.add(code);
            impacts.add(buildParameterImpact(code, parameter.getLabel(), baseline, simulated, affectedParameters));
        }

        // Include parameters that were only referenced through PARAMETER adjustments.
        for (String code : simulated.keySet()) {
            if (emitted.contains(code)) {
                continue;
            }
            impacts.add(buildParameterImpact(code, null, baseline, simulated, affectedParameters));
        }
        return impacts;
    }

    private ParameterImpactDTO buildParameterImpact(
            String code,
            String label,
            Map<String, ParameterEvaluation> baseline,
            Map<String, ParameterEvaluation> simulated,
            Set<String> affectedParameters
    ) {
        double original = baseline.getOrDefault(code, new ParameterEvaluation(0d, null)).value;
        double simulatedValue = simulated.getOrDefault(code, new ParameterEvaluation(original, null)).value;

        double delta = simulatedValue - original;
        Double impactPercent = Math.abs(original) < ZERO_EPSILON ? null : (delta / original) * 100d;
        boolean changed = Math.abs(delta) > CHANGE_EPSILON;

        return ParameterImpactDTO.builder()
                .code(code)
                .label(label)
                .original(original)
                .simulated(simulatedValue)
                .delta(delta)
                .impactPercent(impactPercent)
                .impacted(affectedParameters.contains(code))
                .changed(changed)
                .build();
    }

    // ---------------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------------

    private Map<String, Double> toScalarMap(Map<String, ParameterEvaluation> evaluations) {
        Map<String, Double> result = new HashMap<>(evaluations.size());
        evaluations.forEach((code, eval) -> result.put(code, eval.value));
        return result;
    }

    private List<InMemoryRow> deepCopyRows(List<InMemoryRow> rows) {
        List<InMemoryRow> copy = new ArrayList<>(rows.size());
        for (InMemoryRow row : rows) {
            copy.add(new InMemoryRow(row.asMap()));
        }
        return copy;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String str) {
            try {
                return new BigDecimal(str.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    // ---------------------------------------------------------------------
    // Internal aggregates
    // ---------------------------------------------------------------------

    private static final class BalanceApplicationResult {
        private BigDecimal positiveDelta = BigDecimal.ZERO;
        private BigDecimal negativeDelta = BigDecimal.ZERO;
        private final Set<String> affectedFields = new LinkedHashSet<>();
        private int impactedRowCount = 0;
    }

    private static final class ParameterEvaluation {
        private final double value;
        private final FormulaDefinition definition;

        private ParameterEvaluation(double value, FormulaDefinition definition) {
            this.value = value;
            this.definition = definition;
        }
    }

    // ---------------------------------------------------------------------
    // Unused helpers (kept so the stress-test package exposes collaborators)
    // ---------------------------------------------------------------------

    @SuppressWarnings("unused")
    private void referenceUnused() {
        Objects.requireNonNull(ratioFormulaValidatorService);
    }
}
