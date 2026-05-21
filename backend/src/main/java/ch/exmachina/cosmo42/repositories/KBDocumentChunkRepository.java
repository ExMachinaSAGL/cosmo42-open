package ch.exmachina.cosmo42.repositories;

import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.entities.KBDocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KBDocumentChunkRepository extends JpaRepository<KBDocumentChunk, Long> {
    void deleteByKbDocument_Uuid(String uuid);

    long countByKbDocument(KBDocument kbDocument);

    @Query(value = """
    SELECT c.*
    FROM (
        SELECT c.*, VEC_DISTANCE_COSINE(c.embedding, :queryVector) AS distance
        FROM kb_document_chunk c
    ) c
    WHERE (:maxDistance IS NULL OR c.distance <= :maxDistance)
    ORDER BY c.distance ASC
    LIMIT :limit
    """, nativeQuery = true)
    List<KBDocumentChunk> findMostSimilarByCosine(
            @Param("queryVector") byte[] queryVector,
            @Param("maxDistance") Double maxDistance,
            @Param("limit") int limit
    );
}
