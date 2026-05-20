package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.utils.SupportedMimeTypes;
import org.springframework.web.multipart.MultipartFile;

public interface MimeTypeService {

    boolean isSupportedMimeType(MultipartFile file);

    boolean isMimeType(MultipartFile file, SupportedMimeTypes mimeType);
}
