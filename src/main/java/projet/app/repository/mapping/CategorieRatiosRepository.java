package projet.app.repository.mapping;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import projet.app.entity.mapping.CategorieRatios;

@Repository
public interface CategorieRatiosRepository extends JpaRepository<CategorieRatios, Long> {
}
