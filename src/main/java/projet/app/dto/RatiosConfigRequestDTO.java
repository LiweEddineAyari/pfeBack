package projet.app.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RatiosConfigRequestDTO {

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "label is required")
    private String label;

    @NotNull(message = "familleId is required")
    private Long familleId;

    @NotNull(message = "categorieId is required")
    private Long categorieId;

    @NotNull(message = "formula is required")
    private JsonNode formula;

    private Double seuilTolerance;
    private Double seuilAlerte;
    private Double seuilAppetence;
    private String description;
    private Boolean isActive;
}
