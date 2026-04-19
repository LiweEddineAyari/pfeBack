package projet.app.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FormulaRequestDTO {

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "label is required")
    private String label;

    private JsonNode formula;

    private String nativeSql;

    private Boolean isActive;

    @AssertTrue(message = "Either formula or nativeSql is required")
    public boolean hasFormulaOrNativeSql() {
        boolean hasFormula = formula != null && !formula.isNull() && !formula.isMissingNode();
        boolean hasNativeSql = nativeSql != null && !nativeSql.isBlank();
        return hasFormula || hasNativeSql;
    }
}
