package projet.app.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RatiosConfigResponseDTO {

    private Long id;
    private String code;
    private String label;
    private Long familleId;
    private Long categorieId;
    private JsonNode formula;
    private Double seuilTolerance;
    private Double seuilAlerte;
    private Double seuilAppetence;
    private String description;
    private Integer version;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
