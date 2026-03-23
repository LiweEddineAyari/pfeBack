package projet.app.entity.staging;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Staging table entity for CONTRAT (contracts/credits) raw data.
 * NO business transformation applied - raw ingestion only.
 */
@Entity
@Table(name = "stg_contrat_raw", schema = "staging")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StgContratRaw {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idcontrat")
    private String idcontrat;

    @Column(name = "agence")
    private String agence;

    @Column(name = "devise")
    private String devise;

    @Column(name = "ancienneteimpaye")
    private String ancienneteimpaye;

    @Column(name = "objetfinance")
    private String objetfinance;

    @Column(name = "typcontrat")
    private String typcontrat;

    @Column(name = "datouv")
    private String datouv;

    @Column(name = "datech")
    private String datech;

    @Column(name = "idtiers")
    private String idtiers;

    @Column(name = "tauxcontrat")
    private String tauxcontrat;

    @Column(name = "actif")
    private String actif;
}
