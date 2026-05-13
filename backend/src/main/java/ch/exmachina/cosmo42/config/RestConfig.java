package ch.exmachina.cosmo42.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class RestConfig {

    @Value("${cosmo42.libreoffice.base-url}")
    String libreofficeBaseUrl;

    @Bean
    public WebClient libreofficeWebClient() {
        return WebClient.builder()
                .baseUrl(libreofficeBaseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                .build();
    }


}
