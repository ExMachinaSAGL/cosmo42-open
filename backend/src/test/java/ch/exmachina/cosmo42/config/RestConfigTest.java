package ch.exmachina.cosmo42.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class RestConfigTest {

    @Test
    void createsLibreofficeRestClient() {
        RestConfig config = new RestConfig();
        ReflectionTestUtils.setField(config, "libreofficeBaseUrl", "http://localhost:9999");

        RestClient client = config.libreofficeRestClient();

        assertThat(client).isNotNull();
    }

}
