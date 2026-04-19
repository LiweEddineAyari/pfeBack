package projet.app.repository.mapping;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import projet.app.entity.mapping.ParameterConfig;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParameterConfigRepository extends JpaRepository<ParameterConfig, Long> {

    Optional<ParameterConfig> findByCode(String code);

    boolean existsByCode(String code);

    List<ParameterConfig> findAllByCodeIn(List<String> codes);

    Optional<ParameterConfig> findByCodeAndIsActiveTrue(String code);
}
