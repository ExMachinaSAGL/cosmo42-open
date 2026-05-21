package ch.exmachina.cosmo42.controllers;

import ch.exmachina.cosmo42.services.MimeTypeService;
import ch.exmachina.cosmo42.dto.DocumentDTO;
import ch.exmachina.cosmo42.dto.DownloadDocumentDTO;
import ch.exmachina.cosmo42.services.KBDocumentService;
import ch.exmachina.cosmo42.utils.MimeTypeUtils;
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
    MimeTypeService mimeTypeService;

    @GetMapping
    @Operation(summary = "List all documents with ingestion status")
    public List<DocumentDTO> getDocuments() {
        return kbDocumentService.listAllKBDocuments();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a document",
            description = "Starts async ingestion. Returns immediately with job status.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Job started",
                            content = @Content(schema = @Schema(implementation = DocumentDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Empty file or unsupported format")
            }
    )
    public ResponseEntity<DocumentDTO> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty file.");
        }
        if (!MimeTypeUtils.isSupportedMimeType(file)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only PDF, DOCX, and XLSX files are supported.");
        }
        if (mimeTypeService.isSupportedMimeType(file)) {
            return ResponseEntity.ok(kbDocumentService.saveKBDocument(file));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Only PDF files are supported.");
    }

    @GetMapping("/{uuid}")
    @Operation(
            summary = "Get document with ingestion status",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Document with current ingestion status"),
                    @ApiResponse(responseCode = "404", description = "Document not found")
            }
    )
    public ResponseEntity<DocumentDTO> getDocument(@PathVariable String uuid) {
        return kbDocumentService.getDocument(uuid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{uuid}/download")
    @Operation(summary = "Download the original document file")
    public ResponseEntity<ByteArrayResource> downloadDocument(@PathVariable UUID uuid) {
        DownloadDocumentDTO dto = kbDocumentService.downloadKBDocument(uuid.toString());
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dto.getFileName() + "\"");
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
