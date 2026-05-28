package ch.exmachina.cosmo42.controllers;

import ch.exmachina.cosmo42.config.FeatureFlagsProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@Tag(name = "Config", description = "Configuration endpoints")
public class ConfigController {

    private final FeatureFlagsProperties props;

    @GetMapping("/features")
    @Operation(summary = "Get feature flags")
    public ResponseEntity<Map<String, Boolean>> getFeatureFlags() {
        return ResponseEntity.ok(props.getFlags());
    }
}
