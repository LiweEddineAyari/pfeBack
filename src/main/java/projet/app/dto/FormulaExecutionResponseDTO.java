package projet.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FormulaExecutionResponseDTO {

    private String code;
    private String sql;
    private List<Object> parameters;
    private Object value;
}
