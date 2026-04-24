package projet.app.dto.stresstest;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParameterImpactDTO {

    private String code;
    private String label;
    private Double original;
    private Double simulated;
    private Double delta;
    private Double impactPercent;

    /**
     * Dependency-based flag: {@code true} when this parameter references one of the fields
     * touched by the simulation (via the field -> parameter dependency graph). Stays {@code true}
     * even if the computed {@code simulated} value ended up equal to {@code original} (e.g. because
     * the adjusted rows were excluded by the parameter's own filter).
     */
    private Boolean impacted;

    /**
     * Numerical-change flag: {@code true} when {@code |simulated - original|} exceeds the
     * epsilon threshold. Only entries with {@code changed=true} are returned by default.
     */
    private Boolean changed;
}
