package com.shaterguy.chatgptpromptscheduler;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Process;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Local, same-process WebView DevTools input transport for DEV builds.
 * Does not connect to an external host and never reads cookies or message payloads.
 */
final class DevToolsComposerInput {
    static final class Result {
        final boolean accepted;
        final String code;
        final int pageCount;
        Result(boolean accepted, String code, int pageCount) {
            this.accepted = accepted;
            this.code = code;
            this.pageCount = pageCount;
        }
    }

    private static final String SOCKET_NAME = "webview_devtools_remote_";
    private static final int MAX_HTTP_BYTES = 1_000_000;
    private static final int MAX_WS_BYTES = 1_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private DevToolsComposerInput() {}

    static Result insertText(String runId, String prompt) {
        if (runId == null || runId.isEmpty() || prompt == null || prompt.isEmpty())
            return new Result(false, "EMPTY_INPUT", 0);
        int candidates = 0;
        try {
            JSONArray pages = listPages();
            for (int index = 0; index < pages.length(); index++) {
                JSONObject page = pages.optJSONObject(index);
                if (page == null || !"page".equals(page.optString("type"))) continue;
                String url = page.optString("url", "");
                if (!url.startsWith("https://chatgpt.com/")) continue;
                String endpoint = page.optString("webSocketDebuggerUrl", "");
                if (endpoint.isEmpty()) continue;
                candidates++;
                try (Session session = new Session(endpoint)) {
                    JSONObject params = new JSONObject();
                    params.put("expression", "String(window.__chatgptSchedulerInputTarget||'')");
                    params.put("returnByValue", true);
                    JSONObject evaluated = session.command(1, "Runtime.evaluate", params);
                    JSONObject value = evaluated.optJSONObject("result");
                    JSONObject inner = value == null ? null : value.optJSONObject("result");
                    if (inner == null || !runId.equals(inner.optString("value"))) continue;
                    JSONObject insert = new JSONObject();
                    insert.put("text", prompt);
                    session.command(2, "Input.insertText", insert);
                    return new Result(true, "CDP_INSERT_ACK", candidates);
                }
            }
            return new Result(false, "MATCHING_WEBVIEW_NOT_FOUND", candidates);
        } catch (Exception error) {
            return new Result(false, error.getClass().getSimpleName(), candidates);
        }
    }

    private static LocalSocket connect() throws IOException {
        LocalSocket socket = new LocalSocket();
        try {
            socket.connect(new LocalSocketAddress(
                    SOCKET_NAME + Process.myPid(), LocalSocketAddress.Namespace.ABSTRACT));
            socket.setSoTimeout(5000);
            return socket;
        } catch (IOException | RuntimeException error) {
            try { socket.close(); } catch (IOException ignored) {}
            throw error;
        }
    }

