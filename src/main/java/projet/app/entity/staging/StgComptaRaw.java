package projet.app.entity.staging;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Staging table entity for COMPTA (accounting balances) raw data.
 * NO business transformation applied - raw ingestion only.
 */
@Entity
@Table(name = "stg_compta_raw", schema = "staging")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StgComptaRaw {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agence")
    private String agence;

    @Column(name = "devise")
    private String devise;

    @Column(name = "compte")
    private String compte;

    @Column(name = "chapitre")
    private String chapitre;

    @Column(name = "libellecompte")
    private String libellecompte;

    @Column(name = "idtiers")
    private String idtiers;

    @Column(name = "soldeorigine")
    private String soldeorigine;

    @Column(name = "soldeconvertie")
    private String soldeconvertie;

    @Column(name = "devisebbnq")
    private String devisebbnq;

    @Column(name = "cumulmvtdb")
    private String cumulmvtdb;

    @Column(name = "cumulmvtcr")
    private String cumulmvtcr;

    @Column(name = "soldeinitdebmois")
    private String soldeinitdebmois;

    @Column(name = "idcontrat")
    private String idcontrat;

    @Column(name = "amount")
    private String amount;

    @Column(name = "actif")
    private String actif;

    @Column(name = "date_bal")
    private String dateBal;
}
