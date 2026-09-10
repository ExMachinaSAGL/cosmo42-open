package ch.exmachina.cosmo42.mappers;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import ch.exmachina.cosmo42.dto.ChatConversationDTO;
import ch.exmachina.cosmo42.dto.ChatConversationListItemDTO;
import ch.exmachina.cosmo42.dto.ChatMessageDTO;
import ch.exmachina.cosmo42.dto.CitationEntryDTO;
import ch.exmachina.cosmo42.entities.ChatConversation;
import ch.exmachina.cosmo42.services.chat.ChatAttribute;

@Component
public class ChatConversationMapper {

    public ChatConversationListItemDTO toListItem(ChatConversation c) {
        return new ChatConversationListItemDTO(
                c.getUuid(),
                c.getTitle(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    public ChatConversationDTO toDetail(ChatConversation c, List<Message> messages) {
        return new ChatConversationDTO(
                c.getUuid(),
                c.getTitle(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                messages.stream().map(this::toMessage).toList()
        );
    }

    public ChatMessageDTO toMessage(Message m) {
		@SuppressWarnings("unchecked")
		var citations = (List<CitationEntryDTO>)m.getMetadata().getOrDefault(ChatAttribute.CITATIONS.name(), List.of());
		return new ChatMessageDTO(
                m.getMessageType().getValue(),
                m.getText(),
                citations
        );
    }
}
