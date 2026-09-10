package ch.exmachina.cosmo42.services.chat.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.http.codec.ServerSentEvent;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.entities.KBDocumentChunk;
import ch.exmachina.cosmo42.entities.converters.VectorAttributeConverter;
import ch.exmachina.cosmo42.repositories.KBDocumentChunkRepository;
import ch.exmachina.cosmo42.services.chat.ChatAttribute;
import ch.exmachina.cosmo42.services.kb.CitationCollector;
import ch.exmachina.cosmo42.services.kb.schema.ChunkType;
import ch.exmachina.cosmo42.testsupport.EmbeddingMocks;
import ch.exmachina.cosmo42.testsupport.Fixtures;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class KBDocumentSimilaritySearchToolTest {

    CitationCollector citationCollector;
    EmbeddingModel embeddingModel;
    OpenAiEmbeddingOptions embeddingOptions;
    VectorAttributeConverter converter;
    KBDocumentChunkRepository chunkRepository;
    KBDocumentSimilaritySearchTool tool;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        embeddingOptions = OpenAiEmbeddingOptions.builder().model("test-embedding").build();
        converter = new VectorAttributeConverter();
        chunkRepository = mock(KBDocumentChunkRepository.class);
        tool = new KBDocumentSimilaritySearchTool(embeddingModel, embeddingOptions, converter, chunkRepository);
        citationCollector = mock(CitationCollector.class);
    }

    @Test
    void embedsQueryAndCallsRepositoryWithConvertedVector() {
        float[] queryVec = Fixtures.unitVector(1024, 0);
        EmbeddingMocks.stubWithFixedVector(embeddingModel, queryVec);
        when(chunkRepository.findMostSimilarByCosine(any(), any(), eq(10))).thenReturn(List.of());

        tool.search("alpha beta gamma", emptyContext());

        ArgumentCaptor<byte[]> bytesCap = ArgumentCaptor.forClass(byte[].class);
        verify(chunkRepository).findMostSimilarByCosine(bytesCap.capture(), any(), eq(10));
        assertThat(bytesCap.getValue()).isEqualTo(converter.convertToDatabaseColumn(queryVec));
    }

    @Test
    void embeddingRequestIncludesQueryAndConfiguredOptions() {
        float[] queryVec = Fixtures.zeroVector(1024);
        EmbeddingMocks.stubWithFixedVector(embeddingModel, queryVec);
        when(chunkRepository.findMostSimilarByCosine(any(), any(), any(Integer.class))).thenReturn(List.of());

        tool.search("hello world", emptyContext());

        ArgumentCaptor<EmbeddingRequest> reqCap = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingModel).call(reqCap.capture());
        assertThat(reqCap.getValue().getInstructions()).containsExactly("hello world");
        assertThat(reqCap.getValue().getOptions()).isSameAs(embeddingOptions);
    }

    @Test
    void mapsRepositoryChunksToDtosPreservingOrder() {
        EmbeddingMocks.stubWithFixedVector(embeddingModel, Fixtures.zeroVector(1024));
        KBDocument doc1 = Fixtures.document("doc-uuid-1", "first.pdf");
        KBDocument doc2 = Fixtures.document("doc-uuid-2", "second.pdf");
        KBDocumentChunk chunk1 = Fixtures.chunk(doc1, ChunkType.TEXT, "first content", new int[] { 0 },
            Fixtures.zeroVector(1024));
        KBDocumentChunk chunk2 = Fixtures.chunk(doc2, ChunkType.TABLE, "second content", new int[] { 1, 2 },
            Fixtures.zeroVector(1024));
        when(chunkRepository.findMostSimilarByCosine(any(), any(), any(Integer.class)))
            .thenReturn(List.of(chunk1, chunk2));

        ToolContext context = contextWithChunContext();
        KBDocumentSimilaritySearchTool.KBSimilaritySearchResponse response = tool.search("q", context);

        assertThat(response.chunks())
            .usingRecursiveFieldByFieldElementComparator()
            .containsExactly(
                new KBDocumentSimilaritySearchTool.ChunkDTO(1, "first.pdf", "doc-uuid-1", "first content",
                    new int[] { 1 }),
                new KBDocumentSimilaritySearchTool.ChunkDTO(2, "second.pdf", "doc-uuid-2", "second content",
                    new int[] { 2, 3 }));

        verify(citationCollector).collectChunks(response.chunks());
    }

    @Test
    void emptyRepositoryResultProducesEmptyResponse() {
        EmbeddingMocks.stubWithFixedVector(embeddingModel, Fixtures.zeroVector(1024));
        when(chunkRepository.findMostSimilarByCosine(any(), any(), any(Integer.class))).thenReturn(List.of());

        KBDocumentSimilaritySearchTool.KBSimilaritySearchResponse response = tool.search("nothing matches",
            emptyContext());

        assertThat(response.chunks()).isEmpty();
    }

    @Test
    void emitsStatusEventOnSinkBeforeRepositoryCall() {
        EmbeddingMocks.stubWithFixedVector(embeddingModel, Fixtures.zeroVector(1024));
        when(chunkRepository.findMostSimilarByCosine(any(), any(), any(Integer.class))).thenReturn(List.of());

        Sinks.Many<ServerSentEvent<ChatResponseDTO>> sink = Sinks.many().multicast().onBackpressureBuffer();

        tool.search("q", contextWithSink(sink));
        sink.tryEmitComplete();

        StepVerifier.create(sink.asFlux()).assertNext(sse -> {
            assertThat(sse.data()).isNotNull();
            assertThat(sse.data().getType()).isEqualTo(ChatEventType.STATUS);
            assertThat(sse.data().getData()).isEqualTo("Searching Knowledge Base...");
        }).verifyComplete();
    }

    @Test
    void missingSinkInContextDoesNotThrow() {
        EmbeddingMocks.stubWithFixedVector(embeddingModel, Fixtures.zeroVector(1024));
        when(chunkRepository.findMostSimilarByCosine(any(), any(), any(Integer.class))).thenReturn(List.of());

        // No SINK key in the context map at all.
        KBDocumentSimilaritySearchTool.KBSimilaritySearchResponse response = tool.search("q",
            new ToolContext(new HashMap<>()));

        assertThat(response.chunks()).isEmpty();
    }

    @Test
    void embeddingModelFailurePropagates() {
        when(embeddingModel.call(any(EmbeddingRequest.class)))
            .thenThrow(new RuntimeException("Embedding service down"));

        assertThatThrownBy(() -> tool.search("q", emptyContext())).isInstanceOf(RuntimeException.class)
            .hasMessage("Embedding service down");
    }

    @Test
    void vectorConverterFailurePropagates() {
        float[] queryVec = Fixtures.unitVector(1024, 0);
        EmbeddingMocks.stubWithFixedVector(embeddingModel, queryVec);
        VectorAttributeConverter throwingConverter = mock(VectorAttributeConverter.class);
        when(throwingConverter.convertToDatabaseColumn(queryVec))
            .thenThrow(new IllegalArgumentException("Invalid vector"));
        KBDocumentSimilaritySearchTool toolWithFailingConverter = new KBDocumentSimilaritySearchTool(embeddingModel,
            embeddingOptions, throwingConverter, chunkRepository);

        assertThatThrownBy(() -> toolWithFailingConverter.search("q", emptyContext()))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid vector");
    }

    @Test
    void repositoryQueryFailurePropagates() {
        EmbeddingMocks.stubWithFixedVector(embeddingModel, Fixtures.zeroVector(1024));
        when(chunkRepository.findMostSimilarByCosine(any(), any(), any(Integer.class)))
            .thenThrow(new RuntimeException("DB connection lost"));

        assertThatThrownBy(() -> tool.search("q", emptyContext())).isInstanceOf(RuntimeException.class)
            .hasMessage("DB connection lost");
    }

    private static ToolContext emptyContext() {
        return new ToolContext(new HashMap<>());
    }

    private static ToolContext contextWithSink(Sinks.Many<ServerSentEvent<ChatResponseDTO>> sink) {
        Map<String, Object> map = new HashMap<>();
        map.put(ChatAttribute.SINK.name(), sink);
        return new ToolContext(map);
    }

    private ToolContext contextWithChunContext() {
        Map<String, Object> map = new HashMap<>();
        map.put(ChatAttribute.CITATIONS.name(), citationCollector);
        return new ToolContext(map);
    }
}
