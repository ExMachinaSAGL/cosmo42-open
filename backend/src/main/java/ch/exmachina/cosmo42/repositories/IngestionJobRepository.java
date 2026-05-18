package ch.exmachina.cosmo42.repositories;

import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IngestionJobRepository extends JpaRepository<IngestionJob, Long> {

    Optional<IngestionJob> findByUuid(String uuid);

    List<IngestionJob> findByStatusIn(List<IngestionJobStatus> statuses);

    void deleteByKbDocumentUuid(String kbDocumentUuid);
}
