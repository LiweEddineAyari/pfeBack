package projet.app.entity.datamart;

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
@Table(name = "fact_balance", schema = "datamart")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_agence")
    private Long idAgence;

    @Column(name = "id_devise")
    private Long idDevise;

    @Column(name = "id_devisebnq")
    private Long idDevisebnq;

    @Column(name = "id_compte")
    private Long idCompte;

    @Column(name = "id_chapitre")
    private Long idChapitre;

    @Column(name = "id_client")
    private String idClient;

    @Column(name = "id_contrat")
    private String idContrat;

    @Column(name = "id_date")
    private Long idDate;

    @Column(name = "soldeorigine")
    private Long soldeorigine;

    @Column(name = "soldeconvertie")
    private Long soldeconvertie;

    @Column(name = "cumulmvtdb")
    private Long cumulmvtdb;

    @Column(name = "cumulmvtcr")
    private Long cumulmvtcr;

    @Column(name = "soldeinitdebmois")
    private Long soldeinitdebmois;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "actif")
    private Integer actif;
}
