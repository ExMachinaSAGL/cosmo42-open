package ch.exmachina.cosmo42.repositories;

import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IngestionJobRepository extends JpaRepository<IngestionJob, Long> {

    Optional<IngestionJob> findByUuid(String uuid);

    List<IngestionJob> findByStatusIn(List<IngestionJobStatus> statuses);

    void deleteByKbDocumentUuid(String kbDocumentUuid);

    @Modifying
    @Query("UPDATE IngestionJob j SET j.status = :status WHERE j.uuid = :uuid")
    void updateStatus(@Param("uuid") String uuid, @Param("status") IngestionJobStatus status);

    @Modifying
    @Query("UPDATE IngestionJob j SET j.status = :status, j.startedAt = :startedAt WHERE j.uuid = :uuid")
    void updateStatusAndStartedAt(@Param("uuid") String uuid, @Param("status") IngestionJobStatus status, @Param("startedAt") LocalDateTime startedAt);

    @Modifying
    @Query("UPDATE IngestionJob j SET j.status = :status, j.completedAt = :completedAt WHERE j.uuid = :uuid")
    void updateStatusAndCompletedAt(@Param("uuid") String uuid, @Param("status") IngestionJobStatus status, @Param("completedAt") LocalDateTime completedAt);

    @Modifying
    @Query("UPDATE IngestionJob j SET j.status = :status, j.errorMessage = :errorMessage WHERE j.uuid = :uuid")
    void updateStatusAndError(@Param("uuid") String uuid, @Param("status") IngestionJobStatus status, @Param("errorMessage") String errorMessage);

    @Modifying
    @Query("UPDATE IngestionJob j SET j.totalPages = :totalPages WHERE j.uuid = :uuid")
    void updateTotalPages(@Param("uuid") String uuid, @Param("totalPages") int totalPages);

    @Modifying
    @Query("UPDATE IngestionJob j SET j.kbDocumentUuid = :kbDocumentUuid WHERE j.uuid = :uuid")
    void updateKbDocumentUuid(@Param("uuid") String uuid, @Param("kbDocumentUuid") String kbDocumentUuid);
}
