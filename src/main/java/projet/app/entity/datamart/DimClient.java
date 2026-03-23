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
@Table(name = "dim_client", schema = "datamart")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimClient {

    @Id
    @Column(name = "idtiers")
    private String idtiers;

    @Column(name = "id_residence")
    private Long idResidence;

    @Column(name = "id_agenteco")
    private Long idAgenteco;

    @Column(name = "id_douteux")
    private Long idDouteux;

    @Column(name = "id_grpaffaire")
    private Long idGrpaffaire;

    @Column(name = "id_sectionactivite")
    private Long idSectionactivite;

    @Column(name = "nomprenom")
    private String nomprenom;

    @Column(name = "raisonsoc")
    private String raisonsoc;

    @Column(name = "chiffreaffaires")
    private String chiffreaffaires;
}
