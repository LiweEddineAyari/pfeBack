package projet.app.ai.rag.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import projet.app.ai.rag.entity.RagDocumentEntity;

import java.util.List;
import java.util.UUID;

public interface RagDocumentRepository extends JpaRepository<RagDocumentEntity, UUID> {

    List<RagDocumentEntity> findByRatioCode(String ratioCode);

    List<RagDocumentEntity> findByDomain(String domain);

    List<RagDocumentEntity> findByParameterCode(String parameterCode);

    List<RagDocumentEntity> findByDocumentTypeAndRatioCode(String documentType, String ratioCode);

    long countByDocumentType(String documentType);

    long countBySource(String source);

    @Modifying
    @Query("DELETE FROM RagDocumentEntity d WHERE d.source = :source")
    int deleteBySource(@Param("source") String source);
}
