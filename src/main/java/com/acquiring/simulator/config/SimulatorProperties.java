package com.acquiring.simulator.config;

import com.acquiring.simulator.domain.model.CredentialDecision;
import com.acquiring.simulator.domain.model.Route;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Validated, typed simulator configuration. Markdown decision records are never runtime
 * configuration. All identifiers and fixtures are synthetic.
 */
@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(Authorization authorization, Profile profile) {

    public SimulatorProperties {
        Objects.requireNonNull(authorization, "simulator.authorization is required");
        Objects.requireNonNull(profile, "simulator.profile is required");
    }

    public record Authorization(Duration duplicateWindow, Duration responseTimeout) {
        public Authorization {
            Objects.requireNonNull(duplicateWindow, "simulator.authorization.duplicate-window is required");
            Objects.requireNonNull(responseTimeout, "simulator.authorization.response-timeout is required");
            if (duplicateWindow.isZero() || duplicateWindow.isNegative()) {
                throw new IllegalArgumentException("duplicate window must be positive");
            }
            if (responseTimeout.isZero() || responseTimeout.isNegative()) {
                throw new IllegalArgumentException("response timeout must be positive");
            }
        }
    }

    public record Profile(
            List<AcquirerFixture> acquirers,
            List<MerchantFixture> merchants,
            List<TerminalFixture> terminals,
            List<CredentialRange> credentialRanges) {

        public Profile {
            acquirers = List.copyOf(Objects.requireNonNull(acquirers, "acquirers are required"));
            merchants = List.copyOf(Objects.requireNonNull(merchants, "merchants are required"));
            terminals = List.copyOf(Objects.requireNonNull(terminals, "terminals are required"));
            credentialRanges = List.copyOf(
                    Objects.requireNonNull(credentialRanges, "credential ranges are required"));
            if (acquirers.isEmpty() || merchants.isEmpty() || terminals.isEmpty()
                    || credentialRanges.isEmpty()) {
                throw new IllegalArgumentException("simulator profile fixtures must not be empty");
            }
        }

        public Optional<AcquirerFixture> acquirer(String reference) {
            return acquirers.stream().filter(a -> a.reference().equals(reference)).findFirst();
        }

        public Optional<MerchantFixture> merchant(String reference) {
            return merchants.stream().filter(m -> m.reference().equals(reference)).findFirst();
        }

        public Optional<TerminalFixture> terminal(String reference) {
            return terminals.stream().filter(t -> t.reference().equals(reference)).findFirst();
        }

        /** Registered symbolic fixture only: a known prefix with exactly three digits. */
        public Optional<CredentialRange> credential(String credentialRef) {
            if (credentialRef == null || credentialRef.length() < 4) {
                return Optional.empty();
            }
            return credentialRanges.stream()
                    .filter(range -> credentialRef.startsWith(range.prefix()))
                    .filter(range -> {
                        String suffix = credentialRef.substring(range.prefix().length());
                        return suffix.length() == 3 && suffix.chars().allMatch(Character::isDigit);
                    })
                    .findFirst();
        }
    }

    public record AcquirerFixture(String reference, List<Route> routes) {
        public AcquirerFixture {
            Objects.requireNonNull(reference, "acquirer reference is required");
            routes = List.copyOf(Objects.requireNonNull(routes, "acquirer routes are required"));
        }
    }

    public record MerchantFixture(String reference, String acquirer) {
        public MerchantFixture {
            Objects.requireNonNull(reference, "merchant reference is required");
            Objects.requireNonNull(acquirer, "merchant acquirer is required");
        }
    }

    public record TerminalFixture(String reference, String merchant) {
        public TerminalFixture {
            Objects.requireNonNull(reference, "terminal reference is required");
            Objects.requireNonNull(merchant, "terminal merchant is required");
        }
    }

    public record CredentialRange(String prefix, CredentialDecision decision, Route route) {
        public CredentialRange {
            Objects.requireNonNull(prefix, "credential prefix is required");
            Objects.requireNonNull(decision, "credential decision is required");
            Objects.requireNonNull(route, "credential route is required");
        }
    }
}
