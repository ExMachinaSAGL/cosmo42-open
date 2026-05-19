package ch.exmachina.cosmo42.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DownloadDocumentDTO {

    String fileUuid;
    String fileName;
    LocalDateTime uploadedAt;
    byte[] content;

}
