package ch.exmachina.cosmo42.services.kb;

import ch.exmachina.cosmo42.services.kb.schema.Chunk;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.math3.util.Pair;
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
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import static java.util.Comparator.comparing;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static org.springframework.ai.chat.client.AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT;

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
            - The summary MUST ONLY be provided for 'table' chunks, NEVER for other type of chunks.
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

        CompletionService<Pair<Integer, DocumentPage>> completionService =
                new ExecutorCompletionService<>(executorService);
        int submitted = 0;
        for (Integer pageIndex : targetIndices) {
            if (pageIndex < 0 || pageIndex >= totalPages) continue;
            Media media = new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(pageImages.get(pageIndex)));
            completionService.submit(() -> Pair.create(pageIndex, chunkSinglePage(chatClient, media, pageIndex, totalPages)));
            submitted++;
        }

        for (int i = 0; i < submitted; i++) {
            int pageIndex = -1;
            DocumentPage page = null;
            try {
                Future<Pair<Integer, DocumentPage>> future =
                        completionService.poll(pageChunkingTimeoutSeconds, TimeUnit.SECONDS);
                if (future == null) {
                    log.error("Global timeout waiting for a page result after {}s", pageChunkingTimeoutSeconds);
                    break;
                }
                Pair<Integer, DocumentPage> result = future.get();
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

    private ChatClient buildChatClient() {
        return ChatClient.builder(chatModel)
                .defaultOptions(chunkerModelOptionsBuilder)
                .defaultSystem(CHUNKER_PROMPT)
                .build();
    }

    private DocumentPage chunkSinglePage(ChatClient chatClient, Media media, int pageIndex, int totalPages) {
        log.info("Chunking page {}/{} of the document.", pageIndex + 1, totalPages);
        DocumentPage page = null;
    	try {
	    	page = chatClient.prompt()
	    		.user(u -> u.text("Extract the chunks for the attached page.").media(media))
	    		.advisors(ENABLE_NATIVE_STRUCTURED_OUTPUT)
	    		.call()
	    		.entity(DocumentPage.class);
    	} catch (RuntimeException e) {
            log.error("Empty response from LLM for page {}. Skipping.", pageIndex + 1, e);
    	}
    	return page;
    }

	public List<DocumentPage> mergePages(List<Map.Entry<Integer, DocumentPage>> orderedPages) {
		orderedPages = orderedPages.stream()
				.filter(this::valid)
				.sorted(comparing(Map.Entry::getKey))
				.toList();

		List<DocumentPage> merged = new ArrayList<>();

		for (int i = 0; i < orderedPages.size(); i++) {
			var page = orderedPages.get(i);
			var source = page.getValue();
			List<Chunk> chunks = source.getChunks();
			if (chunks.isEmpty()) {
				merged.add(page.getValue());
				continue;
			}
			List<Chunk> outChunks = chunks.stream().limit(chunks.size() - 1).collect(toList());
			var lastChunk = chunks.getLast();
			if (lastChunk.getContinuesOnNextPage()) {
				lastChunk = joinConitnuingChunks(lastChunk, i, orderedPages);
			}
			outChunks.add(lastChunk);
			merged.add(new DocumentPage(outChunks));
		}
		return merged;
	}
	
	private boolean valid(Map.Entry<Integer, DocumentPage> page) {
		return nonNull(page) && nonNull(page.getValue()) && nonNull(page.getValue().getChunks());
	}

	private Chunk joinConitnuingChunks(Chunk start, int from, List<Map.Entry<Integer, DocumentPage>> orderedPages) {
		for (; start.getContinuesOnNextPage() && from < orderedPages.size() - 1;) {
			var page = orderedPages.get(from);
			var nextPage = orderedPages.get(++from);
			if (nextPage.getKey() == page.getKey() + 1) {
				var chunks = nextPage.getValue().getChunks();
				var continuationChunk = chunks.stream()
						.filter(chunk -> Objects.equals(start.getType(), chunk.getType()))
						.findFirst();
				if (continuationChunk.isPresent()) {
					var continuation = continuationChunk.get();
					start.setContent(joinTexts(start.getContent(), continuation.getContent()));
					start.setSummary(joinTexts(start.getSummary(), continuation.getSummary()));
					start.setContinuesOnNextPage(continuation.getContinuesOnNextPage());
					nextPage.getValue().setChunks(chunks.stream().filter(c -> c != continuation).toList());
				} else {
					// The model may have hallucinated
					start.setContinuesOnNextPage(false);
					return start;
				}
			} else {
				break;
			}
		}
		return start;
	}

	private String joinTexts(String left, String right) {
        if (left == null) return right;
        if (right == null) return left;
        return left + " " + right;
	}
}
