package ch.exmachina.cosmo42.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionConfigTest {

    @Test
    void createsExecutorWithConfiguredPoolSizes() {
        IngestionConfig config = new IngestionConfig();

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.ingestionExecutor(2, 4, 50);

        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).isEqualTo(4);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("ingestion-");
    }

}
