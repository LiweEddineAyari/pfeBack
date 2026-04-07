package projet.app.entity.staging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mapping_config", schema = "mapping")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_source", nullable = false)
    private String tableSource;

    @Column(name = "table_target", nullable = false)
    private String tableTarget;

    @Column(name = "column_source", nullable = false)
    private String columnSource;

    @Column(name = "column_target", nullable = false)
    private String columnTarget;

    @Column(name = "configgroupnumber", nullable = false)
    private Integer configGroupNumber;
}
