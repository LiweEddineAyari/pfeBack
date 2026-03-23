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

import java.time.LocalDate;

@Entity
@Table(name = "sub_dim_douteux", schema = "datamart")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubDimDouteux {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "douteux")
    private Integer douteux;

    @Column(name = "datdouteux")
    private LocalDate datdouteux;
}
