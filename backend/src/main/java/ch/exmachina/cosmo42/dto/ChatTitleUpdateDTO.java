package ch.exmachina.cosmo42.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatTitleUpdateDTO(
        @NotBlank @Size(max = 80) String title
) {}
