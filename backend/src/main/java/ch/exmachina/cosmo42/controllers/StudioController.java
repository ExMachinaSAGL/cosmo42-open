package ch.exmachina.cosmo42.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/studio")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Tag(name = "Studio", description = "LLM Studio for experiments")
public class StudioController {

    // TODO: Inject a StudioService here when implemented
    // StudioService studioService;

    @PostMapping(value = "/run", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Run an experiment in the studio")
    public ResponseEntity<String> runExperiment(
            @RequestParam(value = "prompt", required = false) String prompt,
            @RequestParam(value = "attachments", required = false) List<MultipartFile> attachments) {

        // TODO: Replace with actual implementation by calling the StudioService
        System.out.println("Received prompt: " + prompt);
        
        int attachmentCount = (attachments != null) ? attachments.size() : 0;
        System.out.println("Received " + attachmentCount + " attachments.");
        
        if (attachments != null) {
            attachments.forEach(file -> System.out.println("Attachment: " + file.getOriginalFilename()));
        }

        String simulatedResponse = "{ \"k1\": \"value 1\", \"k2\": \"value 2\" }";
        
        return ResponseEntity.ok(simulatedResponse);
    }
}
