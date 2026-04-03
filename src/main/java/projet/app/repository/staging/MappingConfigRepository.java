package projet.app.repository.staging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import projet.app.entity.staging.MappingConfig;

import java.util.List;

@Repository
public interface MappingConfigRepository extends JpaRepository<MappingConfig, Long> {

	List<MappingConfig> findByConfigGroupNumberOrderByTableTargetAscIdAsc(Integer configGroupNumber);
}
