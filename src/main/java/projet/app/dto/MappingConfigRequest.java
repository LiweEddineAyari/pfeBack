package projet.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MappingConfigRequest {

    @NotBlank
    private String tableSource;

    @NotBlank
    private String tableTarget;

    @NotBlank
    private String columnSource;

    @NotBlank
    private String columnTarget;

    @NotNull
    private Integer configGroupNumber;
}
