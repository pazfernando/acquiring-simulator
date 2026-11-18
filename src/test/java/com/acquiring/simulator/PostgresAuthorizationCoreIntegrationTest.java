package com.acquiring.simulator;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Test-profile execution of the authorization core scenarios against an
 * isolated PostgreSQL container.
 *
 * <p>The container is the only database used: no existing test or production
 * service is contacted, and the schema is initialised fresh from
 * {@code db/schema.sql}. The image is pinned to the same major version as the
 * approved test/production baseline.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestDoublesConfiguration.class)
@Testcontainers
class PostgresAuthorizationCoreIntegrationTest extends AuthorizationCoreScenarios {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("acquiring_simulator")
            .withUsername("acquiring")
            .withPassword("acquiring");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
