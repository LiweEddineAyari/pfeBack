package projet.app.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import projet.app.ai.tools.dto.RatioTrendDTO;
import projet.app.ai.tools.dto.ThresholdBriefDTO;
import projet.app.dto.DashboardRowResponseDTO;
import projet.app.dto.FormulaExecutionResponseDTO;
import projet.app.dto.ParameterConfigResponseDTO;
import projet.app.dto.RatioExecutionResponseDTO;
import projet.app.dto.RatiosConfigResponseDTO;
import projet.app.dto.stresstest.StressTestDiagnosticsResponseDTO;
import projet.app.dto.stresstest.StressTestRequestDTO;
import projet.app.dto.stresstest.StressTestResponseDTO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The set of {@link Tool}s that the {@code FinancialAiService} (LangChain4j proxy)
 * exposes to GPT-4o for function-calling. Each method is translated into a JSON
 * schema and shipped to the model alongside the user prompt.
 *
 * <p>Engineering contract:
 * <ul>
 *   <li>The LLM never computes financial values. Every numeric result returned
 *       here originates from a backend REST call against the existing modules
 *       (Parameters / Ratios / Dashboard / Stress-Test).</li>
 *   <li>Composite tools ({@link #compareRatioAcrossDates}, {@link #checkThresholdBreaches})
 *       perform their math in Java, not via the model.</li>
 *   <li>Tool descriptions are deliberately verbose: they are the contract that
 *       the model uses to decide when to call.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinancialTools {

    private static final ParameterizedTypeReference<List<DashboardRowResponseDTO>> DASHBOARD_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<RatiosConfigResponseDTO>> RATIO_CONFIG_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ParameterConfigResponseDTO>> PARAM_CONFIG_LIST =
            new ParameterizedTypeReference<>() {};

    private final BackendApiClient client;

    // ─── TOOL 1: Execute a single parameter at a reference date ─────────────────

    @Tool(name = "execute_parameter",
            value = "Execute a banking parameter formula and get its real computed value " +
                    "from the DataMart at a specific reference date. " +
                    "Use this for parameters like FPE, RCR, RM, RO, FPT1, TOEXP, ENCACTL, " +
                    "SNT, ACTL, PAEX, RNET, TACT, PNB, ENCTENG, FPBT1, FPBT2, ENTENG, ENTRES.")
    public FormulaExecutionResponseDTO executeParameter(
            @P("The parameter code, e.g. FPE, RCR, TOEXP") String code,
            @P("Reference date in YYYY-MM-DD format") String date) {
        log.info("[tool] execute_parameter code={} date={}", code, date);
        return client.post("/parameters/" + code + "/execute/" + date,
                null, FormulaExecutionResponseDTO.class);
    }

    // ─── TOOL 2: Execute a single ratio at a reference date ─────────────────────

    @Tool(name = "execute_ratio",
            value = "Execute a financial ratio and get its real computed value at a specific " +
                    "reference date. Use for ratios like RS (Solvabilité), RCET1, RT1, RL " +
                    "(Levier), RLCT (Liquidité CT), RLLT (Liquidité LT), COEL, ROE, ROA, " +
                    "COEEXP, RNPL, TECH, TCR, TCS, TCPS, TCGAR, LGPARTCOM.")
    public RatioExecutionResponseDTO executeRatio(
            @P("The ratio code, e.g. RS, RCET1, RLCT") String code,
            @P("Reference date in YYYY-MM-DD format") String date) {
        log.info("[tool] execute_ratio code={} date={}", code, date);
        return client.post("/ratios/" + code + "/execute/" + date,
                null, RatioExecutionResponseDTO.class);
    }

    // ─── TOOL 3: Get full dashboard for a date (all ratios + thresholds) ────────

    @Tool(name = "get_dashboard_by_date",
            value = "Get the complete dashboard for a reference date: every ratio's stored " +
                    "value with its thresholds (seuilTolerance, seuilAlerte, seuilAppetence), " +
                    "family code and category code. Use this for an overview of the bank's " +
                    "financial position on a given date.")
    public List<DashboardRowResponseDTO> getDashboardByDate(
            @P("Reference date in YYYY-MM-DD format") String date) {
        log.info("[tool] get_dashboard_by_date date={}", date);
        return client.get("/dashboard/date/" + date, DASHBOARD_LIST);
    }

    // ─── TOOL 4: Get all dashboard rows across all dates ────────────────────────

    @Tool(name = "get_all_dashboard_rows",
            value = "Retrieve all dashboard rows across ALL available reference dates. " +
                    "Use this for multi-date trend analysis or to discover available dates.")
    public List<DashboardRowResponseDTO> getAllDashboardRows() {
        log.info("[tool] get_all_dashboard_rows");
        return client.get("/dashboard", DASHBOARD_LIST);
    }

    // ─── TOOL 5: Compare a ratio across multiple dates (trend, computed in Java) ─

    @Tool(name = "compare_ratio_across_dates",
            value = "Compare a financial ratio value across multiple reference dates to " +
                    "analyse its trend over time. Returns each (date, value) pair, the " +
                    "absolute delta between the latest and earliest values, the percentage " +
                    "change, and a direction tag (IMPROVING / DETERIORATING / STABLE). " +
                    "Use whenever the user asks about evolution / trend / comparison.")
    public RatioTrendDTO compareRatioAcrossDates(
            @P("The ratio code, e.g. RS, RLCT") String code,
            @P("List of reference dates in YYYY-MM-DD format") List<String> dates) {
        log.info("[tool] compare_ratio_across_dates code={} dates={}", code, dates);
        if (dates == null || dates.isEmpty()) {
            return new RatioTrendDTO(code, List.of(), 0.0, 0.0, "STABLE");
        }

        List<RatioTrendDTO.DataPoint> points = new ArrayList<>();
        for (String date : dates) {
            try {
                RatioExecutionResponseDTO result = client.post(
                        "/ratios/" + code + "/execute/" + date, null,
                        RatioExecutionResponseDTO.class);
                points.add(new RatioTrendDTO.DataPoint(date, result.getValue()));
            } catch (RuntimeException ex) {
                log.warn("compare_ratio_across_dates: could not execute {} at {}: {}",
                        code, date, ex.getMessage());
                points.add(new RatioTrendDTO.DataPoint(date, null));
            }
        }
        points.sort(Comparator.comparing(RatioTrendDTO.DataPoint::date));

        Double first = firstNonNull(points);
        Double last = lastNonNull(points);
        double delta = (first != null && last != null) ? last - first : 0.0;
        double percentChange = (first != null && first != 0.0 && last != null)
                ? (delta / first) * 100.0
                : 0.0;
        String direction = delta > 0.001 ? "IMPROVING"
                : delta < -0.001 ? "DETERIORATING"
                : "STABLE";

        return new RatioTrendDTO(code, points, delta, percentChange, direction);
    }

    // ─── TOOL 6: Threshold breach categorisation (computed in Java) ─────────────

    @Tool(name = "check_threshold_breaches",
            value = "Analyse the threshold breach status for all ratios on a given date. " +
                    "Returns ratios bucketed by severity: CRITICAL (below seuilAppetence), " +
                    "ALERT (below seuilAlerte), WARNING (below seuilTolerance), and HEALTHY " +
                    "(above all thresholds). Use whenever the user asks about risk status, " +
                    "breaches, alarms or portfolio health.")
    public ThresholdBriefDTO checkThresholdBreaches(
            @P("Reference date in YYYY-MM-DD format") String date) {
        log.info("[tool] check_threshold_breaches date={}", date);
        List<DashboardRowResponseDTO> rows = getDashboardByDate(date);

        List<ThresholdBriefDTO.BreachItem> critical = new ArrayList<>();
        List<ThresholdBriefDTO.BreachItem> alert = new ArrayList<>();
        List<ThresholdBriefDTO.BreachItem> warning = new ArrayList<>();
        List<ThresholdBriefDTO.BreachItem> healthy = new ArrayList<>();

        for (DashboardRowResponseDTO row : rows) {
            Double value = row.getValue();
            if (value == null) {
                continue;
            }
            if (row.getSeuilAppetence() != null && value < row.getSeuilAppetence()) {
                critical.add(toBreachItem(row, "CRITICAL"));
            } else if (row.getSeuilAlerte() != null && value < row.getSeuilAlerte()) {
                alert.add(toBreachItem(row, "ALERT"));
            } else if (row.getSeuilTolerance() != null && value < row.getSeuilTolerance()) {
                warning.add(toBreachItem(row, "WARNING"));
            } else {
                healthy.add(toBreachItem(row, "HEALTHY"));
            }
        }
        return new ThresholdBriefDTO(date, critical, alert, warning, healthy);
    }

    // ─── TOOL 7: Run stress-test simulation ─────────────────────────────────────

    @Tool(name = "run_stress_test",
            value = "Run an in-memory what-if stress-test simulation. Supports the PARAMETER " +
                    "method (adjust parameter values via MULTIPLY, ADD, REPLACE) and the " +
                    "BALANCE method (row-level balance overrides). Returns the original " +
                    "value, simulated value, delta and impactPercent for every affected " +
                    "ratio. Does NOT persist any data. Always verify date availability via " +
                    "get_stress_test_diagnostics before calling.")
    public StressTestResponseDTO runStressTest(
            @P("Stress-test request: method, referenceDate, balanceAdjustments or " +
                    "parameterAdjustments, optional parameterCodes / ratioCodes scope.")
            StressTestRequestDTO request) {
        log.info("[tool] run_stress_test method={} date={}",
                request.getMethod(), request.getReferenceDate());
        return client.post("/stress-test/simulate", request, StressTestResponseDTO.class);
    }

    // ─── TOOL 8: Get stress-test diagnostics (available dates) ──────────────────

    @Tool(name = "get_stress_test_diagnostics",
            value = "Get available reference dates and row counts for stress-test simulation. " +
                    "Always call this BEFORE run_stress_test to verify date availability in " +
                    "the DataMart.")
    public StressTestDiagnosticsResponseDTO getStressTestDiagnostics() {
        log.info("[tool] get_stress_test_diagnostics");
        return client.get("/stress-test/diagnostics",
                StressTestDiagnosticsResponseDTO.class);
    }

    // ─── TOOL 9: List all configured ratios ─────────────────────────────────────

    @Tool(name = "list_all_ratios",
            value = "List every configured financial ratio with its code, label, thresholds, " +
                    "formula tree, family and category. Use when the user asks what ratios " +
                    "exist, or to find a ratio code before executing it.")
    public List<RatiosConfigResponseDTO> listAllRatios() {
        log.info("[tool] list_all_ratios");
        return client.get("/ratios", RATIO_CONFIG_LIST);
    }

    // ─── TOOL 10: Get single ratio config detail ────────────────────────────────

    @Tool(name = "get_ratio_detail",
            value = "Get detailed configuration of a specific ratio by code: formula tree, " +
                    "thresholds, family, category, description. Use when the user asks for " +
                    "formula details or ratio metadata.")
    public RatiosConfigResponseDTO getRatioDetail(
            @P("The ratio code, e.g. RS, RCET1") String code) {
        log.info("[tool] get_ratio_detail code={}", code);
        return client.get("/ratios/" + code, RatiosConfigResponseDTO.class);
    }

    // ─── TOOL 11: List all parameters ───────────────────────────────────────────

    @Tool(name = "list_all_parameters",
            value = "List every configured banking parameter with its code and label. " +
                    "Use when the user asks what parameters exist or wants to discover " +
                    "parameter codes before referencing them.")
    public List<ParameterConfigResponseDTO> listAllParameters() {
        log.info("[tool] list_all_parameters");
        return client.get("/parameters", PARAM_CONFIG_LIST);
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private ThresholdBriefDTO.BreachItem toBreachItem(DashboardRowResponseDTO r, String severity) {
        return new ThresholdBriefDTO.BreachItem(
                r.getCode(),
                r.getLabel(),
                r.getValue(),
                r.getSeuilTolerance(),
                r.getSeuilAlerte(),
                r.getSeuilAppetence(),
                r.getFamilleCode(),
                r.getCategorieCode(),
                severity
        );
    }

    private static Double firstNonNull(List<RatioTrendDTO.DataPoint> points) {
        for (RatioTrendDTO.DataPoint p : points) {
            if (p.value() != null) {
                return p.value();
            }
        }
        return null;
    }

    private static Double lastNonNull(List<RatioTrendDTO.DataPoint> points) {
        for (int i = points.size() - 1; i >= 0; i--) {
            if (points.get(i).value() != null) {
                return points.get(i).value();
            }
        }
        return null;
    }
}
