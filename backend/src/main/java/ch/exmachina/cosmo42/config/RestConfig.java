package ch.exmachina.cosmo42.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestConfig {

    @Value("${cosmo42.libreoffice.base-url}")
    String libreofficeBaseUrl;

    @Bean
    public RestClient libreofficeRestClient() {
        return RestClient.builder()
                .baseUrl(libreofficeBaseUrl)
                .build();
    }

}
