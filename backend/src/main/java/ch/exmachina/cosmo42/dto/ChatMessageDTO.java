package ch.exmachina.cosmo42.dto;

import java.util.List;

public record ChatMessageDTO(
        String role,
        String content,
        List<CitationEntryDTO> citations
) {}
