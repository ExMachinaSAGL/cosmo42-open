package ch.exmachina.cosmo42.entities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KBDocumentChunkTypeTest {

    @Test
    void fromLabelReturnsTextForText() {
        assertThat(KBDocumentChunkType.fromLabel("text")).isEqualTo(KBDocumentChunkType.TEXT);
    }

    @Test
    void fromLabelReturnsTableForTable() {
        assertThat(KBDocumentChunkType.fromLabel("table")).isEqualTo(KBDocumentChunkType.TABLE);
    }

    @Test
    void fromLabelReturnsImageForImage() {
        assertThat(KBDocumentChunkType.fromLabel("image")).isEqualTo(KBDocumentChunkType.IMAGE);
    }

    @Test
    void fromLabelIsCaseInsensitive() {
        assertThat(KBDocumentChunkType.fromLabel("TEXT")).isEqualTo(KBDocumentChunkType.TEXT);
        assertThat(KBDocumentChunkType.fromLabel("Table")).isEqualTo(KBDocumentChunkType.TABLE);
        assertThat(KBDocumentChunkType.fromLabel("IMAGE")).isEqualTo(KBDocumentChunkType.IMAGE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"video", "unknown", "pdf", "  text  "})
    void fromLabelThrowsForNullEmptyAndUnknown(String label) {
        assertThatThrownBy(() -> KBDocumentChunkType.fromLabel(label))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown KBDocumentChunkType");
    }
}
