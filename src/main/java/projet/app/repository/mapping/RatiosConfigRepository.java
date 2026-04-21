package projet.app.repository.mapping;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import projet.app.entity.mapping.RatiosConfig;

import java.util.List;
import java.util.Optional;

@Repository
public interface RatiosConfigRepository extends JpaRepository<RatiosConfig, Long> {

    Optional<RatiosConfig> findByCode(String code);

    boolean existsByCode(String code);

    List<RatiosConfig> findAllByCodeIn(List<String> codes);

    Optional<RatiosConfig> findByCodeAndIsActiveTrue(String code);

    boolean existsByFamilleId(Long familleId);

    boolean existsByCategorieId(Long categorieId);
}
