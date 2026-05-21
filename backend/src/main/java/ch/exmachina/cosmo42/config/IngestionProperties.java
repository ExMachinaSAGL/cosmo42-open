package ch.exmachina.cosmo42.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cosmo42.ingestion")
@Getter
@Setter
public class IngestionProperties {

    private int maxPageAttempts = 3;
    private int pageChunkingTimeoutSeconds = 600;
}
