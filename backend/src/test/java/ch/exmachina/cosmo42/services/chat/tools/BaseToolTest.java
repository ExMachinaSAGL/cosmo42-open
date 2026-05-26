package ch.exmachina.cosmo42.services.chat.tools;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.services.chat.ChatAttribute;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BaseToolTest {

    private final TestTool tool = new TestTool();

    @Nested
    class StripJsonFences {

        @Test
        void nullInputReturnsNull() {
            assertThat(TestTool.publicStripJsonFences(null)).isNull();
        }

        @Test
        void plainTextLeftUnchanged() {
            assertThat(TestTool.publicStripJsonFences("hello world")).isEqualTo("hello world");
        }

        @Test
        void leadingTrailingWhitespaceTrimmedEvenWithoutFences() {
            assertThat(TestTool.publicStripJsonFences("   hello   ")).isEqualTo("hello");
        }

        @Test
        void unlabeledFenceStripped() {
            String input = "```\n{\"key\":\"value\"}\n```";

            assertThat(TestTool.publicStripJsonFences(input)).isEqualTo("{\"key\":\"value\"}");
        }

        @Test
        void languageLabeledFenceStripped() {
            String input = "```json\n{\"key\":\"value\"}\n```";

            assertThat(TestTool.publicStripJsonFences(input)).isEqualTo("{\"key\":\"value\"}");
        }

        @Test
        void mixedCaseLanguageLabelStripped() {
            String input = "```PYTHON\nprint('hi')\n```";

            assertThat(TestTool.publicStripJsonFences(input)).isEqualTo("print('hi')");
        }

        @Test
        void fenceWithLeadingWhitespaceStillStripped() {
            String input = "   ```json\n{}\n```   ";

            assertThat(TestTool.publicStripJsonFences(input)).isEqualTo("{}");
        }

        @Test
        void fenceWithNoNewlineAfterLanguageLabelStripped() {
            String input = "```json{\"k\":1}\n```";

            assertThat(TestTool.publicStripJsonFences(input)).isEqualTo("{\"k\":1}");
        }
    }

    @Nested
    class EmitStatus {

        @Test
        void emitsStatusEventOnSinkWhenContextContainsValidSink() {
            Sinks.Many<ServerSentEvent<ChatResponseDTO>> sink =
                    Sinks.many().multicast().onBackpressureBuffer();
            ToolContext context = contextWithSink(sink);

            tool.callEmitStatus(context, "searching...");
            sink.tryEmitComplete();

            StepVerifier.create(sink.asFlux())
                    .assertNext(sse -> {
                        assertThat(sse.data()).isNotNull();
                        assertThat(sse.data().getType()).isEqualTo(ChatEventType.STATUS);
                        assertThat(sse.data().getData()).isEqualTo("searching...");
                    })
                    .verifyComplete();
        }

        @Test
        void missingSinkInContextIsSilentlyIgnored() {
            ToolContext context = new ToolContext(new HashMap<>());

            // Must not throw.
            tool.callEmitStatus(context, "no sink here");
        }

        @Test
        void wrongTypeAtSinkKeyIsSilentlyIgnored() {
            Map<String, Object> map = new HashMap<>();
            map.put(ChatAttribute.SINK.name(), "not-a-sink");

            tool.callEmitStatus(new ToolContext(map), "should not crash");
        }

        @Test
        void alreadyCompletedSinkDoesNotThrow() {
            Sinks.Many<ServerSentEvent<ChatResponseDTO>> sink =
                    Sinks.many().multicast().onBackpressureBuffer();
            sink.tryEmitComplete();
            ToolContext context = contextWithSink(sink);

            tool.callEmitStatus(context, "after complete");
        }
    }

    private static ToolContext contextWithSink(Sinks.Many<ServerSentEvent<ChatResponseDTO>> sink) {
        Map<String, Object> map = new HashMap<>();
        map.put(ChatAttribute.SINK.name(), sink);
        return new ToolContext(map);
    }

    private static class TestTool extends BaseTool {
        void callEmitStatus(ToolContext context, String message) {
            emitStatus(context, message);
        }

        static String publicStripJsonFences(String raw) {
            return stripJsonFences(raw);
        }
    }
}
