package ch.exmachina.cosmo42.entities;

import java.util.Arrays;

public enum KBDocumentChunkType {
    TEXT("text"),
    TABLE("table"),
    IMAGE("image");

    private final String label;

    KBDocumentChunkType(String label){
        this.label = label;
    }

    public static KBDocumentChunkType fromLabel(String label, KBDocumentChunkType fallbackValue){
        return Arrays.stream(KBDocumentChunkType.values())
                .filter(t -> t.label.equalsIgnoreCase(label))
                .findFirst()
                .orElse(fallbackValue);
    }
}