    private static JSONArray listPages() throws Exception {
        try (LocalSocket socket = connect()) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET /json/list HTTP/1.1\r\nHost: localhost\r\n"
                    + "Accept: application/json\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            String headers = readHeaders(in);
            if (!headers.startsWith("HTTP/1.1 200 ")
                    && !headers.startsWith("HTTP/1.0 200 ")) {
                throw new IOException("LIST_HTTP_NOT_OK");
            }
            String lower = headers.toLowerCase(java.util.Locale.ROOT);
            byte[] body;
            if (lower.contains("transfer-encoding: chunked")) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                while (true) {
                    String line = readLine(in);
                    int semi = line.indexOf(';');
                    String chunk = semi < 0 ? line : line.substring(0, semi);
                    int size = Integer.parseInt(chunk.trim(), 16);
                    if (size < 0 || size > MAX_HTTP_BYTES || output.size() + size > MAX_HTTP_BYTES)
                        throw new IOException("LIST_SIZE_LIMIT");
                    if (size == 0) break;
                    output.write(readExact(in, size));
                    if (in.read() != '\r' || in.read() != '\n')
                        throw new IOException("CHUNK_DELIMITER");
                }
                body = output.toByteArray();
            } else {
                int contentLength = -1;
                for (String line : headers.split("\r\n")) {
                    if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))
                        contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                }
                if (contentLength >= 0) {
                    if (contentLength > MAX_HTTP_BYTES) throw new IOException("LIST_SIZE_LIMIT");
                    body = readExact(in, contentLength);
                } else {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] block = new byte[4096];
                    int n;
                    while ((n = in.read(block)) != -1) {
                        output.write(block, 0, n);
                        if (output.size() > MAX_HTTP_BYTES) throw new IOException("LIST_SIZE_LIMIT");
                    }
                    body = output.toByteArray();
                }
            }
            return new JSONArray(new String(body, StandardCharsets.UTF_8));
        }
    }

    private static String readHeaders(InputStream in) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int state = 0;
        while (output.size() < 32768) {
            int b = in.read();
            if (b < 0) throw new IOException("HTTP_HEADER_EOF");
            output.write(b);
            if (state == 0) state = b == '\r' ? 1 : 0;
            else if (state == 1) state = b == '\n' ? 2 : 0;
            else if (state == 2) state = b == '\r' ? 3 : 0;
            else if (state == 3) {
                if (b == '\n') return output.toString(StandardCharsets.US_ASCII.name());
                state = 0;
            }
        }
        throw new IOException("HTTP_HEADER_LIMIT");
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (output.size() < 1024) {
            int b = in.read();
            if (b < 0) throw new IOException("HTTP_LINE_EOF");
            if (b == '\r') {
                if (in.read() != '\n') throw new IOException("HTTP_LINE_DELIMITER");
                return output.toString(StandardCharsets.US_ASCII.name());
            }
            output.write(b);
        }
        throw new IOException("HTTP_LINE_LIMIT");
    }

    private static byte[] readExact(InputStream in, int n) throws IOException {
        if (n < 0 || n > MAX_WS_BYTES) throw new IOException("READ_LIMIT");
        byte[] data = new byte[n];
        int at = 0;
        while (at < n) {
            int count = in.read(data, at, n - at);
            if (count < 0) throw new IOException("SOCKET_EOF");
            at += count;
        }
        return data;
    }

    private static final class Session implements AutoCloseable {
        private final LocalSocket socket;
        private final InputStream in;
        private final OutputStream out;

        Session(String wsUrl) throws Exception {
            URI uri = URI.create(wsUrl);
            String path = uri.getRawPath();
            if (!"ws".equals(uri.getScheme()) || !"localhost".equals(uri.getHost())
                    || path == null || !path.startsWith("/devtools/page/"))
                throw new IOException("UNTRUSTED_DEVTOOLS_ENDPOINT");
            socket = connect();
            in = socket.getInputStream();
            out = socket.getOutputStream();
            byte[] random = new byte[16];
            RANDOM.nextBytes(random);
            String key = Base64.encodeToString(random, Base64.NO_WRAP);
            out.write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\n"
                    + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\nSec-WebSocket-Version: 13\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String headers = readHeaders(in);
            if (!headers.startsWith("HTTP/1.1 101 ")) throw new IOException("WS_HANDSHAKE_REJECTED");
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(
                    (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                            .getBytes(StandardCharsets.US_ASCII));
            String accept = Base64.encodeToString(digest, Base64.NO_WRAP);
            if (!headers.toLowerCase(java.util.Locale.ROOT).contains(
                    ("sec-websocket-accept: " + accept).toLowerCase(java.util.Locale.ROOT)))
                throw new IOException("WS_HANDSHAKE_MISMATCH");
        }

        JSONObject command(int id, String method, JSONObject params) throws Exception {
            JSONObject cmd = new JSONObject();
            cmd.put("id", id);
            cmd.put("method", method);
            cmd.put("params", params);
            writeFrame(1, cmd.toString().getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < 100; i++) {
                String message = readMessage();
                if (message == null) continue;
                JSONObject response = new JSONObject(message);
                if (response.optInt("id", -1) != id) continue;
                if (response.has("error")) throw new IOException("CDP_COMMAND_ERROR");
                if (!response.has("result")) throw new IOException("CDP_RESULT_ABSENT");
                return response;
            }
            throw new IOException("CDP_RESPONSE_LIMIT");
        }

        private void writeFrame(int opcode, byte[] payload) throws IOException {
            if (payload.length > MAX_WS_BYTES) throw new IOException("WS_SIZE_LIMIT");
            ByteArrayOutputStream frame = new ByteArrayOutputStream(payload.length + 20);
            frame.write(0x80 | opcode);
            if (payload.length <= 125) frame.write(0x80 | payload.length);
            else if (payload.length <= 65535) {
                frame.write(0x80 | 126);
                frame.write((payload.length >> 8) & 0xff);
                frame.write(payload.length & 0xff);
            } else {
                frame.write(0x80 | 127);
                for (int shift = 56; shift >= 0; shift -= 8)
                    frame.write((int) (((long) payload.length >> shift) & 0xff));
            }
            byte[] mask = new byte[4];
            RANDOM.nextBytes(mask);
            frame.write(mask);
            for (int i = 0; i < payload.length; i++) frame.write(payload[i] ^ mask[i % 4]);
            out.write(frame.toByteArray());
            out.flush();
        }

        private String readMessage() throws IOException {
            int first = in.read();
            int second = in.read();
            if (first < 0 || second < 0) throw new IOException("WS_EOF");
            int opcode = first & 15;
            long length = second & 127;
            if (length == 126) {
                length = (long) ((in.read() & 255) << 8) | (in.read() & 255);
            } else if (length == 127) {
                length = 0;
                for (int i = 0; i < 8; i++) {
                    int b = in.read();
                    if (b < 0) throw new IOException("WS_LENGTH_EOF");
                    length = (length << 8) | b;
                }
            }
            if (length > MAX_WS_BYTES) throw new IOException("WS_SIZE_LIMIT");
            byte[] mask = (second & 0x80) != 0 ? readExact(in, 4) : null;
            byte[] bytes = readExact(in, (int) length);
            if (mask != null) for (int i = 0; i < bytes.length; i++) bytes[i] ^= mask[i % 4];
            if (opcode == 9) { writeFrame(10, bytes); return null; }
            if (opcode == 8) throw new IOException("WS_CLOSED");
            if (opcode != 1) return null;
            return new String(bytes, StandardCharsets.UTF_8);
        }

        @Override public void close() throws IOException { socket.close(); }
    }
}
