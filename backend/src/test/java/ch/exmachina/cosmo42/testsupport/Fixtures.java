package ch.exmachina.cosmo42.testsupport;

import ch.exmachina.cosmo42.entities.ChatConversation;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.entities.KBDocumentChunk;
import ch.exmachina.cosmo42.entities.KBDocumentChunkType;

import java.time.LocalDateTime;
import java.util.UUID;

public final class Fixtures {

    public static final LocalDateTime FIXED_NOW = LocalDateTime.parse("2026-05-15T12:00:00");

    private Fixtures() {}

    public static ChatConversation conversation(String uuid, String title, LocalDateTime at) {
        ChatConversation c = new ChatConversation();
        c.setUuid(uuid);
        c.setTitle(title);
        c.setCreatedAt(at);
        c.setUpdatedAt(at);
        return c;
    }

    public static ChatConversation conversation(String title) {
        return conversation(UUID.randomUUID().toString(), title, FIXED_NOW);
    }

    public static KBDocument document(String uuid, String fileName) {
        KBDocument d = new KBDocument();
        d.setUuid(uuid);
        d.setFileName(fileName);
        d.setFileSize(0L);
        d.setCreationTimestamp(FIXED_NOW);
        return d;
    }

    public static KBDocument document(String fileName) {
        return document(UUID.randomUUID().toString(), fileName);
    }

    public static KBDocumentChunk chunk(KBDocument doc,
                                        KBDocumentChunkType type,
                                        String content,
                                        float[] embedding) {
        KBDocumentChunk c = new KBDocumentChunk();
        c.setUuid(UUID.randomUUID().toString());
        c.setKbDocument(doc);
        c.setType(type);
        c.setContent(content);
        c.setEmbedding(embedding);
        return c;
    }

    public static float[] unitVector(int dim, int axis) {
        if (axis < 0 || axis >= dim) {
            throw new IllegalArgumentException("axis out of range: " + axis);
        }
        float[] v = new float[dim];
        v[axis] = 1.0f;
        return v;
    }

    public static float[] zeroVector(int dim) {
        return new float[dim];
    }
}
