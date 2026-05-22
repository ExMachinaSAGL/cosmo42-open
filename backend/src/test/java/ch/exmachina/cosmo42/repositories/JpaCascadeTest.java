package ch.exmachina.cosmo42.repositories;

import ch.exmachina.cosmo42.AbstractIntegrationTest;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.entities.KBDocumentChunk;
import ch.exmachina.cosmo42.entities.KBDocumentChunkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class JpaCascadeTest extends AbstractIntegrationTest {

    @Autowired
    KBDocumentRepository documentRepo;
    @Autowired
    KBDocumentChunkRepository chunkRepo;

    @BeforeEach
    void cleanState() {
        chunkRepo.deleteAll();
        documentRepo.deleteAll();
    }

    @Test
    void deletingParentDocumentWhileChunksExistViolatesForeignKey() {
        KBDocument doc = newDocument("with-chunks.pdf");
        documentRepo.saveAndFlush(doc);
        chunkRepo.saveAndFlush(newChunk(doc, "chunk-1"));
        
        assertThatThrownBy(() -> {
            documentRepo.deleteByUuid(doc.getUuid());
            documentRepo.flush();
        })
                .as("orphan-chunk creation must be rejected (either by Hibernate or by FK)")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void deletingChunksFirstAllowsParentDocumentRemoval() {
        KBDocument doc = newDocument("delete-me.pdf");
        documentRepo.saveAndFlush(doc);
        chunkRepo.saveAndFlush(newChunk(doc, "chunk-1"));
        chunkRepo.saveAndFlush(newChunk(doc, "chunk-2"));

        chunkRepo.deleteByKbDocument_Uuid(doc.getUuid());
        chunkRepo.flush();
        documentRepo.deleteByUuid(doc.getUuid());
        documentRepo.flush();

        assertThat(documentRepo.findByUuid(doc.getUuid())).isEmpty();
        assertThat(chunkRepo.findAll()).noneMatch(c -> c.getKbDocument().getId().equals(doc.getId()));
    }

    @Test
    void deleteByKbDocumentUuidLeavesUnrelatedChunksUntouched() {
        KBDocument docA = documentRepo.saveAndFlush(newDocument("a.pdf"));
        KBDocument docB = documentRepo.saveAndFlush(newDocument("b.pdf"));
        chunkRepo.saveAndFlush(newChunk(docA, "a-1"));
        chunkRepo.saveAndFlush(newChunk(docA, "a-2"));
        chunkRepo.saveAndFlush(newChunk(docB, "b-1"));

        chunkRepo.deleteByKbDocument_Uuid(docA.getUuid());
        chunkRepo.flush();

        assertThat(chunkRepo.findAll())
                .extracting(KBDocumentChunk::getContent)
                .containsExactly("b-1");
    }

    @Test
    void chunkInheritsParentDocumentReferenceAfterReload() {
        KBDocument doc = documentRepo.saveAndFlush(newDocument("parent.pdf"));
        KBDocumentChunk chunk = chunkRepo.saveAndFlush(newChunk(doc, "child"));

        KBDocumentChunk reloaded = chunkRepo.findById(chunk.getId()).orElseThrow();

        assertThat(reloaded.getKbDocument().getUuid()).isEqualTo(doc.getUuid());
        assertThat(reloaded.getKbDocument().getFileName()).isEqualTo("parent.pdf");
    }

    private KBDocument newDocument(String name) {
        KBDocument d = new KBDocument();
        d.setUuid(UUID.randomUUID().toString());
        d.setFileName(name);
        d.setFileSize(1L);
        d.setCreationTimestamp(LocalDateTime.now());
        return d;
    }

    private KBDocumentChunk newChunk(KBDocument parent, String content) {
        KBDocumentChunk c = new KBDocumentChunk();
        c.setUuid(UUID.randomUUID().toString());
        c.setKbDocument(parent);
        c.setType(KBDocumentChunkType.TEXT);
        c.setContent(content);
        c.setEmbedding(new float[1024]);
        return c;
    }
}
