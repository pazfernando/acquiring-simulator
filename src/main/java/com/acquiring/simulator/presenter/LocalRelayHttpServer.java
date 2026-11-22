package com.acquiring.simulator.presenter;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal loopback HTTP/1.1 listener that can also upgrade one request to a raw WebSocket.
 *
 * <p>The JDK {@code com.sun.net.httpserver} implementation forces the request stream closed when a
 * handler answers {@code 101}, so it cannot serve a same-origin WebSocket. This listener keeps the
 * relay's existing {@link HttpExchange}-based routing by adapting each parsed request to that API,
 * and hands the original socket streams to the upgrade handler when the request asks for a
 * WebSocket.</p>
 *
 * <p>It is deliberately small and local-only: one request per connection, no keep-alive, no chunked
 * request bodies, no TLS and no proxying. Every response carries {@code Connection: close}. Request
 * headers and bodies are bounded so a local browser cannot exhaust the relay.</p>
 */
final class LocalRelayHttpServer implements AutoCloseable {

    private static final int MAX_REQUEST_LINE = 8 * 1024;
    private static final int MAX_HEADER_BYTES = 32 * 1024;
    private static final int MAX_HEADER_COUNT = 100;
    private static final long MAX_BODY_BYTES = 1L * 1024 * 1024;

    /** Normal HTTP routing, reusing the existing {@link HttpExchange} handler. */
    interface Dispatcher {
        void handle(HttpExchange exchange) throws IOException;
    }

    /** A WebSocket upgrade over the untouched socket streams. */
    interface UpgradeHandler {
        boolean upgrade(UpgradeRequest request) throws IOException;
    }

    /** Raw inputs of one upgrade attempt; the handler owns the streams while it runs. */
    record UpgradeRequest(String method, String path, Headers headers, InputStream input,
            OutputStream output, InetSocketAddress remoteAddress) {
    }

    private final ServerSocket serverSocket;
    private final Dispatcher dispatcher;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile UpgradeHandler upgradeHandler;
    private final Set<Socket> active = ConcurrentHashMap.newKeySet();
    private final Thread acceptThread;
    private final AtomicBoolean started = new AtomicBoolean();

    LocalRelayHttpServer(InetSocketAddress address, Dispatcher dispatcher) throws IOException {
        this.dispatcher = dispatcher;
        this.serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(address, 50);
        this.acceptThread = Thread.ofPlatform().name("presenter-relay-accept")
                .unstarted(this::acceptLoop);
    }

    /** Begin accepting connections; binding already happened in the constructor. */
    void start() {
        if (started.compareAndSet(false, true)) {
            acceptThread.start();
        }
    }

    void setUpgradeHandler(UpgradeHandler handler) {
        this.upgradeHandler = handler;
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    InetAddress address() {
        return serverSocket.getInetAddress();
    }

    private void acceptLoop() {
        while (!closed.get()) {
            try {
                Socket socket = serverSocket.accept();
                active.add(socket);
                Thread.ofVirtual().name("presenter-relay-connection").start(() -> serve(socket));
            } catch (IOException stopped) {
                if (!closed.get()) {
                    // A failed accept is not recoverable for this local listener; stop serving.
                    close();
                }
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            socket.setSoTimeout(30_000);
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream(), 8 * 1024);
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream(), 8 * 1024);

            String requestLine = readLine(input, MAX_REQUEST_LINE);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length != 3 || !parts[2].startsWith("HTTP/")) {
                writeSimple(output, 400, "Bad Request");
                return;
            }
            String method = parts[0];
            String target = parts[1];

            Headers headers = new Headers();
            int headerBytes = 0;
            int headerCount = 0;
            String line;
            while ((line = readLine(input, MAX_REQUEST_LINE)) != null && !line.isEmpty()) {
                headerBytes += line.length() + 2;
                headerCount += 1;
                if (headerBytes > MAX_HEADER_BYTES || headerCount > MAX_HEADER_COUNT) {
                    writeSimple(output, 431, "Request Header Fields Too Large");
                    return;
                }
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    writeSimple(output, 400, "Bad Request");
                    return;
                }
                headers.add(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }

            URI uri;
            try {
                uri = new URI(target);
            } catch (URISyntaxException invalid) {
                writeSimple(output, 400, "Bad Request");
                return;
            }
            if (uri.getPath() == null || !uri.getPath().startsWith("/")) {
                writeSimple(output, 400, "Bad Request");
                return;
            }

            if (isWebSocketUpgrade(headers)) {
                UpgradeHandler handler = upgradeHandler;
                if (handler == null) {
                    writeSimple(output, 501, "Not Implemented");
                    return;
                }
                upgrade(handler, method, uri, headers, input, output, socket);
                return;
            }

            long contentLength = contentLength(headers);
            if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
                writeSimple(output, contentLength < 0 ? 400 : 413, "Bad Request");
                return;
            }
            String expect = headers.getFirst("Expect");
            if (expect != null && expect.equalsIgnoreCase("100-continue")) {
                output.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                output.flush();
            }

