package projet.app.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RatioDimensionValueDTO {

    private String dimensionKey;
    private Double value;
}
