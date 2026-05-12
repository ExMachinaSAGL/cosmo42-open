package ch.exmachina.cosmo42.services.fs;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Builder
@AllArgsConstructor
public class FileReference {

    String uuid;
    String fileName;
    Long fileSize;

}
