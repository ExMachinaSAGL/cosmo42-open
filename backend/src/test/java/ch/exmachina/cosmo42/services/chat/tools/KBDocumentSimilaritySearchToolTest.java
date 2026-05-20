package ch.exmachina.cosmo42.services.chat.tools;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.entities.KBDocumentChunk;
import ch.exmachina.cosmo42.entities.KBDocumentChunkType;
import ch.exmachina.cosmo42.entities.converters.VectorAttributeConverter;
import ch.exmachina.cosmo42.repositories.KBDocumentChunkRepository;
import ch.exmachina.cosmo42.services.chat.ChatAttribute;
import ch.exmachina.cosmo42.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KBDocumentSimilaritySearchToolTest {

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
        tool = new KBDocumentSimilaritySearchTool(
                embeddingModel, embeddingOptions, converter, chunkRepository);
    }

    @Test
    void embedsQueryAndCallsRepositoryWithConvertedVector() {
        float[] queryVec = Fixtures.unitVector(1024, 0);
        stubEmbeddingResponse(queryVec);
        when(chunkRepository.findMostSimilarByCosine(any(), eq(0.5), eq(10)))
                .thenReturn(List.of());

        tool.search(request("alpha beta gamma"), emptyContext());

        ArgumentCaptor<byte[]> bytesCap = ArgumentCaptor.forClass(byte[].class);
        verify(chunkRepository).findMostSimilarByCosine(bytesCap.capture(), eq(0.5), eq(10));
        assertThat(bytesCap.getValue()).isEqualTo(converter.convertToDatabaseColumn(queryVec));
    }

    @Test
    void embeddingRequestIncludesQueryAndConfiguredOptions() {
        float[] queryVec = Fixtures.zeroVector(1024);
        stubEmbeddingResponse(queryVec);
        when(chunkRepository.findMostSimilarByCosine(any(), any(), any(Integer.class)))
                .thenReturn(List.of());

        tool.search(request("hello world"), emptyContext());

        ArgumentCaptor<EmbeddingRequest> reqCap = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingModel).call(reqCap.capture());
        assertThat(reqCap.getValue().getInstructions()).containsExactly("hello world");
        assertThat(reqCap.getValue().getOptions()).isSameAs(embeddingOptions);
    }

    @Test
    void mapsRepositoryChunksToDtosPreservingOrder() {
        stubEmbeddingResponse(Fixtures.zeroVector(1024));
        KBDocument doc1 = Fixtures.document("doc-uuid-1", "first.pdf");
        KBDocument doc2 = Fixtures.document("doc-uuid-2", "second.pdf");
        KBDocumentChunk chunk1 = Fixtures.chunk(doc1, KBDocumentChunkType.TEXT, "first content", Fixtures.zeroVector(1024));
        KBDocumentChunk chunk2 = Fixtures.chunk(doc2, KBDocumentChunkType.TABLE, "second content", Fixtures.zeroVector(1024));
        when(chunkRepository.findMostSimilarByCosine(any(), any(), any(Integer.class)))
                .thenReturn(List.of(chunk1, chunk2));

        KBDocumentSimilaritySearchTool.KBSimilaritySearchResponse response =
                tool.search(request("q"), emptyContext());

        assertThat(response.chunks())
                .containsExactly(
                        new KBDocumentSimilaritySearchTool.ChunkDTO("first.pdf", "first content"),
                        new KBDocumentSimilaritySearchTool.ChunkDTO("second.pdf", "second content"));
    }

    @Test
    void emptyRepositoryResultProducesEmptyResponse() {
        stubEmbeddingResponse(Fixtures.zeroVector(1024));
        when(chunkRepository.findMostSimilarByCosine(any(), any(), any(Integer.class)))
                .thenReturn(List.of());

        KBDocumentSimilaritySearchTool.KBSimilaritySearchResponse response =
                tool.search(request("nothing matches"), emptyContext());

        assertThat(response.chunks()).isEmpty();
    }

    @Test
    void emitsStatusEventOnSinkBeforeRepositoryCall() {
        stubEmbeddingResponse(Fixtures.zeroVector(1024));
        when(chunkRepository.findMostSimilarByCosine(any(), any(), any(Integer.class)))
                .thenReturn(List.of());

        Sinks.Many<ServerSentEvent<ChatResponseDTO>> sink =
                Sinks.many().multicast().onBackpressureBuffer();

        tool.search(request("q"), contextWithSink(sink));
        sink.tryEmitComplete();

        StepVerifier.create(sink.asFlux())
                .assertNext(sse -> {
                    assertThat(sse.data()).isNotNull();
                    assertThat(sse.data().getType()).isEqualTo(ChatEventType.STATUS);
                    assertThat(sse.data().getData()).isEqualTo("Searching Knowledge Base...");
                })
                .verifyComplete();
    }

    @Test
    void missingSinkInContextDoesNotThrow() {
        stubEmbeddingResponse(Fixtures.zeroVector(1024));
        when(chunkRepository.findMostSimilarByCosine(any(), any(), any(Integer.class)))
                .thenReturn(List.of());

        // No SINK key in the context map at all.
        KBDocumentSimilaritySearchTool.KBSimilaritySearchResponse response =
                tool.search(request("q"), new ToolContext(new HashMap<>()));

        assertThat(response.chunks()).isEmpty();
    }

    private void stubEmbeddingResponse(float[] vector) {
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
            EmbeddingRequest req = invocation.getArgument(0);
            return new EmbeddingResponse(req.getInstructions().stream()
                    .map(s -> new Embedding(vector.clone(), 0))
                    .toList());
        });
    }

    private static KBDocumentSimilaritySearchTool.KBSimilaritySearchRequest request(String query) {
        return new KBDocumentSimilaritySearchTool.KBSimilaritySearchRequest(query);
    }

    private static ToolContext emptyContext() {
        return new ToolContext(new HashMap<>());
    }

    private static ToolContext contextWithSink(Sinks.Many<ServerSentEvent<ChatResponseDTO>> sink) {
        Map<String, Object> map = new HashMap<>();
        map.put(ChatAttribute.SINK.name(), sink);
        return new ToolContext(map);
    }
}
