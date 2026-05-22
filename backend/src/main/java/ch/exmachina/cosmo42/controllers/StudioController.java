package ch.exmachina.cosmo42.controllers;

import ch.exmachina.cosmo42.services.StudioService;
import ch.exmachina.cosmo42.utils.MimeTypeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/studio")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Tag(name = "Studio", description = "LLM Studio for experiments")
public class StudioController {

    StudioService studioService;

    @PostMapping(value = "/run", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Run an experiment in the studio")
    public ResponseEntity<String> runExperiment(
            @RequestParam(value = "prompt") String prompt,
            @RequestParam(value = "attachments", required = false) List<MultipartFile> attachments) {

        if ( prompt == null || prompt.isEmpty() ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty prompt.");
        }
        if (attachments != null) {
            for( MultipartFile file : attachments ) {
                if (file.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty file.");
                }
                if (!MimeTypeUtils.isSupportedMimeType(file)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Only PDF, DOCX, and XLSX files are supported.");
                }
            }
        }
        return ResponseEntity.ok(studioService.elaborate(prompt, attachments));
    }
}
