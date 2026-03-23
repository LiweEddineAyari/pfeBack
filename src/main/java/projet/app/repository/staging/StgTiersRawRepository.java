package projet.app.repository.staging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import projet.app.entity.staging.StgTiersRaw;

@Repository
public interface StgTiersRawRepository extends JpaRepository<StgTiersRaw, Long> {
}
