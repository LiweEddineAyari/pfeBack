package projet.app.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LoadFromDbRequest {

    @NotNull
    @Valid
    private DbConnectionRequest connection;

    @NotNull
    @JsonAlias("configgroupnumber")
    private Integer configGroupNumber;

    @JsonProperty("date_bal")
    private String dateBal;
}
