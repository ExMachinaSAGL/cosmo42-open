package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.dto.DocumentDTO;
import ch.exmachina.cosmo42.dto.DownloadDocumentDTO;
import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.exceptions.FileSaveException;
import ch.exmachina.cosmo42.exceptions.KBDocumentNotFoundException;
import ch.exmachina.cosmo42.mappers.KBDocumentMapper;
import ch.exmachina.cosmo42.repositories.IngestionJobRepository;
import ch.exmachina.cosmo42.repositories.KBDocumentChunkRepository;
import ch.exmachina.cosmo42.repositories.KBDocumentRepository;
import ch.exmachina.cosmo42.services.fs.FileReference;
import ch.exmachina.cosmo42.services.fs.FileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class KBDocumentService {

    KBDocumentRepository kbDocumentRepository;
    KBDocumentChunkRepository kbDocumentChunkRepository;
    IngestionJobRepository ingestionJobRepository;
    FileService fileService;
    KBDocumentMapper kbDocumentMapper;
    KBDocumentChunker kbDocumentChunker;
    EmbeddingModel embeddingModel;
    OpenAiEmbeddingOptions embeddingModelOptions;
    IngestionJobService ingestionJobService;
    KBDocumentIngestionProcessor ingestionProcessor;
    Clock clock;

    @Transactional(readOnly = true)
    public List<DocumentDTO> listAllKBDocuments() {
        return ingestionJobRepository.findAll().stream()
                .map(kbDocumentMapper::toDocumentDTO)
                .toList();
    }

    public DocumentDTO enqueueKBDocument(MultipartFile file) {
        try {
            FileReference ref = fileService.save(file);
            IngestionJob job = ingestionJobService.createJob(
                    file.getOriginalFilename(), file.getSize(), ref.getUuid());
            ingestionProcessor.processAsync(job.getUuid());
            return kbDocumentMapper.toDocumentDTO(job);
        } catch (IOException e) {
            log.error("Error saving file for ingestion", e);
            throw new FileSaveException();
        }
    }

    @Transactional(readOnly = true)
    public Optional<DocumentDTO> getDocument(String storedFileUuid) {
        return ingestionJobRepository.findByStoredFileUuid(storedFileUuid)
                .map(kbDocumentMapper::toDocumentDTO);
    }

    @Transactional(readOnly = true)
    public DownloadDocumentDTO downloadKBDocument(String uuid) {
        KBDocument kbDocument = kbDocumentRepository.findByUuid(uuid)
                .orElseThrow(() -> new KBDocumentNotFoundException(uuid));
        try {
            DownloadDocumentDTO dto = kbDocumentMapper.toDownloadDocumentDTO(kbDocument);
            dto.setContent(fileService.load(kbDocument.getUuid()));
            return dto;
        } catch (IOException e) {
            log.error("Error downloading the KB Document with UUID: {}", uuid, e);
            throw new RuntimeException("Error downloading file", e);
        }
    }

    @Transactional
    public void deleteKBDocument(String uuid) {
        try {
            ingestionJobRepository.deleteByKbDocumentUuid(uuid);
            kbDocumentChunkRepository.deleteByKbDocument_Uuid(uuid);
            kbDocumentRepository.deleteByUuid(uuid);
            fileService.delete(uuid);
        } catch (IOException e) {
            log.error("Error deleting the KB Document file with UUID: {}", uuid, e);
            throw new RuntimeException("Error deleting file", e);
        }
    }

}
