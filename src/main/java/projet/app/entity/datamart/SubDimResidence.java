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
@Table(name = "sub_dim_residence", schema = "datamart")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubDimResidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pays")
    private String pays;

    @Column(name = "residence")
    private String residence;

    @Column(name = "geo")
    private String geo;
}
