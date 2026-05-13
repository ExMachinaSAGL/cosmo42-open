package ch.exmachina.cosmo42.controllers;

import ch.exmachina.cosmo42.utils.MimeTypeUtils;
import ch.exmachina.cosmo42.dto.DocumentDTO;
import ch.exmachina.cosmo42.services.KBDocumentService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kb/documents")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class KBDocumentsController {

    KBDocumentService kbDocumentService;

    @GetMapping
    public List<DocumentDTO> getDocuments() {
        return kbDocumentService.listAllKBDocuments();
    }

    @PostMapping
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Empty file.");
        }
        if (MimeTypeUtils.isSupportedMimeType(file)) {
            return ResponseEntity.ok(kbDocumentService.saveKBDocument(file));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Only PDF files are supported.");
    }

    @GetMapping("/{uuid}/download")
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
    public void deleteDocument(@PathVariable UUID uuid) {
        kbDocumentService.deleteKBDocument(uuid.toString());
    }
}
