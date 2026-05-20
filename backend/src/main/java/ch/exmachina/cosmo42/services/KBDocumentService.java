package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.dto.DocumentDTO;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.entities.KBDocumentChunk;
import ch.exmachina.cosmo42.entities.KBDocumentChunkType;
import ch.exmachina.cosmo42.exceptions.FileSaveException;
import ch.exmachina.cosmo42.exceptions.KBDocumentNotFoundException;
import ch.exmachina.cosmo42.mappers.KBDocumentMapper;
import ch.exmachina.cosmo42.repositories.KBDocumentChunkRepository;
import ch.exmachina.cosmo42.repositories.KBDocumentRepository;
import ch.exmachina.cosmo42.services.fs.FileReference;
import ch.exmachina.cosmo42.services.fs.FileService;
import ch.exmachina.cosmo42.services.kb.KBDocumentChunker;
import ch.exmachina.cosmo42.services.kb.schema.Chunk;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class KBDocumentService {

    KBDocumentRepository kbDocumentRepository;
    KBDocumentChunkRepository kbDocumentChunkRepository;
    FileService fileService;
    KBDocumentMapper kbDocumentMapper;
    KBDocumentChunker kbDocumentChunker;
    EmbeddingModel embeddingModel;
    OpenAiEmbeddingOptions embeddingModelOptions;
    Clock clock;

    @Transactional(readOnly = true)
    public List<DocumentDTO> listAllKBDocuments(){
        return kbDocumentRepository.findAll().stream()
                .map(kbDocumentMapper::toDocumentDTO)
                .toList();
    }

    @Transactional
    public DocumentDTO saveKBDocument(MultipartFile file) {
        List<DocumentPage> chunks;
        try {
            chunks = kbDocumentChunker.extractRawChunks(file);
        } catch (IOException e) {
            log.error("Error extracting chunks from the KB Document", e);
            throw new FileSaveException();
        }

        FileReference fileReference;
        try {
            fileReference = fileService.save(file);
        } catch (IOException e) {
            log.error("Error saving the KB Document file", e);
            throw new FileSaveException();
        }

        try {
            KBDocument kbDocument = new KBDocument();
            kbDocument.setUuid(fileReference.getUuid());
            kbDocument.setFileName(fileReference.getFileName());
            kbDocument.setFileSize(fileReference.getFileSize());
            kbDocument.setCreationTimestamp(LocalDateTime.now(clock));
            kbDocumentRepository.save(kbDocument);

            List<KBDocumentChunk> persistedChunks = new ArrayList<>();
            List<String> toEmbed = new ArrayList<>();
            for (DocumentPage pageChunks : chunks) {
                for (Chunk chunk : pageChunks.getChunks()) {
                    KBDocumentChunk kbChunk = new KBDocumentChunk();
                    kbChunk.setUuid(UUID.randomUUID().toString());
                    kbChunk.setKbDocument(kbDocument);
                    kbChunk.setType(KBDocumentChunkType.fromLabel(chunk.getType()));
                    kbChunk.setContent(chunk.getContent());
                    kbChunk.setSummary(chunk.getSummary());
                    persistedChunks.add(kbChunk);
                    toEmbed.add(kbChunk.getType() == KBDocumentChunkType.TABLE ?
                            kbChunk.getSummary() : kbChunk.getContent());
                }
            }
            EmbeddingResponse embeddingResponse = embeddingModel.call(
                    new EmbeddingRequest(toEmbed, embeddingModelOptions));
            for (int i = 0; i < persistedChunks.size(); i++) {
                float[] vector = embeddingResponse.getResults().get(i).getOutput();
                persistedChunks.get(i).setEmbedding(vector);
            }
            kbDocumentChunkRepository.saveAll(persistedChunks);

            return kbDocumentMapper.toDocumentDTO(kbDocument);
        } catch (RuntimeException e) {
            deleteOrphanFileQuietly(fileReference.getUuid());
            throw e;
        }
    }

    private void deleteOrphanFileQuietly(String uuid) {
        try {
            fileService.delete(uuid);
        } catch (IOException ioe) {
            log.error("Failed to delete orphan file {} during rollback", uuid, ioe);
        }
    }

    @Transactional(readOnly = true)
    public DocumentDTO loadKBDocument(String uuid) {
        KBDocument kbDocument = kbDocumentRepository.findByUuid(uuid)
                .orElseThrow(() -> new KBDocumentNotFoundException(uuid));
        try {
            DocumentDTO dto = kbDocumentMapper.toDocumentDTO(kbDocument);
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
            kbDocumentChunkRepository.deleteByKbDocument_Uuid(uuid);
            kbDocumentRepository.deleteByUuid(uuid);
            fileService.delete(uuid);
        } catch (IOException e) {
            log.error("Error deleting the KB Document file with UUID: {}", uuid, e);
            throw new RuntimeException("Error deleting file", e);
        }
    }

}
