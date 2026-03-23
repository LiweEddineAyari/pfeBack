package projet.app.repository.staging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import projet.app.entity.staging.StgComptaRaw;

@Repository
public interface StgComptaRawRepository extends JpaRepository<StgComptaRaw, Long> {
}
