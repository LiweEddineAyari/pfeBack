package projet.app.dto.stresstest;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for {@code GET /stress-test/diagnostics}. Exposes the dates for which
 * {@code fact_balance} currently has data so callers can pick a valid {@code referenceDate}
 * before invoking the stress-test simulation endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StressTestDiagnosticsResponseDTO {

    /** The {@code referenceDate} the caller asked about (may be {@code null}). */
    private LocalDate referenceDate;

    /** Number of {@code fact_balance} rows found for {@link #referenceDate} (0 when unknown). */
    private Long rowCountForReferenceDate;

    /** Most recent distinct reference dates present in {@code fact_balance}, newest first. */
    private List<LocalDate> availableReferenceDates;

    /** Global row count in {@code fact_balance}. */
    private Long totalFactRows;
}
