package com.acquiring.simulator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestDoublesConfiguration {

    public static final Instant BASE = Instant.parse("2026-11-18T12:00:00Z");

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock(BASE);
    }

    @Bean
    @Primary
    StubIssuerSimulator stubIssuerSimulator() {
        return new StubIssuerSimulator();
    }

    static Path temporaryDatabase() {
        try {
            Path directory = Files.createTempDirectory("acq-sim-sqlite-");
            return directory.resolve("simulator.db");
        } catch (IOException e) {
            throw new IllegalStateException("cannot create temporary database", e);
        }
    }
}
