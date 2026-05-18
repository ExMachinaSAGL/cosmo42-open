package ch.exmachina.cosmo42.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequestDTO(
        @Size(max = 36) String uuid,
        @NotBlank @Size(max = 8000) String message
) {}
