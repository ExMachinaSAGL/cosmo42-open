package ch.exmachina.cosmo42.services.fs;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileService {

    FileReference save(MultipartFile file) throws IOException;
    byte[] load(String uuid) throws IOException;
    void delete(String uuid) throws IOException;

}
