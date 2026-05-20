package ch.exmachina.cosmo42.testsupport;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ChatModelMocks {

    private ChatModelMocks() {}

    public static ChatModel replyingWith(String content) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions())
                .thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(content));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chatResponse(content)));
        return chatModel;
    }

    public static ChatModel streamingChunks(String... chunks) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions())
                .thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        Flux<ChatResponse> stream = Flux.fromArray(chunks).map(ChatModelMocks::chatResponse);
        when(chatModel.stream(any(Prompt.class))).thenReturn(stream);
        return chatModel;
    }

    public static ChatResponse chatResponse(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    public static void stubDefaultOptions(ChatModel model) {
        when(model.getDefaultOptions())
                .thenReturn(OpenAiChatOptions.builder().model("test-model").build());
    }
}
