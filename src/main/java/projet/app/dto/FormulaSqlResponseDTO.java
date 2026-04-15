package projet.app.dto;

import lombok.Builder;
import lombok.Data;
import projet.app.engine.ast.OrderByNode;

import java.util.List;
import java.util.Set;

@Data
@Builder
public class FormulaSqlResponseDTO {

    private String code;
    private Integer version;
    private String sql;
    private List<Object> parameters;
    private Set<String> referencedFields;
    private List<String> joins;
    private List<String> groupByFields;
    private List<OrderByNode> orderBy;
    private Integer limit;
    private Integer top;
}
