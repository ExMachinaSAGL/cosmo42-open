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
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class KBDocumentChunker {

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
    ChatModel chatModel;
    OpenAiChatOptions.Builder chunkerModelOptionsBuilder;
    ExecutorService executorService;
    int pageChunkingTimeoutSeconds;

    public KBDocumentChunker(ChatModel chatModel,
                             OpenAiChatOptions.Builder chunkerModelOptionsBuilder,
                             @Value("${cosmo42.chunking.pool.size:4}") int poolSize,
                             @Value("${cosmo42.ingestion.page-chunking-timeout-seconds:600}") int pageChunkingTimeoutSeconds) {
        this.chatModel = chatModel;
        this.chunkerModelOptionsBuilder = chunkerModelOptionsBuilder;
        this.pageChunkingTimeoutSeconds = pageChunkingTimeoutSeconds;
        log.info("KBDocumentChunker pool size: {}, page timeout: {}s", poolSize, pageChunkingTimeoutSeconds);
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

    public void processPages(List<byte[]> pageImages, Set<Integer> indicesToProcess,
                             BiConsumer<Integer, DocumentPage> onPageComplete) {
        Set<Integer> targetIndices = indicesToProcess == null
                ? new LinkedHashSet<>(IntStream.range(0, pageImages.size()).boxed().toList())
                : indicesToProcess;

        ChatClient chatClient = buildChatClient();
        int totalPages = pageImages.size();

        CompletionService<Map.Entry<Integer, DocumentPage>> completionService =
                new ExecutorCompletionService<>(executorService);
        int submitted = 0;
        for (Integer pageIndex : targetIndices) {
            if (pageIndex < 0 || pageIndex >= totalPages) continue;
            Media media = new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(pageImages.get(pageIndex)));
            completionService.submit(() -> Map.entry(pageIndex, chunkSinglePageNullSafe(chatClient, media, pageIndex, totalPages)));
            submitted++;
        }

        for (int i = 0; i < submitted; i++) {
            int pageIndex = -1;
            DocumentPage page = null;
            try {
                Future<Map.Entry<Integer, DocumentPage>> future =
                        completionService.poll(pageChunkingTimeoutSeconds, TimeUnit.SECONDS);
                if (future == null) {
                    log.error("Global timeout waiting for a page result after {}s", pageChunkingTimeoutSeconds);
                    break;
                }
                Map.Entry<Integer, DocumentPage> result = future.get();
                pageIndex = result.getKey();
                page = result.getValue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                log.error("Unexpected execution error collecting page result", e);
            }
            if (pageIndex >= 0) {
                onPageComplete.accept(pageIndex, page);
            }
        }
    }

    private DocumentPage chunkSinglePageNullSafe(ChatClient chatClient, Media media, int pageIndex, int totalPages) {
        try {
            return chunkSinglePage(chatClient, media, pageIndex, totalPages);
        } catch (Exception e) {
            log.error("Failed to extract chunks for page {}. Skipping.", pageIndex + 1, e);
            return null;
        }
    }

    private ChatClient buildChatClient() {
        return ChatClient.builder(chatModel)
                .defaultOptions(chunkerModelOptionsBuilder)
                .defaultSystem(CHUNKER_PROMPT)
                .build();
    }

    private DocumentPage chunkSinglePage(ChatClient chatClient, Media media, int pageIndex, int totalPages) {
        log.info("Chunking page {}/{} of the document.", pageIndex + 1, totalPages);
        BeanOutputConverter<DocumentPage> converter = new BeanOutputConverter<>(DocumentPage.class);
        String fullContent = chatClient.prompt()
                .user(u -> u.text("Extract the chunks for the attached page.\n" + converter.getFormat()).media(media))
                .stream()
                .content()
                .collectList()
                .map(chunks -> String.join("", chunks))
                .block();
        if (fullContent == null || fullContent.isBlank()) {
            log.error("Empty response from LLM for page {}. Skipping.", pageIndex + 1);
            return null;
        }
        return converter.convert(fullContent);
    }

    public List<DocumentPage> mergePages(List<DocumentPage> orderedPages) {
        List<DocumentPage> merged = new ArrayList<>();
        Chunk pendingCutoff = null;

        for (DocumentPage source : orderedPages) {
            if (source == null || source.getChunks() == null) continue;

            List<Chunk> outChunks = new ArrayList<>(source.getChunks().size());
            for (Chunk srcChunk : source.getChunks()) {
                Chunk copy = copyChunk(srcChunk);
                if (pendingCutoff != null && Objects.equals(pendingCutoff.getType(), copy.getType())) {
                    pendingCutoff.setContent(pendingCutoff.getContent() + " " + copy.getContent());
                    pendingCutoff.setSummary(joinSummaries(pendingCutoff.getSummary(), copy.getSummary()));
                    pendingCutoff.setContinuesOnNextPage(copy.getContinuesOnNextPage());
                    if (!Boolean.TRUE.equals(pendingCutoff.getContinuesOnNextPage())) {
                        pendingCutoff = null;
                    }
                } else {
                    outChunks.add(copy);
                    if (Boolean.TRUE.equals(copy.getContinuesOnNextPage())) {
                        pendingCutoff = copy;
                    }
                }
            }
            merged.add(new DocumentPage(outChunks));
        }
        return merged;
    }

    private Chunk copyChunk(Chunk c) {
        return new Chunk(c.getType(), c.getContent(), c.getSummary(), c.getContinuesOnNextPage());
    }

    private String joinSummaries(String left, String right) {
        if (left == null) return right;
        if (right == null) return left;
        return left + " " + right;
    }
}
