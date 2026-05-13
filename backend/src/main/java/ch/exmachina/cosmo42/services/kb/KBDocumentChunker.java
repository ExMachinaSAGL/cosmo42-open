package ch.exmachina.cosmo42.services.kb;

import ch.exmachina.cosmo42.services.kb.schema.Chunk;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class KBDocumentChunker {

    FileConverter fileConverter;
    ChatModel chatModel;
    OpenAiChatOptions.Builder chunkerModelOptionsBuilder;
    ExecutorService executorService;

    private static final String CHUNKER_PROMPT = """
            You are an expert document analysis and data extraction AI.
            Analyze the provided single document page and extract its content into logical, self-contained chunks.
            
            CRITICAL RULES:
            1. Process the page strictly from top to bottom.
            2. EXCLUSIONS: You MUST ignore document headers, footers, logos, page numbers, and recurring technical marginalia. Do not extract them.
            
            EXTRACTION:
            - Text: Group text by semantic completeness. Prioritize logic over visual whitespace.
              - HEADINGS: NEVER extract a heading or title alone. Always merge it with the paragraph that immediately follows.
              - LISTS: If a sentence introduces a list (especially if it ends with a colon ":"), you MUST extract that introductory sentence AND the entire bulleted or numbered list together as ONE single chunk. Do NOT split the introduction from the list.
            - Cut-offs: If a paragraph is cut off at the very bottom of the page, extract what you see and set 'continuesOnNextPage' to true.
            - Tables: Extract as Markdown, prepend the title, and provide a context summary. Ignore empty trailing rows.
            - Images: Provide a detailed descriptive text summary.
            
            SCHEMA INSTRUCTIONS:
            - The 'summary' field is STRICTLY for 'table' and 'image' chunks. For 'text' chunks, the 'summary' field MUST be null.
            """;

    public KBDocumentChunker(FileConverter fileConverter,
                             ChatModel chatModel,
                             OpenAiChatOptions.Builder chunkerModelOptionsBuilder,
                             @Value("${cosmo42.chunking.pool.size:4}") int poolSize) {
        this.fileConverter = fileConverter;
        this.chatModel = chatModel;
        this.chunkerModelOptionsBuilder = chunkerModelOptionsBuilder;
        log.info("KBDocumentChunker pool size: {}", poolSize);
        this.executorService = Executors.newFixedThreadPool(poolSize);
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public List<DocumentPage> extractRawChunks(MultipartFile file) throws IOException {

        byte[] pdf = fileConverter.convertSupportedFileToPdf(file);
        List<byte[]> images = fileConverter.convertPdfToImages(pdf);
        
        List<Media> mediaList = images.stream()
                .map(imgBytes -> new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(imgBytes)))
                .toList();

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultOptions(chunkerModelOptionsBuilder)
                .defaultSystem(CHUNKER_PROMPT)
                .build();

        List<Future<PageResult>> futures = new ArrayList<>();
        for (int i = 0; i < mediaList.size(); i++) {
            final int pageIndex = i;
            final Media currentPage = mediaList.get(i);
            Future<PageResult> future = executorService.submit(() -> {
                try {
                    log.info("Chunking page {}/{} of the document.", pageIndex + 1, mediaList.size());
                    DocumentPage extractedPage = chatClient.prompt()
                            .user(u -> u
                                    .text("Extract the chunks for the attached page.")
                                    .media(currentPage))
                            .call()
                            .entity(DocumentPage.class);
                    if (extractedPage == null) {
                        log.error("Null response from LLM extracting chunks for page {} of the document. Skipping page.", pageIndex + 1);
                        return new PageResult(pageIndex, null);
                    }
                    return new PageResult(pageIndex, extractedPage);
                } catch (Exception e) {
                    log.error("Failed to extract chunks for page {} of the document. Skipping page.", pageIndex + 1, e);
                    return new PageResult(pageIndex, null);
                }
            });
            futures.add(future);
        }


        List<DocumentPage> allExtractedPages = new ArrayList<>();
        Chunk pendingCutoffChunk = null;
        for (int i = 0; i < futures.size(); i++) {
            try {
                PageResult result = futures.get(i).get();
                if (result.page == null) {
                    continue;
                }
                DocumentPage extractedPage = result.page;
                boolean removeFirst = false;
                for (Chunk newChunk : extractedPage.getChunks()) {
                    if (pendingCutoffChunk != null && pendingCutoffChunk.getType().equals(newChunk.getType())) {
                        String mergedContent = pendingCutoffChunk.getContent() + " " + newChunk.getContent();
                        String summary = pendingCutoffChunk.getSummary();
                        if (summary != null) {
                            if (newChunk.getSummary() != null) {
                                summary += " " + newChunk.getSummary();
                            }
                        } else {
                            summary = newChunk.getSummary();
                        }
                        pendingCutoffChunk.setContent(mergedContent);
                        pendingCutoffChunk.setSummary(summary);
                        pendingCutoffChunk.setContinuesOnNextPage(newChunk.getContinuesOnNextPage());
                        removeFirst = true;
                        if (!pendingCutoffChunk.getContinuesOnNextPage()) {
                            pendingCutoffChunk = null;
                        }
                    } else {
                        if (newChunk.getContinuesOnNextPage()) {
                            pendingCutoffChunk = newChunk;
                        }
                    }
                }
                if (removeFirst) {
                    extractedPage.getChunks().removeFirst();
                }
                allExtractedPages.add(extractedPage);
            } catch (InterruptedException | ExecutionException e) {
                log.error("Failed to get result for page {}", i + 1, e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        log.info("Chunking done.");
        return allExtractedPages;
    }

    private static class PageResult {
        final int pageIndex;
        final DocumentPage page;

        PageResult(int pageIndex, DocumentPage page) {
            this.pageIndex = pageIndex;
            this.page = page;
        }
    }
}
