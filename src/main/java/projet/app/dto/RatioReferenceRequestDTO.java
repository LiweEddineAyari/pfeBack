package projet.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RatioReferenceRequestDTO {

    @NotBlank(message = "name is required")
    private String name;
}