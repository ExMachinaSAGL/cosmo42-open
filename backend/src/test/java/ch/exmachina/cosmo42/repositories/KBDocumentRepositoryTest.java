package ch.exmachina.cosmo42.repositories;

import ch.exmachina.cosmo42.AbstractIntegrationTest;
import ch.exmachina.cosmo42.entities.KBDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class KBDocumentRepositoryTest extends AbstractIntegrationTest {

    @Autowired KBDocumentRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void saveAndFindByUuid() {
        KBDocument doc = newDocument("report.pdf");
        repository.save(doc);

        var found = repository.findByUuid(doc.getUuid());

        assertThat(found).isPresent();
        assertThat(found.get().getFileName()).isEqualTo("report.pdf");
    }

    @Test
    void findByUuidReturnsEmptyWhenMissing() {
        assertThat(repository.findByUuid("00000000-0000-0000-0000-000000000000")).isEmpty();
    }

    @Test
    void deleteByUuidRemovesRow() {
        KBDocument doc = newDocument("doomed.pdf");
        repository.save(doc);

        repository.deleteByUuid(doc.getUuid());
        repository.flush();

        assertThat(repository.findByUuid(doc.getUuid())).isEmpty();
    }

    @Test
    void deleteByUuidOnUnknownUuidIsNoOp() {
        // No throw expected; derived delete on no matching row is a clean no-op.
        repository.deleteByUuid("00000000-0000-0000-0000-000000000000");
        repository.flush();

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void uuidUniquenessIsEnforcedAtDbLevel() {
        String sharedUuid = UUID.randomUUID().toString();
        repository.saveAndFlush(documentWithUuid(sharedUuid, "first.pdf"));

        assertThatThrownBy(() -> repository.saveAndFlush(documentWithUuid(sharedUuid, "second.pdf")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private KBDocument newDocument(String name) {
        KBDocument d = new KBDocument();
        d.setUuid(UUID.randomUUID().toString());
        d.setFileName(name);
        d.setFileSize(1L);
        d.setCreationTimestamp(LocalDateTime.now());
        return d;
    }

    private KBDocument documentWithUuid(String uuid, String name) {
        KBDocument d = newDocument(name);
        d.setUuid(uuid);
        return d;
    }
}
