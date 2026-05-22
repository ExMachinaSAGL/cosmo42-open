package ch.exmachina.cosmo42.entities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class KBDocumentChunkTypeTest {

    private static final KBDocumentChunkType FALLBACK = KBDocumentChunkType.TEXT;

    @Test
    void fromLabelReturnsTextForText() {
        assertThat(KBDocumentChunkType.fromLabel("text", FALLBACK)).isEqualTo(KBDocumentChunkType.TEXT);
    }

    @Test
    void fromLabelReturnsTableForTable() {
        assertThat(KBDocumentChunkType.fromLabel("table", FALLBACK)).isEqualTo(KBDocumentChunkType.TABLE);
    }

    @Test
    void fromLabelReturnsImageForImage() {
        assertThat(KBDocumentChunkType.fromLabel("image", FALLBACK)).isEqualTo(KBDocumentChunkType.IMAGE);
    }

    @Test
    void fromLabelIsCaseInsensitive() {
        assertThat(KBDocumentChunkType.fromLabel("TEXT", FALLBACK)).isEqualTo(KBDocumentChunkType.TEXT);
        assertThat(KBDocumentChunkType.fromLabel("Table", FALLBACK)).isEqualTo(KBDocumentChunkType.TABLE);
        assertThat(KBDocumentChunkType.fromLabel("IMAGE", FALLBACK)).isEqualTo(KBDocumentChunkType.IMAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "video", "unknown", "pdf", "  text  "})
    void fromLabelReturnsFallbackForUnknownValues(String label) {
        assertThat(KBDocumentChunkType.fromLabel(label, FALLBACK)).isEqualTo(FALLBACK);
    }

    @ParameterizedTest
    @NullSource
    void fromLabelReturnsFallbackForNull(String label) {
        assertThat(KBDocumentChunkType.fromLabel(label, FALLBACK)).isEqualTo(FALLBACK);
    }
}
