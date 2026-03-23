package projet.app.entity.quality;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "data_quality_result_compta", schema = "staging")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataQualityResultCompta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "null_check_count")
    private Integer nullCheckCount;

    @Column(name = "duplicate_count")
    private Integer duplicateCount;

    @Column(name = "type_check_count")
    private Integer typeCheckCount;

    @Column(name = "balance_sum")
    private Long balanceSum;

    @Column(name = "contrat_relation_check")
    private Integer contratRelationCheck;

    @Column(name = "tiers_relation_check")
    private Integer tiersRelationCheck;

    @Column(name = "total_issues")
    private Integer totalIssues;

    @Column(name = "status")
    private String status;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;
}
