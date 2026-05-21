package ch.exmachina.cosmo42;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mariadb.MariaDBContainer;

public abstract class AbstractIntegrationTest {

    static final MariaDBContainer MARIADB =
            new MariaDBContainer("mariadb:11.8.5")
                    .withDatabaseName("cosmo42")
                    .withUsername("cosmo42")
                    .withPassword("changeme");

    static {
        MARIADB.start();
    }

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
    }
}
