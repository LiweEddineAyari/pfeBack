package projet.app.repository.quality;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import projet.app.entity.quality.DataQualityResultCompta;

@Repository
public interface DataQualityResultComptaRepository extends JpaRepository<DataQualityResultCompta, Long> {
}
