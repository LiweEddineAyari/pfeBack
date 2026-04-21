package projet.app.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class DashboardCreateRequestDTO {

    @NotNull(message = "idRatios is required")
    private Long idRatios;

    @NotNull(message = "value is required")
    private Double value;

    @NotNull(message = "date is required")
    private LocalDate date;
}