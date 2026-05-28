package ch.exmachina.cosmo42.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Tag(name = "Config", description = "Configuration endpoints")
public class ConfigController {

    @Value("${cosmo42.features.studio:false}")
    boolean studioFeatureEnabled;

    @GetMapping("/features")
    @Operation(summary = "Get feature flags")
    public ResponseEntity<Map<String, Boolean>> getFeatureFlags() {
        Map<String, Boolean> features = new HashMap<>();
        features.put("studio", studioFeatureEnabled);
        return ResponseEntity.ok(features);
    }
}
