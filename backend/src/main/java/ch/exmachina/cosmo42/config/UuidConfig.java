package ch.exmachina.cosmo42.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;
import java.util.function.Supplier;

@Configuration
public class UuidConfig {

    @Bean
    public Supplier<String> uuidSupplier() {
        return () -> UUID.randomUUID().toString();
    }
}