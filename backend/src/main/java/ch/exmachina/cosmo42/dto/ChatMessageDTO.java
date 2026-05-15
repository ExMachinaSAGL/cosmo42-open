package ch.exmachina.cosmo42.dto;

import java.time.LocalDateTime;

public record ChatMessageDTO(
        String role,
        String content,
        LocalDateTime timestamp
) {}
