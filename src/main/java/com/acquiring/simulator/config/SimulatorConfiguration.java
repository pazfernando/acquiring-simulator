package com.acquiring.simulator.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SimulatorProperties.class)
public class SimulatorConfiguration {

    /** Injected clock keeps the 30-second default and window boundaries testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
