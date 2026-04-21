package projet.app.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RatioReferenceResponseDTO {

    private Long id;
    private String name;
}