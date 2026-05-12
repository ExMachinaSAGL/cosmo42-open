package ch.exmachina.cosmo42.services.fs;

import ch.exmachina.cosmo42.exceptions.KBDocumentNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "cosmo42.fs.service.implementation", havingValue = "local")
public class LocalFileService implements FileService {

    @Value("${cosmo42.fs.storage.path}")
    private String storagePath;

    @Override
    public FileReference save(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(storagePath);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        FileReference fileReference = FileReference.builder()
                .uuid(UUID.randomUUID().toString())
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .build();

        Path filePath = uploadPath.resolve(fileReference.getUuid());
        file.transferTo(filePath);

        return fileReference;
    }

    @Override
    public byte[] load(String uuid) throws IOException {
        Path filePath = Paths.get(storagePath).resolve(uuid);
        if (!Files.exists(filePath)) {
            throw new KBDocumentNotFoundException(uuid);
        }
        return Files.readAllBytes(filePath);
    }

    @Override
    public void delete(String uuid) throws IOException {
        Path filePath = Paths.get(storagePath).resolve(uuid);
        if (!Files.exists(filePath)) {
            throw new KBDocumentNotFoundException(uuid);
        }
        Files.delete(filePath);
    }
}
