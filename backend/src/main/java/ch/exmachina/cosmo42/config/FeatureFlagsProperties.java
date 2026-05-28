package ch.exmachina.cosmo42.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "cosmo42.features")
@Data
public class FeatureFlagsProperties {

    private Map<String, Boolean> flags;

}
