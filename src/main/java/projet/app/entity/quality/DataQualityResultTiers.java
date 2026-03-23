package projet.app.entity.quality;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity to store data quality check results for TIERS staging table.
 */
@Entity
@Table(name = "data_quality_result_tiers", schema = "staging")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataQualityResultTiers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "null_check_deleted")
    private Integer nullCheckDeleted;

    @Column(name = "duplicate_deleted")
    private Integer duplicateDeleted;

    @Column(name = "type_check_deleted")
    private Integer typeCheckDeleted;

    @Column(name = "total_deleted")
    private Integer totalDeleted;

    @Column(name = "status")
    private String status;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;
}
