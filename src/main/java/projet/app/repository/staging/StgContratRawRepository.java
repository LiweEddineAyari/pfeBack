package projet.app.repository.staging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import projet.app.entity.staging.StgContratRaw;

@Repository
public interface StgContratRawRepository extends JpaRepository<StgContratRaw, Long> {
}
