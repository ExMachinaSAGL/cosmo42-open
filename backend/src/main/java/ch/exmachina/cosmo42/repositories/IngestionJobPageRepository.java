package ch.exmachina.cosmo42.repositories;

import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobPage;
import ch.exmachina.cosmo42.entities.IngestionJobPageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IngestionJobPageRepository extends JpaRepository<IngestionJobPage, Long> {

    List<IngestionJobPage> findByJobOrderByPageIndexAsc(IngestionJob job);

    Optional<IngestionJobPage> findByJobAndPageIndex(IngestionJob job, int pageIndex);

    boolean existsByJobAndPageIndex(IngestionJob job, int pageIndex);

    long countByJobAndStatus(IngestionJob job, IngestionJobPageStatus status);

    @Query("SELECT p.pageIndex FROM IngestionJobPage p WHERE p.job = :job AND p.status = :status")
    List<Integer> findPageIndicesByJobAndStatus(@Param("job") IngestionJob job,
                                                @Param("status") IngestionJobPageStatus status);

    @Query("""
            SELECT p.pageIndex FROM IngestionJobPage p
             WHERE p.job = :job
               AND (p.status = ch.exmachina.cosmo42.entities.IngestionJobPageStatus.PENDING
                    OR (p.status = ch.exmachina.cosmo42.entities.IngestionJobPageStatus.FAILED
                        AND p.attemptCount < :maxAttempts))
            """)
    List<Integer> findRetryablePageIndices(@Param("job") IngestionJob job,
                                           @Param("maxAttempts") int maxAttempts);

    @Query("""
            SELECT COUNT(p) FROM IngestionJobPage p
             WHERE p.job = :job
               AND p.status = ch.exmachina.cosmo42.entities.IngestionJobPageStatus.FAILED
               AND p.attemptCount >= :maxAttempts
            """)
    long countExhaustedFailures(@Param("job") IngestionJob job,
                                @Param("maxAttempts") int maxAttempts);
}
