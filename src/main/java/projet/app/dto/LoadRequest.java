package projet.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class LoadRequest {

    @NotNull
    @Valid
    private DbConnectionRequest connection;

    @NotNull
    private IngestionType type;

    @JsonProperty("date_bal")
    private String dateBal;

    private Map<String, String> mapping = new LinkedHashMap<>();

    private String targetTable;
}
