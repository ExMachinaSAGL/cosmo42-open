package ch.exmachina.cosmo42.controllers;

import ch.exmachina.cosmo42.dto.JobStatusDTO;
import ch.exmachina.cosmo42.services.IngestionJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kb/jobs")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Tag(name = "Ingestion Jobs", description = "Async upload progress monitoring")
public class IngestionJobController {

    IngestionJobService ingestionJobService;

    @GetMapping("/{uuid}")
    @Operation(
            summary = "Get the status of an ingestion job",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Current status with progressPercent 0-100"),
                    @ApiResponse(responseCode = "404", description = "Job not found")
            }
    )
    public ResponseEntity<JobStatusDTO> getJobStatus(@PathVariable String uuid) {
        return ingestionJobService.findByUuid(uuid)
                .map(job -> ResponseEntity.ok(ingestionJobService.toStatusDTO(job)))
                .orElse(ResponseEntity.notFound().build());
    }
}