            Exchange exchange = new Exchange(socket, method, uri, headers, output,
                    new BoundedInputStream(input, contentLength));
            try {
                dispatcher.handle(exchange);
            } catch (RuntimeException failure) {
                exchange.failInternal();
            } finally {
                exchange.finish();
            }
        } catch (IOException ignored) {
            // The peer went away; nothing is retried and nothing is invented.
        } finally {
            active.remove(socket);
        }
    }

    private void upgrade(UpgradeHandler handler, String method, URI uri, Headers headers,
            InputStream input, OutputStream output, Socket socket) throws IOException {
        socket.setSoTimeout(0);
        UpgradeRequest request = new UpgradeRequest(method, uri.getPath(), headers, input, output,
                (InetSocketAddress) socket.getRemoteSocketAddress());
        if (!handler.upgrade(request)) {
            writeSimple(output, 404, "Not Found");
        }
    }

    private static boolean isWebSocketUpgrade(Headers headers) {
        String upgrade = headers.getFirst("Upgrade");
        String connection = headers.getFirst("Connection");
        if (upgrade == null || !upgrade.trim().equalsIgnoreCase("websocket") || connection == null) {
            return false;
        }
        for (String token : connection.split(",")) {
            if (token.trim().equalsIgnoreCase("upgrade")) {
                return true;
            }
        }
        return false;
    }

    private static long contentLength(Headers headers) {
        String value = headers.getFirst("Content-Length");
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            long length = Long.parseLong(value.trim());
            return length < 0 ? -1L : length;
        } catch (NumberFormatException invalid) {
            return -1L;
        }
    }

    private static String readLine(InputStream input, int limit) throws IOException {
        StringBuilder line = new StringBuilder(128);
        int current;
        while ((current = input.read()) != -1) {
            if (current == '\n') {
                int length = line.length();
                if (length > 0 && line.charAt(length - 1) == '\r') {
                    line.setLength(length - 1);
                }
                return line.toString();
            }
            if (line.length() >= limit) {
                throw new IOException("request line or header exceeds the local limit");
            }
            line.append((char) current);
        }
        return line.isEmpty() ? null : line.toString();
    }

    private static void writeSimple(OutputStream output, int status, String reason)
            throws IOException {
        String response = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Length: 0\r\nConnection: close\r\n\r\n";
        output.write(response.getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // Already closed.
        }
        for (Socket socket : new ArrayList<>(active)) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Best effort.
            }
        }
        active.clear();
        acceptThread.interrupt();
    }

    /** Request-scoped adapter so existing handlers keep using the {@link HttpExchange} API. */
    private static final class Exchange extends HttpExchange {
        private final Socket socket;
        private final String method;
        private final URI uri;
        private final Headers requestHeaders;
        private final Headers responseHeaders = new Headers();
        private final OutputStream socketOutput;
        private final InputStream requestBody;
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private boolean headersSent;
        private int responseCode = -1;
        private OutputStream responseBody = OutputStream.nullOutputStream();
        private long remaining;

        private Exchange(Socket socket, String method, URI uri, Headers requestHeaders,
                OutputStream socketOutput, InputStream requestBody) {
            this.socket = socket;
            this.method = method;
            this.uri = uri;
            this.requestHeaders = requestHeaders;
            this.socketOutput = socketOutput;
            this.requestBody = requestBody;
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return uri;
        }

        @Override
        public String getRequestMethod() {
            return method;
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }

        @Override
        public InputStream getRequestBody() {
            return requestBody;
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int code, long responseLength) throws IOException {
            if (headersSent) {
                throw new IOException("response headers already sent");
            }
            headersSent = true;
            responseCode = code;
            StringBuilder head = new StringBuilder(256);
            head.append("HTTP/1.1 ").append(code).append(' ')
                    .append(reason(code)).append("\r\n");
            boolean lengthDeclared = false;
            for (Map.Entry<String, List<String>> header : responseHeaders.entrySet()) {
                String name = header.getKey();
                if (name == null) {
                    continue;
                }
                if (name.equalsIgnoreCase("Content-Length")) {
                    lengthDeclared = true;
                }
                for (String value : header.getValue()) {
                    head.append(name).append(": ").append(value).append("\r\n");
                }
            }
            if (responseLength >= 0 && !lengthDeclared) {
                head.append("Content-Length: ").append(responseLength).append("\r\n");
            }
            if (!responseHeaders.containsKey("Connection")) {
                head.append("Connection: close\r\n");
            }
            head.append("\r\n");
            socketOutput.write(head.toString().getBytes(StandardCharsets.US_ASCII));
            socketOutput.flush();
            if (responseLength > 0) {
                remaining = responseLength;
                responseBody = new FixedLengthOutputStream(socketOutput, responseLength);
            } else {
                remaining = 0;
                responseBody = OutputStream.nullOutputStream();
            }
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return (InetSocketAddress) socket.getRemoteSocketAddress();
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return (InetSocketAddress) socket.getLocalSocketAddress();
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }

        @Override
        public void setStreams(InputStream input, OutputStream output) {
            // The transport owns the streams; handlers may not replace them.
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }

        private void failInternal() {
            if (!headersSent) {
                try {
                    writeSimple(socketOutput, 500, "Internal Server Error");
                    headersSent = true;
                    responseCode = 500;
                } catch (IOException ignored) {
                    // The peer is gone.
                }
            }
        }

        private void finish() {
            try {
                responseBody.flush();
                socketOutput.flush();
            } catch (IOException ignored) {
                // The peer went away; no retry.
            }
        }

        private static String reason(int code) {
            return switch (code) {
                case 200 -> "OK";
                case 204 -> "No Content";
                case 400 -> "Bad Request";
                case 401 -> "Unauthorized";
                case 403 -> "Forbidden";
                case 404 -> "Not Found";
                case 405 -> "Method Not Allowed";
                case 409 -> "Conflict";
                case 410 -> "Gone";
                case 413 -> "Payload Too Large";
                case 421 -> "Misdirected Request";
                case 426 -> "Upgrade Required";
                case 500 -> "Internal Server Error";
                case 501 -> "Not Implemented";
                case 503 -> "Service Unavailable";
                default -> "";
            };
        }
    }

    /** Request body bound to the declared length; reads never run into the next request. */
    private static final class BoundedInputStream extends FilterInputStream {
        private long remaining;

        private BoundedInputStream(InputStream input, long remaining) {
            super(input);
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = super.read();
            if (value >= 0) {
                remaining -= 1;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int requested = (int) Math.min(length, remaining);
            int read = super.read(buffer, offset, requested);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public long skip(long count) throws IOException {
            long skipped = super.skip(Math.min(count, remaining));
            remaining -= skipped;
            return skipped;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(super.available(), remaining);
        }

        @Override
        public void close() {
            // The connection owns the socket input; a handler closing the request body must not
            // close the socket before the response is written.
        }
    }

    /** Output stream that stops the handler from overrunning the declared response length. */
    private static final class FixedLengthOutputStream extends OutputStream {
        private final OutputStream output;
        private long remaining;

        private FixedLengthOutputStream(OutputStream output, long length) {
            this.output = output;
            this.remaining = length;
        }

        @Override
        public void write(int value) throws IOException {
            if (remaining <= 0) {
                throw new IOException("response body exceeds the declared length");
            }
            output.write(value);
            remaining -= 1;
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            if (length > remaining) {
                throw new IOException("response body exceeds the declared length");
            }
            output.write(buffer, offset, length);
            remaining -= length;
        }

        @Override
        public void flush() throws IOException {
            output.flush();
        }

        @Override
        public void close() throws IOException {
            output.flush();
        }
    }
}
