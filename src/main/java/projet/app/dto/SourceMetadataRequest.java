package projet.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SourceMetadataRequest {

    @NotNull
    @Valid
    private DbConnectionRequest connection;
}
