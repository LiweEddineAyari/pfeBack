package projet.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DbConnectionRequest {

    @NotBlank
    private String host;

    @Min(1)
    @Max(65535)
    private int port;

    @NotBlank
    private String database;

    @NotNull
    private DbType dbType;

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    private String table;
}
