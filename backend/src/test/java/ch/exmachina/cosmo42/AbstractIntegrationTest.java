package ch.exmachina.cosmo42;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared base for tests that need a real MariaDB.
 * Reuses a single container across the JVM via the static field.
 */
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final MariaDBContainer<?> MARIADB =
            new MariaDBContainer<>("mariadb:11.8.5")
                    .withDatabaseName("cosmo42")
                    .withUsername("cosmo42")
                    .withPassword("changeme");
}