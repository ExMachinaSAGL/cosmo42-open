package ch.exmachina.cosmo42.services.chat.tools;

import ch.exmachina.cosmo42.entities.KBDocumentChunk;
import ch.exmachina.cosmo42.entities.converters.VectorAttributeConverter;
import ch.exmachina.cosmo42.repositories.KBDocumentChunkRepository;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public KBSimilaritySearchResponse search(KBSimilaritySearchRequest request, ToolContext context) {
        emitStatus(context, "Searching Knowledge Base...");

        EmbeddingResponse embeddingResponse = embeddingModel.call(
                new EmbeddingRequest(List.of(request.query()), embeddingModelOptions));
        float[] queryVector = embeddingResponse.getResults().getFirst().getOutput();
        byte[] bytesVector = vectorAttributeConverter.convertToDatabaseColumn(queryVector);

        List<KBDocumentChunk> chunks = kbDocumentChunkRepository.findMostSimilarByCosine(bytesVector, 0.5, 10);
        List<ChunkDTO> chunkDTOs = chunks.stream()
                .map(c -> new ChunkDTO(c.getKbDocument().getFileName(), c.getContent()))
                .toList();
        return new KBSimilaritySearchResponse(chunkDTOs);
    }

    public record KBSimilaritySearchRequest(
            @JsonPropertyDescription("A specific, context-rich search query written in natural language, designed to retrieve the most semantically relevant documents.")
            String query
    ) {}

    public record KBSimilaritySearchResponse(List<ChunkDTO> chunks) {}

    public record ChunkDTO(String fileName, String content) {}

}
