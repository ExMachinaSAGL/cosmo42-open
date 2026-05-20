package ch.exmachina.cosmo42.services.chat.processors;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatRequestDTO;
import ch.exmachina.cosmo42.services.chat.ChatContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class UuidProcessorTest {

    private final UuidProcessor processor = new UuidProcessor();

    @Test
    void newChatEmitsSingleUuidSseEvent() {
        ChatContext ctx = ChatContext.builder()
                .newChat(true)
                .chatUuid("new-uuid-123")
                .request(new ChatRequestDTO(null, "hi"))
                .eventSink(Sinks.many().multicast().onBackpressureBuffer())
                .build();

        StepVerifier.create(processor.process(ctx))
                .assertNext(sse -> {
                    assertThat(sse.data()).isNotNull();
                    assertThat(sse.data().getType()).isEqualTo(ChatEventType.UUID);
                    assertThat(sse.data().getData()).isEqualTo("new-uuid-123");
                })
                .verifyComplete();
    }

    @Test
    void existingChatProducesEmptyFlux() {
        ChatContext ctx = ChatContext.builder()
                .newChat(false)
                .chatUuid("existing-uuid")
                .request(new ChatRequestDTO("existing-uuid", "follow-up"))
                .eventSink(Sinks.many().multicast().onBackpressureBuffer())
                .build();

        StepVerifier.create(processor.process(ctx)).verifyComplete();
    }

    @Test
    void gatesOnRequestUuidNotContextNewChatFlag() {
        // The processor decides based on request.uuid(), not context.isNewChat() —
        // pin this so a future refactor that conflates the two trips a test.
        ChatContext ctx = ChatContext.builder()
                .newChat(true)
                .chatUuid("ignored")
                .request(new ChatRequestDTO("client-supplied", "hi"))
                .eventSink(Sinks.many().multicast().onBackpressureBuffer())
                .build();

        StepVerifier.create(processor.process(ctx)).verifyComplete();
    }
}
