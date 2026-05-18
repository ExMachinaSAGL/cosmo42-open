package ch.exmachina.cosmo42.controllers;

import ch.exmachina.cosmo42.utils.MimeTypeUtils;
import ch.exmachina.cosmo42.dto.DocumentDTO;
import ch.exmachina.cosmo42.dto.JobAcceptedDTO;
import ch.exmachina.cosmo42.services.KBDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kb/documents")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Tag(name = "Knowledge Base", description = "Document management")
public class KBDocumentsController {

    KBDocumentService kbDocumentService;

    @GetMapping
    @Operation(summary = "Document list")
    public List<DocumentDTO> getDocuments() {
        return kbDocumentService.listAllKBDocuments();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a document",
            description = "Starts async ingestion. Returns immediately a jobUuid used to check the progress.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Job started",
                            content = @Content(schema = @Schema(implementation = JobAcceptedDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Empty file or unsupported format")
            }
    )
    public ResponseEntity<JobAcceptedDTO> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty file.");
        }
        if (!MimeTypeUtils.isSupportedMimeType(file)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only PDF, DOCX, and XLSX files are supported.");
        }
        return ResponseEntity.accepted().body(kbDocumentService.enqueueKBDocument(file));
    }

    @GetMapping("/{uuid}/download")
    @Operation(summary = "Download the original document file")
    public ResponseEntity<ByteArrayResource> downloadDocument(@PathVariable UUID uuid) {
        DocumentDTO dto = kbDocumentService.loadKBDocument(uuid.toString());
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dto.getName() + "\"");
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(dto.getContent().length)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(dto.getContent()));
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a document and all its chunks")
    public void deleteDocument(@PathVariable UUID uuid) {
        kbDocumentService.deleteKBDocument(uuid.toString());
    }
}
