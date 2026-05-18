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

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class KBDocumentChunker {

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

    public KBDocumentChunker(ChatModel chatModel,
                             OpenAiChatOptions.Builder chunkerModelOptionsBuilder,
                             @Value("${cosmo42.chunking.pool.size:4}") int poolSize) {
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

    public Map<Integer, DocumentPage> processPages(List<byte[]> pageImages, Set<Integer> indicesToProcess) {
        Set<Integer> targetIndices = indicesToProcess == null
                ? new LinkedHashSet<>(IntStream.range(0, pageImages.size()).boxed().toList())
                : indicesToProcess;

        ChatClient chatClient = buildChatClient();
        int totalPages = pageImages.size();

        Map<Integer, Future<DocumentPage>> futures = new LinkedHashMap<>();
        for (Integer pageIndex : targetIndices) {
            if (pageIndex < 0 || pageIndex >= totalPages) continue;
            Media media = new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(pageImages.get(pageIndex)));
            futures.put(pageIndex, executorService.submit(() -> chunkSinglePage(chatClient, media, pageIndex, totalPages)));
        }

        Map<Integer, DocumentPage> results = new LinkedHashMap<>();
        for (Map.Entry<Integer, Future<DocumentPage>> entry : futures.entrySet()) {
            int pageIndex = entry.getKey();
            try {
                results.put(pageIndex, entry.getValue().get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                results.put(pageIndex, null);
            } catch (ExecutionException e) {
                log.error("Failed to get result for page {}", pageIndex + 1, e);
                results.put(pageIndex, null);
            }
        }
        return results;
    }

    private ChatClient buildChatClient() {
        return ChatClient.builder(chatModel)
                .defaultOptions(chunkerModelOptionsBuilder)
                .defaultSystem(CHUNKER_PROMPT)
                .build();
    }

    private DocumentPage chunkSinglePage(ChatClient chatClient, Media media, int pageIndex, int totalPages) {
        try {
            log.info("Chunking page {}/{} of the document.", pageIndex + 1, totalPages);
            DocumentPage extracted = chatClient.prompt()
                    .user(u -> u.text("Extract the chunks for the attached page.").media(media))
                    .call()
                    .entity(DocumentPage.class);
            if (extracted == null) {
                log.error("Null response from LLM for page {}. Skipping.", pageIndex + 1);
            }
            return extracted;
        } catch (Exception e) {
            log.error("Failed to extract chunks for page {}. Skipping.", pageIndex + 1, e);
            return null;
        }
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
