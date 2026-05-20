package ch.exmachina.cosmo42.repositories;

import ch.exmachina.cosmo42.AbstractIntegrationTest;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.entities.KBDocumentChunk;
import ch.exmachina.cosmo42.entities.KBDocumentChunkType;
import ch.exmachina.cosmo42.entities.converters.VectorAttributeConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class KBDocumentChunkRepositoryTest extends AbstractIntegrationTest {

    private static final int DIM = 1024;
    private static final float INV_SQRT_2 = (float) (1.0 / Math.sqrt(2.0));

    private final VectorAttributeConverter converter = new VectorAttributeConverter();

    @Autowired KBDocumentChunkRepository chunkRepository;
    @Autowired KBDocumentRepository documentRepository;

    private KBDocument document;
    private KBDocumentChunk identical;
    private KBDocumentChunk near;
    private KBDocumentChunk orthogonal;
    private KBDocumentChunk opposite;

    @BeforeEach
    void cleanAndSeed() {
        chunkRepository.deleteAll();
        documentRepository.deleteAll();

        document = new KBDocument();
        document.setUuid(UUID.randomUUID().toString());
        document.setFileName("seed.pdf");
        document.setFileSize(1L);
        document.setCreationTimestamp(LocalDateTime.now());
        documentRepository.saveAndFlush(document);

        identical = persistChunk("identical", basis(0));
        near = persistChunk("near", normalized(basis(0), basis(1)));
        orthogonal = persistChunk("orthogonal", basis(1));
        opposite = persistChunk("opposite", normalized(negate(basis(0)), basis(1)));
    }

    @Test
    void returnsEmptyWhenNoChunksMatchDistanceFilter() {
        chunkRepository.deleteAll();

        List<KBDocumentChunk> results = chunkRepository.findMostSimilarByCosine(
                converter.convertToDatabaseColumn(basis(0)), 0.5, 10);

        assertThat(results).isEmpty();
    }

    @Test
    void ordersByAscendingCosineDistance() {
        byte[] query = converter.convertToDatabaseColumn(basis(0));

        List<KBDocumentChunk> results = chunkRepository.findMostSimilarByCosine(query, null, 10);

        assertThat(results)
                .extracting(KBDocumentChunk::getContent)
                .containsExactly("identical", "near", "orthogonal", "opposite");
    }

    @Test
    void respectsLimit() {
        byte[] query = converter.convertToDatabaseColumn(basis(0));

        List<KBDocumentChunk> results = chunkRepository.findMostSimilarByCosine(query, null, 2);

        assertThat(results)
                .extracting(KBDocumentChunk::getContent)
                .containsExactly("identical", "near");
    }

    @Test
    void filtersByMaxDistanceThreshold() {
        // Expected cosine distances against basis(0):
        //   identical    = 0.0
        //   near         ≈ 1 - 1/√2 ≈ 0.293
        //   orthogonal   = 1.0
        //   opposite     ≈ 1 + 1/√2 ≈ 1.707
        // maxDistance=0.5 must keep identical + near and drop the other two.
        byte[] query = converter.convertToDatabaseColumn(basis(0));

        List<KBDocumentChunk> results = chunkRepository.findMostSimilarByCosine(query, 0.5, 10);

        assertThat(results)
                .extracting(KBDocumentChunk::getContent)
                .containsExactly("identical", "near");
    }

    @Test
    void nullMaxDistanceReturnsAllOrdered() {
        byte[] query = converter.convertToDatabaseColumn(basis(0));

        List<KBDocumentChunk> results = chunkRepository.findMostSimilarByCosine(query, null, 10);

        assertThat(results).hasSize(4);
    }

    @Test
    void deleteByKbDocumentUuidRemovesAllChunks() {
        chunkRepository.deleteByKbDocument_Uuid(document.getUuid());

        assertThat(chunkRepository.findAll()).isEmpty();
    }

    private KBDocumentChunk persistChunk(String content, float[] embedding) {
        KBDocumentChunk c = new KBDocumentChunk();
        c.setUuid(UUID.randomUUID().toString());
        c.setKbDocument(document);
        c.setType(KBDocumentChunkType.TEXT);
        c.setContent(content);
        c.setEmbedding(embedding);
        return chunkRepository.saveAndFlush(c);
    }

    private static float[] basis(int axis) {
        float[] v = new float[DIM];
        v[axis] = 1.0f;
        return v;
    }

    private static float[] negate(float[] v) {
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = -v[i];
        }
        return out;
    }

    private static float[] normalized(float[] a, float[] b) {
        float[] out = new float[DIM];
        for (int i = 0; i < DIM; i++) {
            out[i] = (a[i] + b[i]) * INV_SQRT_2;
        }
        return out;
    }
}
