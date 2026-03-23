package projet.app.repository.quality;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import projet.app.entity.quality.DataQualityResultTiers;

@Repository
public interface DataQualityResultTiersRepository extends JpaRepository<DataQualityResultTiers, Long> {
}
