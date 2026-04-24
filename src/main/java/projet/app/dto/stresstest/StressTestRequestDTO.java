package projet.app.dto.stresstest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Data
public class StressTestRequestDTO {

    @NotNull(message = "method is required (BALANCE or PARAMETER)")
    private StressTestMethod method;

    @NotNull(message = "referenceDate is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate referenceDate;

    @Valid
    private List<BalanceAdjustmentDTO> balanceAdjustments;

    @Valid
    private List<ParameterAdjustmentDTO> parameterAdjustments;

    /**
     * Optional scope. If null or empty, all active parameters are evaluated and only impacted
     * parameters are returned. If provided, only the listed parameter codes are evaluated.
     */
    private List<String> parameterCodes;

    /**
     * Optional scope. If null or empty, all active ratios are evaluated and only impacted
     * ratios are returned. If provided, only the listed ratio codes are evaluated.
     */
    private List<String> ratioCodes;
}
