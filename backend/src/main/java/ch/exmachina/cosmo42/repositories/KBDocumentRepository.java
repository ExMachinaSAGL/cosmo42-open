package ch.exmachina.cosmo42.repositories;

import ch.exmachina.cosmo42.entities.KBDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KBDocumentRepository extends JpaRepository<KBDocument, Long> {

    Optional<KBDocument> findByUuid(String uuid);
    void deleteByUuid(String uuid);

}
