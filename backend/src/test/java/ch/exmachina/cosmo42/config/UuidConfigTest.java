package ch.exmachina.cosmo42.config;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class UuidConfigTest {

    @Test
    void uuidSupplierGeneratesValidUuids() {
        UuidConfig config = new UuidConfig();
        Supplier<String> supplier = config.uuidSupplier();

        String uuid = supplier.get();

        assertThat(UUID.fromString(uuid)).isNotNull();
    }
}