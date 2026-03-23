package projet.app.entity.staging;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Staging table entity for TIERS (clients) raw data.
 * NO business transformation applied - raw ingestion only.
 */
@Entity
@Table(name = "stg_tiers_raw", schema = "staging")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StgTiersRaw {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idtiers")
    private String idtiers;

    @Column(name = "nomprenom")
    private String nomprenom;

    @Column(name = "raisonsoc")
    private String raisonsoc;

    @Column(name = "residence")
    private String residence;

    @Column(name = "agenteco")
    private String agenteco;

    @Column(name = "sectionactivite")
    private String sectionactivite;

    @Column(name = "chiffreaffaires")
    private String chiffreaffaires;

    @Column(name = "nationalite")
    private String nationalite;

    @Column(name = "douteux")
    private String douteux;

    @Column(name = "datdouteux")
    private String datdouteux;

    @Column(name = "grpaffaires")
    private Integer grpaffaires;

    @Column(name = "nomgrpaffaires")
    private String nomgrpaffaires;

    @Column(name = "residencenum")
    private Integer residencenum;
}
