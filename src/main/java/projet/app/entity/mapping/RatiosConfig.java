package projet.app.entity.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "ratios_config", schema = "mapping")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatiosConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    @Column(name = "famille", nullable = false, length = 255)
    private String famille;

    @Column(name = "categorie", nullable = false, length = 255)
    private String categorie;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "formula_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode formula;

    @Column(name = "seuil_tolerance")
    private Double seuilTolerance;

    @Column(name = "seuil_alerte")
    private Double seuilAlerte;

    @Column(name = "seuil_appetence")
    private Double seuilAppetence;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "version")
    private Integer version;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;

        if (version == null) {
            version = 1;
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
