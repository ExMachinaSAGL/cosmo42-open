package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.entities.KBDocumentChunk;
import ch.exmachina.cosmo42.entities.KBDocumentChunkType;
import ch.exmachina.cosmo42.repositories.KBDocumentChunkRepository;
import ch.exmachina.cosmo42.repositories.KBDocumentRepository;
import ch.exmachina.cosmo42.services.fs.FileService;
import ch.exmachina.cosmo42.services.kb.KBDocumentChunker;
import ch.exmachina.cosmo42.services.kb.schema.Chunk;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class KBDocumentIngestionProcessor {

    IngestionJobService ingestionJobService;
    KBDocumentChunker kbDocumentChunker;
    FileService fileService;
    KBDocumentRepository kbDocumentRepository;
    KBDocumentChunkRepository kbDocumentChunkRepository;
    EmbeddingModel embeddingModel;
    OpenAiEmbeddingOptions embeddingModelOptions;

    @Async("ingestionExecutor")
    public void processAsync(String jobUuid) {
        processInternal(jobUuid);
    }

    public void processInternal(String jobUuid) {
        IngestionJob job = ingestionJobService.findByUuid(jobUuid).orElseThrow();
        ingestionJobService.markProcessing(jobUuid);
        try {
            runPipeline(job);
        } catch (Exception e) {
            log.error("Ingestion job {} failed", jobUuid, e);
            ingestionJobService.markFailed(jobUuid, e.getMessage());
        }
    }

    private void runPipeline(IngestionJob job) throws Exception {
        byte[] rawBytes = fileService.load(job.getStoredFileUuid());
        byte[] pdf = kbDocumentChunker.convertToPdfFromBytes(rawBytes, job.getOriginalFileName());
        List<byte[]> pageImages = kbDocumentChunker.convertToPageImages(pdf);

        if (job.getTotalPages() == null) {
            ingestionJobService.setTotalPages(job.getUuid(), pageImages.size());
            job = refresh(job.getUuid());
        }

        Set<Integer> donePages = ingestionJobService.getDonePageIndices(job);
        Map<Integer, DocumentPage> newResults = kbDocumentChunker.processPages(pageImages, donePages);

        final IngestionJob jobForLambda = job;
        newResults.forEach((pageIndex, page) ->
                ingestionJobService.savePageResult(jobForLambda, pageIndex, page));

        long failedPages = newResults.values().stream().filter(Objects::isNull).count();
        if (failedPages > 0) {
            log.warn("Job {} has {} failed page(s) due to LLM errors. Marking INTERRUPTED for retry on next startup.", job.getUuid(), failedPages);
            ingestionJobService.markInterrupted(job.getUuid());
            return;
        }

        job = refresh(job.getUuid());

        List<DocumentPage> completedPages = ingestionJobService.loadCompletedPages(job);
        List<DocumentPage> mergedPages = kbDocumentChunker.mergePages(completedPages);

        if (job.getKbDocumentUuid() == null) {
            KBDocument doc = new KBDocument();
            doc.setUuid(job.getStoredFileUuid());
            doc.setFileName(job.getOriginalFileName());
            doc.setFileSize(job.getFileSizeBytes());
            doc.setCreationTimestamp(LocalDateTime.now());
            kbDocumentRepository.save(doc);
            ingestionJobService.setKbDocumentUuid(job.getUuid(), doc.getUuid());
            job = refresh(job.getUuid());
        }

        KBDocument kbDocument = kbDocumentRepository.findByUuid(job.getKbDocumentUuid()).orElseThrow();
        kbDocumentChunkRepository.deleteByKbDocument_Uuid(kbDocument.getUuid());
        embedAndSaveChunks(kbDocument, mergedPages);

        ingestionJobService.markCompleted(job.getUuid());
        log.info("Ingestion job {} completed successfully.", job.getUuid());
    }

    private void embedAndSaveChunks(KBDocument kbDocument, List<DocumentPage> pages) {
        List<KBDocumentChunk> chunks = new ArrayList<>();
        List<String> toEmbed = new ArrayList<>();

        for (DocumentPage page : pages) {
            for (Chunk chunk : page.getChunks()) {
                KBDocumentChunk kbChunk = new KBDocumentChunk();
                kbChunk.setUuid(UUID.randomUUID().toString());
                kbChunk.setKbDocument(kbDocument);
                kbChunk.setType(KBDocumentChunkType.fromLabel(chunk.getType()));
                kbChunk.setContent(chunk.getContent());
                kbChunk.setSummary(chunk.getSummary());
                chunks.add(kbChunk);
                toEmbed.add(kbChunk.getType() == KBDocumentChunkType.TABLE
                        ? kbChunk.getSummary() : kbChunk.getContent());
            }
        }

        if (chunks.isEmpty()) return;

        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(toEmbed, embeddingModelOptions));
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setEmbedding(response.getResults().get(i).getOutput());
        }
        kbDocumentChunkRepository.saveAll(chunks);
        log.info("Saved {} chunks for document {}.", chunks.size(), kbDocument.getUuid());
    }

    private IngestionJob refresh(String jobUuid) {
        return ingestionJobService.findByUuid(jobUuid).orElseThrow();
    }
}
