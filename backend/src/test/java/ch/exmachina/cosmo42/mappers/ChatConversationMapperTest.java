package ch.exmachina.cosmo42.mappers;

import ch.exmachina.cosmo42.entities.ChatConversation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatConversationMapperTest {

    private final ChatConversationMapper mapper = new ChatConversationMapper();

    @Test
    void toListItemCopiesFields() {
        ChatConversation c = new ChatConversation();
        c.setUuid("u-1");
        c.setTitle("hello");
        LocalDateTime t = LocalDateTime.now();
        c.setCreatedAt(t);
        c.setUpdatedAt(t);

        var dto = mapper.toListItem(c);

        assertThat(dto.uuid()).isEqualTo("u-1");
        assertThat(dto.title()).isEqualTo("hello");
        assertThat(dto.createdAt()).isEqualTo(t);
        assertThat(dto.updatedAt()).isEqualTo(t);
    }

    @Test
    void toListItemAllowsNullTitle() {
        ChatConversation c = new ChatConversation();
        c.setUuid("u-1");
        c.setTitle(null);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());

        var dto = mapper.toListItem(c);

        assertThat(dto.title()).isNull();
    }

    @Test
    void toMessageMapsUserMessage() {
        var msg = mapper.toMessage(new UserMessage("hi"));
        assertThat(msg.role()).isEqualTo("user");
        assertThat(msg.content()).isEqualTo("hi");
    }

    @Test
    void chatMessageDtoDoesNotExposeUnavailableTimestamp() {
        assertThat(Arrays.stream(ch.exmachina.cosmo42.dto.ChatMessageDTO.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("role", "content");
    }

    @Test
    void toMessageMapsAssistantMessage() {
        var msg = mapper.toMessage(new AssistantMessage("hello"));
        assertThat(msg.role()).isEqualTo("assistant");
        assertThat(msg.content()).isEqualTo("hello");
    }

    @Test
    void toDetailIncludesAllMessages() {
        ChatConversation c = new ChatConversation();
        c.setUuid("u-1");
        c.setTitle("t");
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());

        var detail = mapper.toDetail(c, List.of(
                new UserMessage("q"), new AssistantMessage("a")
        ));

        assertThat(detail.messages()).hasSize(2);
        assertThat(detail.messages().get(0).role()).isEqualTo("user");
        assertThat(detail.messages().get(1).role()).isEqualTo("assistant");
    }
}
