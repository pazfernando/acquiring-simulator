package com.acquiring.simulator.console.jwt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Retrieves the issuer's JWKS over HTTPS.
 *
 * <p>This is the deployment key source. It is composed from {@link TrustedIssuerConfiguration
 * #jwksUri()} and performs no request until the first verification needs keys. {@link #current()}
 * returns the last successfully loaded set; {@link #refresh()} always makes a fresh request, which
 * is how the decoder discovers a rotated key id.</p>
 *
 * <p>Fail-closed properties: redirects are not followed (a redirect cannot move trust to another
 * host), a non-200 response is a failure, and the response body is bounded. The request carries no
 * bearer token and no credential, and no response body or URL is logged or rendered.</p>
 *
 * <p><strong>Not exercised in this slice:</strong> no test or evidence run contacts a network or a
 * real issuer. Unit tests inject a {@link JwksSource} double instead. This class exists so the
 * deployed composition has a concrete, configuration-driven source; it is not evidence that a
 * live Keycloak JWKS was fetched.</p>
 */
public final class HttpJwksSource implements JwksSource {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_DOCUMENT_BYTES = 64 * 1024;

    private final URI jwksUri;
    private final HttpClient client;
    private volatile JsonWebKeySet cached;

    public HttpJwksSource(URI jwksUri) {
        this.jwksUri = Objects.requireNonNull(jwksUri, "jwksUri");
        this.client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Build the source for one configured token class. */
    public static HttpJwksSource forConfiguration(TrustedIssuerConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return new HttpJwksSource(configuration.jwksUri());
    }

    @Override
    public JsonWebKeySet current() {
        JsonWebKeySet present = cached;
        return present != null ? present : refresh();
    }

    @Override
    public synchronized JsonWebKeySet refresh() {
        HttpRequest request = HttpRequest.newBuilder(jwksUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JwksSourceException("jwks request was interrupted", interrupted);
        } catch (IOException failure) {
            throw new JwksSourceException("jwks request failed", failure);
        }
        if (response.statusCode() != 200) {
            throw new JwksSourceException("jwks endpoint refused the request");
        }
        byte[] document;
        try (InputStream body = response.body()) {
            document = readAtMost(body, MAX_DOCUMENT_BYTES);
        } catch (IOException failure) {
            throw new JwksSourceException("jwks response could not be read", failure);
        }
        JsonWebKeySet parsed = JwksParser.parse(new String(document, StandardCharsets.UTF_8));
        cached = parsed;
        return parsed;
    }

    private static byte[] readAtMost(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[4096];
        int total = 0;
        while (total < limit) {
            int read = input.read(buffer, 0, Math.min(buffer.length, limit - total));
            if (read < 0) {
                break;
            }
            out.write(buffer, 0, read);
            total += read;
        }
        if (total == limit) {
            throw new JwksSourceException("jwks document exceeds the size limit");
        }
        return out.toByteArray();
    }
}
