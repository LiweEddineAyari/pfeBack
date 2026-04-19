package projet.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class SupportedFieldsResponseDTO {

    private List<String> fields;
    private Map<String, List<String>> fieldsByTable;
}
