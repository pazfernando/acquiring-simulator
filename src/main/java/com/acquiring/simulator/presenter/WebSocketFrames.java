package com.acquiring.simulator.presenter;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Minimal RFC 6455 server framing for the local presenter transport.
 *
 * <p>It accepts only masked client frames (the RFC requirement for a server) and emits unmasked
 * server frames. Text messages are bounded to 64 KiB, fragmentation is reassembled with the same
 * bound, and control frames are handled locally. No extension is negotiated, so the connection uses
 * the base protocol only.</p>
 */
final class WebSocketFrames {

    static final int OPCODE_CONTINUATION = 0x0;
    static final int OPCODE_TEXT = 0x1;
    static final int OPCODE_BINARY = 0x2;
    static final int OPCODE_CLOSE = 0x8;
    static final int OPCODE_PING = 0x9;
    static final int OPCODE_PONG = 0xA;

    static final int MAX_MESSAGE_BYTES = 64 * 1024;

    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private WebSocketFrames() {
    }

    /** Write the 101 response for a validated handshake key. */
    static void writeHandshake(OutputStream output, String key) throws IOException {
        String accept = acceptKey(key);
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n"
                + "\r\n";
        output.write(response.getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    static String acceptKey(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((key + MAGIC).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-1 unavailable", unavailable);
        }
    }

    /** One decoded data or control message. */
    record Message(int opcode, byte[] payload) {
    }

    /**
     * Read one complete data message or control frame.
     *
     * @throws ProtocolException when the peer violates the framing rules
     */
    static Message read(InputStream input) throws IOException {
        ByteArrayOutputStream assembled = null;
        int assembledOpcode = -1;
        while (true) {
            int first = input.read();
            if (first < 0) {
                throw new EOFException("websocket peer closed");
            }
            int second = input.read();
            if (second < 0) {
                throw new EOFException("websocket peer closed mid-frame");
            }
            boolean fin = (first & 0x80) != 0;
            int opcode = first & 0x0F;
            boolean masked = (second & 0x80) != 0;
            long length = second & 0x7F;
            if ((first & 0x70) != 0) {
                throw new ProtocolException("reserved bits are not negotiated");
            }
            if (!masked) {
                throw new ProtocolException("client frames must be masked");
            }
            if (length == 126) {
                length = ((long) readByte(input) << 8) | readByte(input);
            } else if (length == 127) {
                length = 0;
                for (int i = 0; i < 8; i++) {
                    length = (length << 8) | readByte(input);
                }
            }
            if (length < 0 || length > MAX_MESSAGE_BYTES) {
                throw new ProtocolException("frame exceeds the local bound");
            }
            boolean control = (opcode & 0x08) != 0;
            if (control && (!fin || length > 125)) {
                throw new ProtocolException("invalid control frame");
            }
            byte[] mask = input.readNBytes(4);
            if (mask.length != 4) {
                throw new EOFException("websocket peer closed mid-mask");
            }
            byte[] payload = input.readNBytes((int) length);
            if (payload.length != (int) length) {
                throw new EOFException("websocket peer closed mid-payload");
            }
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }

            if (control) {
                return new Message(opcode, payload);
            }
            if (opcode == OPCODE_CONTINUATION) {
                if (assembled == null) {
                    throw new ProtocolException("continuation without a started message");
                }
                if (assembled.size() + payload.length > MAX_MESSAGE_BYTES) {
                    throw new ProtocolException("fragmented message exceeds the local bound");
                }
                assembled.writeBytes(payload);
                if (fin) {
                    return new Message(assembledOpcode, assembled.toByteArray());
                }
            } else if (opcode == OPCODE_TEXT || opcode == OPCODE_BINARY) {
                if (assembled != null) {
                    throw new ProtocolException("new message while a fragmented message is open");
                }
                if (fin) {
                    return new Message(opcode, payload);
                }
                assembled = new ByteArrayOutputStream(payload.length);
                assembled.writeBytes(payload);
                assembledOpcode = opcode;
            } else {
                throw new ProtocolException("unsupported data opcode");
            }
        }
    }

    static void writeText(OutputStream output, String text) throws IOException {
        writeFrame(output, OPCODE_TEXT, text.getBytes(StandardCharsets.UTF_8));
    }

    static void writePong(OutputStream output, byte[] payload) throws IOException {
        writeFrame(output, OPCODE_PONG, payload);
    }

    static void writeClose(OutputStream output, int statusCode, String reason) throws IOException {
        byte[] text = reason == null ? new byte[0] : reason.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[2 + text.length];
        payload[0] = (byte) ((statusCode >>> 8) & 0xFF);
        payload[1] = (byte) (statusCode & 0xFF);
        System.arraycopy(text, 0, payload, 2, text.length);
        writeFrame(output, OPCODE_CLOSE, payload);
    }

    private static void writeFrame(OutputStream output, int opcode, byte[] payload)
            throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream(payload.length + 10);
        frame.write(0x80 | opcode);
        if (payload.length < 126) {
            frame.write(payload.length);
        } else if (payload.length <= 0xFFFF) {
            frame.write(126);
            frame.write((payload.length >>> 8) & 0xFF);
            frame.write(payload.length & 0xFF);
        } else {
            frame.write(127);
            long length = payload.length;
            for (int shift = 56; shift >= 0; shift -= 8) {
                frame.write((int) ((length >>> shift) & 0xFF));
            }
        }
        frame.writeBytes(payload);
        output.write(frame.toByteArray());
        output.flush();
    }

    private static int readByte(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) {
            throw new EOFException("websocket peer closed mid-length");
        }
        return value;
    }

    /** A deterministic framing violation; the transport answers with a close frame. */
    static final class ProtocolException extends IOException {
        ProtocolException(String message) {
            super(message);
        }
    }
}
