package ch.exmachina.cosmo42.dto;

import lombok.Builder;

@Builder
public record CitationEntryDTO(
        int id,
        int originalIndex,
        String fileName,
        String uuid,
        int[] sourcePages
) {}
