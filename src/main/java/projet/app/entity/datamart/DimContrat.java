package projet.app.entity.datamart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dim_contrat", schema = "datamart")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimContrat {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "id_client")
    private String idClient;

    @Column(name = "id_agence")
    private Long idAgence;

    @Column(name = "id_devise")
    private Long idDevise;

    @Column(name = "id_objetfinance")
    private Long idObjetfinance;

    @Column(name = "id_typcontrat")
    private Long idTypcontrat;

    @Column(name = "id_dateouverture")
    private Long idDateouverture;

    @Column(name = "id_dateecheance")
    private Long idDateecheance;

    @Column(name = "ancienneteimpaye")
    private Integer ancienneteimpaye;

    @Column(name = "tauxcontrat")
    private Integer tauxcontrat;

    @Column(name = "actif")
    private Integer actif;
}
