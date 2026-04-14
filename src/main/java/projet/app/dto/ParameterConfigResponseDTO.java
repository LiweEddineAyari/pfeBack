package projet.app.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ParameterConfigResponseDTO {

    private Long id;
    private String code;
    private String label;
    private JsonNode formula;
    private Integer version;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
