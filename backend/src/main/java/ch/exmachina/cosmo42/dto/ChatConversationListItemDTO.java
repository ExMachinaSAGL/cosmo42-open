package ch.exmachina.cosmo42.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ChatConversationListItemDTO(
        String uuid,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
