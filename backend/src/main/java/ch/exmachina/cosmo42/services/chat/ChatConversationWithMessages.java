package ch.exmachina.cosmo42.services.chat;

import ch.exmachina.cosmo42.entities.ChatConversation;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

public record ChatConversationWithMessages(
        ChatConversation conversation,
        List<Message> messages
) {}
