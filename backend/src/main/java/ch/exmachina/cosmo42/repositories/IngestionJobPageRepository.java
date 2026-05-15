package ch.exmachina.cosmo42.repositories;

import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobPage;
import ch.exmachina.cosmo42.entities.IngestionJobPageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngestionJobPageRepository extends JpaRepository<IngestionJobPage, Long> {

    List<IngestionJobPage> findByJobOrderByPageIndexAsc(IngestionJob job);

    boolean existsByJobAndPageIndex(IngestionJob job, int pageIndex);

    long countByJobAndStatus(IngestionJob job, IngestionJobPageStatus status);

    @Query("SELECT p.pageIndex FROM IngestionJobPage p WHERE p.job = :job AND p.status = :status")
    List<Integer> findPageIndicesByJobAndStatus(@Param("job") IngestionJob job,
                                                @Param("status") IngestionJobPageStatus status);
}
