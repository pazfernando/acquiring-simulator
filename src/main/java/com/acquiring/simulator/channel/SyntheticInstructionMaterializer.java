package com.acquiring.simulator.channel;

import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.port.OutboundInstructionMaterializer;
import com.acquiring.simulator.protocol.iso8583.Iso8583AdmissionMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Simulator-local materializer for the exact outbound {@code 0100} instruction of one dispatch
 * attempt.
 *
 * <p>It reuses the approved {@link ServerResolvedInstructionMapper} unchanged: the request and the
 * trusted resolution are built from the durable claim, the mapper packs the canonical body and the
 * ordered request pipeline applies the same schema/domain/freshness/826-byte rules. The only values
 * this class invents are the synthetic registry aliases (DE32/41/42), STAN (DE11), RRN (DE37) and
 * attempt identifier, because the approved durable alias/STAN/RRN allocation (T2) does not exist yet.
 * Those synthetic values are derived deterministically from stable persisted claim values, so a
 * repeated materialization for the same attempt is byte-identical. They are simulator fixture
 * values, never scheme rules or a claimed production allocation.</p>
 *
 * <p>The class does not choose an outcome, authenticate, authorize, claim, dispatch, persist, sign
 * or send. When it is not injected into the dispatch-intent adapter, no original instruction is
 * materialized and the existing unsigned path is unchanged.</p>
 */
public final class SyntheticInstructionMaterializer implements OutboundInstructionMaterializer {

    private static final String CONTRACT_VERSION = "1";
    private static final String OPERATION = "A";
    private static final String CURRENCY = "USD";

    private static final DateTimeFormatter SENT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    @Override
    public byte[] materialize(AuthorizationRecord claim, String dispatchCorrelationId,
            Instant dispatchedAt) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(dispatchCorrelationId, "dispatchCorrelationId");
        Objects.requireNonNull(dispatchedAt, "dispatchedAt");
        if (dispatchCorrelationId.isBlank()) {
            throw new IllegalArgumentException("dispatchCorrelationId is required");
        }

        String authorizationId = claim.id();
        String transactionId = claim.channelPaymentRef();
        String attemptId = "ATT-" + digestHex("attempt|" + authorizationId + "|"
                + dispatchCorrelationId).substring(0, 24);
        String sent = SENT.format(dispatchedAt);

        AuthorizedContext context = new AuthorizedContext(
                claim.acquirerRef(), claim.merchantRef(), claim.terminalRef(),
                Set.of(claim.route()));

        ChannelAuthorizationRequest request = new ChannelAuthorizationRequest(
                CONTRACT_VERSION,
                OPERATION,
                dispatchCorrelationId,
                claim.acquirerRef(),
                claim.merchantRef(),
                claim.terminalRef(),
                transactionId,
                claim.credentialRef(),
                claim.amountMinor(),
                CURRENCY,
                claim.entryMode(),
                claim.cvmIndicator(),
                claim.cryptogramPresent(),
                claim.riskIndicator(),
                null);

        ServerInstructionResolution resolution = new ServerInstructionResolution(
                context,
                authorizationId,
                transactionId,
                attemptId,
                claim.generation(),
                claim.acquirerRef(),
                claim.merchantRef(),
                claim.terminalRef(),
                transactionId,
                acquirerAlias(claim.acquirerRef()),
                merchantAlias(claim.acquirerRef(), claim.merchantRef()),
                terminalAlias(claim.merchantRef(), claim.terminalRef()),
                stan(authorizationId),
                rrn(authorizationId),
                sent,
                claim.credentialRef(),
                claim.entryMode(),
                claim.cvmIndicator(),
                claim.cryptogramPresent(),
                claim.riskIndicator(),
                claim.amountMinor(),
                CURRENCY,
                claim.route());

        ServerResolvedInstruction mapped = ServerResolvedInstructionMapper.map(
                request, resolution, Iso8583AdmissionMode.NEW_FIRST_DISPATCH, dispatchedAt);
        byte[] bytes = mapped.body().getBytes(StandardCharsets.US_ASCII);
        if (bytes.length == 0) {
            throw new IllegalStateException("materialized instruction is empty");
        }
        return bytes;
    }

    /**
     * DE32 is an LL numeric 1–11 alias. The synthetic value is a positive decimal derived from the
     * full acquirer reference, so the same acquirer always maps to the same local alias.
     */
    static String acquirerAlias(String acquirerRef) {
        long value = decimal("de32|" + acquirerRef, 999_999_999L);
        return Long.toString(value == 0 ? 1 : value);
    }

    /** DE42 is exactly 15 uppercase letters/digits, unique within the acquirer. */
    static String merchantAlias(String acquirerRef, String merchantRef) {
        return "MERCH" + pad(decimal("de42|" + acquirerRef + "|" + merchantRef, 9_999_999_999L), 10);
    }

    /** DE41 is exactly 8 uppercase letters/digits, unique within acquirer+merchant. */
    static String terminalAlias(String merchantRef, String terminalRef) {
        return "TERM" + pad(decimal("de41|" + merchantRef + "|" + terminalRef, 9_999L), 4);
    }

    /** DE11 is a six-digit STAN; the synthetic value is stable for the authorization. */
    static String stan(String authorizationId) {
        return pad(decimal("de11|" + authorizationId, 999_999L), 6);
    }

    /** DE37 is exactly 12 uppercase letters/digits. */
    static String rrn(String authorizationId) {
        return "RRN" + pad(decimal("de37|" + authorizationId, 999_999_999L), 9);
    }

    private static long decimal(String seed, long exclusiveUpperBound) {
        byte[] digest = digest(seed);
        long value = 0L;
        // Use only the low 63 bits so the remainder is non-negative for every digest.
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (digest[i] & 0xffL);
        }
        return Long.remainderUnsigned(value, exclusiveUpperBound + 1);
    }

    private static String pad(long value, int width) {
        return String.format(Locale.ROOT, "%0" + width + "d", value);
    }

    private static String digestHex(String seed) {
        return HexFormat.of().formatHex(digest(seed));
    }

    private static byte[] digest(String seed) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
