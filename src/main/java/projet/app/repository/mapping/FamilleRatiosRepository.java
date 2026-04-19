package projet.app.repository.mapping;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import projet.app.entity.mapping.FamilleRatios;

@Repository
public interface FamilleRatiosRepository extends JpaRepository<FamilleRatios, Long> {
}
