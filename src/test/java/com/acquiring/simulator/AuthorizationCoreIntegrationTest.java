package com.acquiring.simulator;

import java.nio.file.Path;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Local-profile execution of the authorization core scenarios against SQLite.
 *
 * <p>SQLite is the approved local-development engine only. The same scenarios
 * run against PostgreSQL in {@link PostgresAuthorizationCoreIntegrationTest}.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(TestDoublesConfiguration.class)
class AuthorizationCoreIntegrationTest extends AuthorizationCoreScenarios {

    private static final Path DATABASE = TestDoublesConfiguration.temporaryDatabase();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:" + DATABASE.toAbsolutePath() + "?busy_timeout=5000");
        registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        registry.add("spring.datasource.username", () -> "");
        registry.add("spring.datasource.password", () -> "");
    }
}
