package projet.app.repository.quality;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import projet.app.entity.quality.DataQualityResultContrat;

@Repository
public interface DataQualityResultContratRepository extends JpaRepository<DataQualityResultContrat, Long> {
}
