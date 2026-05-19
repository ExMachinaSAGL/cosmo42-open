package ch.exmachina.cosmo42;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;

/**
 * Shared base for tests that need a real MariaDB.
 * Reuses a single manually managed container across the JVM so Spring context
 * caching never points at a container stopped after another test class.
 */
public abstract class AbstractIntegrationTest {

    static final MariaDBContainer<?> MARIADB =
            new MariaDBContainer<>("mariadb:11.8.5")
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
