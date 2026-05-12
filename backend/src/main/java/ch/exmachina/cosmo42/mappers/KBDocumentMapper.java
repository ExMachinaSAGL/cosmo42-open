package ch.exmachina.cosmo42.mappers;

import ch.exmachina.cosmo42.dto.DocumentDTO;
import ch.exmachina.cosmo42.entities.KBDocument;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class KBDocumentMapper {

    public DocumentDTO toDocumentDTO(KBDocument kbDocument) {
        return DocumentDTO.builder()
                .uuid(kbDocument.getUuid())
                .name(kbDocument.getFileName())
                .build();
    }

}
