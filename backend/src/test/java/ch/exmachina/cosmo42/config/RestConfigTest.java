package ch.exmachina.cosmo42.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RestConfig.class)
@TestPropertySource(properties = "cosmo42.libreoffice.base-url=http://localhost:9999")
class RestConfigTest {

    @Autowired
    RestClient libreofficeRestClient;

    @Test
    void createsLibreofficeRestClient() {
        assertThat(libreofficeRestClient).isNotNull();
    }

}
