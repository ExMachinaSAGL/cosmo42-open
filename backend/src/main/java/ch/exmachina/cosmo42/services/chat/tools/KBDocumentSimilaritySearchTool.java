package ch.exmachina.cosmo42.services.chat.tools;

import static ch.exmachina.cosmo42.services.chat.ChatAttribute.CITATIONS;
import static java.util.stream.IntStream.range;
import static java.util.stream.IntStream.rangeClosed;

import java.util.List;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.exmachina.cosmo42.entities.converters.VectorAttributeConverter;
import ch.exmachina.cosmo42.repositories.KBDocumentChunkRepository;
import ch.exmachina.cosmo42.services.kb.CitationCollector;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class KBDocumentSimilaritySearchTool extends BaseTool {

    EmbeddingModel embeddingModel;
    OpenAiEmbeddingOptions embeddingModelOptions;
    VectorAttributeConverter vectorAttributeConverter;
    KBDocumentChunkRepository kbDocumentChunkRepository;

    @Tool(description = """
            Executes a semantic similarity search against the private Knowledge Base.
            Call this tool to find specific information, data, guidelines, or concepts contained within the various files and documents.
            CRITICAL: Do not just pass the user's raw message. Formulate a specific, descriptive search query
            that captures the core concepts and semantic intent of what you need to know to answer the user's question
            (e.g., 'Cosmo42 system architecture specifications' or 'Q3 financial report summary' instead of 'what does the file say?' or 'tell me about the project').
            """)
    @Transactional(readOnly = true)
    public KBSimilaritySearchResponse search(
    		@ToolParam(
    				description = "A specific, context-rich search query written in natural language, designed to retrieve the most semantically relevant documents.",
    				required = true
    		) String query, 
    		ToolContext context) {
        emitStatus(context, "Searching Knowledge Base...");

        EmbeddingResponse embeddingResponse = embeddingModel.call(
                new EmbeddingRequest(List.of(query), embeddingModelOptions));
        List<Embedding> results = embeddingResponse.getResults();
        if (results.isEmpty()) {
            return new KBSimilaritySearchResponse(List.of());
        }
        float[] queryVector = results.getFirst().getOutput();
        byte[] bytesVector = vectorAttributeConverter.convertToDatabaseColumn(queryVector);

        var chunks = kbDocumentChunkRepository.findMostSimilarByCosine(
                bytesVector,
                0.5,
                10
        );
        var chunkDTOs = range(0, chunks.size()).mapToObj(i -> {
            var c = chunks.get(i);
            var sourcePages = rangeClosed(c.getStartPage() + 1, c.getEndPage() + 1).toArray();
            return new ChunkDTO(i + 1, c.getKbDocument().getFileName(), c.getKbDocument().getUuid(), c.getContent(),
                    sourcePages);
        }).toList();

        if (context.getContext().get(CITATIONS.name()) instanceof CitationCollector citationCollector) {
            citationCollector.collectChunks(chunkDTOs);
        }

        return new KBSimilaritySearchResponse(chunkDTOs);
    }
    public record KBSimilaritySearchResponse(List<ChunkDTO> chunks) {}

    @Builder
    public record ChunkDTO(int id, @JsonIgnore String fileName, @JsonIgnore String uuid, String content,
            @JsonIgnore int[] sourcePages) {}

}
