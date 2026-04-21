package projet.app.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class DashboardRowResponseDTO {

    private Long id;
    private Long idRatios;
    private String code;
    private String label;
    private String description;
    private Long familleId;
    private Long categorieId;
    private String familleCode;
    private String categorieCode;
    private Double seuilTolerance;
    private Double seuilAlerte;
    private Double seuilAppetence;
    private Double value;
    private LocalDate date;
}