package ch.exmachina.cosmo42.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatConversationDTO(
        String uuid,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ChatMessageDTO> messages
) {}
