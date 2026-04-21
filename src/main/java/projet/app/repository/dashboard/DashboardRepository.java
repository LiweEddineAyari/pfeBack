package projet.app.repository.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import projet.app.entity.dashboard.DashboardEntry;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DashboardRepository extends JpaRepository<DashboardEntry, Long> {

    List<DashboardEntry> findByReferenceDateOrderByIdAsc(LocalDate referenceDate);

    boolean existsByIdRatiosAndReferenceDate(Long idRatios, LocalDate referenceDate);
}