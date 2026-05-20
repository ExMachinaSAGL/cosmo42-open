package ch.exmachina.cosmo42;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    private static final DockerImageName MARIADB_DOCKER_IMAGE_NAME = DockerImageName.parse("mariadb:11.8.5");

    @Bean
    @ServiceConnection
    MariaDBContainer mariadbContainer() {
        return new MariaDBContainer(MARIADB_DOCKER_IMAGE_NAME);
    }

    @Bean
    TaskExecutor taskExecutor() {
        return new SyncTaskExecutor();
    }

}
