package ch.exmachina.cosmo42.entities;

import ch.exmachina.cosmo42.entities.converters.VectorAttributeConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "kb_document_chunk")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
public class KBDocumentChunk extends BaseEntity {

    @Column(nullable = false, unique = true)
    String uuid;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_kb_document_id")
    KBDocument kbDocument;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    KBDocumentChunkType type;

    @Column(name = "content", nullable = false)
    String content;

    @Column(name = "summary")
    String summary;

    @Column(name = "embedding", nullable = false, columnDefinition = "vector(1024)")
    @Convert(converter = VectorAttributeConverter.class)
    float[] embedding;
}
