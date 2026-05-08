package projet.app.ai.memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import projet.app.ai.memory.entity.ExtractedEntityEntity;

import java.util.UUID;

public interface ExtractedEntityRepository extends JpaRepository<ExtractedEntityEntity, UUID> {
}
