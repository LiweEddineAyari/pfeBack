package projet.app.dto.stresstest;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RatioImpactDTO {

    private String code;
    private String label;
    private Double original;
    private Double simulated;
    private Double dashboardValue;
    private Double delta;
    private Double impactPercent;

    /**
     * Dependency-based flag: {@code true} when this ratio depends on at least one parameter that
     * is marked as dependency-impacted. Independent of whether the computed {@code simulated}
     * value numerically differs from {@code original}.
     */
    private Boolean impacted;

    /**
     * Numerical-change flag: {@code true} when {@code |simulated - original|} exceeds the
     * epsilon threshold. Only entries with {@code changed=true} are returned by default.
     */
    private Boolean changed;
}
