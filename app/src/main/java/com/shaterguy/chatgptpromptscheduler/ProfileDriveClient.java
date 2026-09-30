package com.shaterguy.chatgptpromptscheduler;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** GET-only client for two pinned private Google documents. No redirect or token persistence. */
final class ProfileDriveClient {
    static final String DOCUMENT_MIME = "application/vnd.google-apps.document";
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int TIMEOUT_MS = 10_000;

    static final class Failure extends IOException {
        final String code;
        Failure(String code) { super(code); this.code = code; }
    }

    static final class Metadata {
        final String version;
        Metadata(JSONObject value, String expectedId) throws Failure {
            if (!expectedId.equals(value.optString("id")) || !DOCUMENT_MIME.equals(value.optString("mimeType"))
                    || value.optBoolean("trashed", false) || value.optString("version").isEmpty()
                    || value.optString("modifiedTime").isEmpty()) throw new Failure("DOCUMENT_INVALID");
            version = value.optString("version") + "|" + value.optString("modifiedTime");
        }
    }

    Metadata metadata(String token, String id, long deadline) throws Exception {
        requireDocument(id);
        return new Metadata(get("https://www.googleapis.com/drive/v3/files/" + id
                + "?supportsAllDrives=true&fields=id,mimeType,trashed,version,modifiedTime", token, deadline), id);
    }

    String readDocument(String token, String id, long deadline) throws Exception {
        requireDocument(id);
        JSONObject document = get("https://docs.googleapis.com/v1/documents/" + id + "?includeTabsContent=true", token, deadline);
        if (!id.equals(document.optString("documentId"))) throw new Failure("DOCUMENT_INVALID");
        return documentText(document);
    }

    static boolean allowedDocument(String id) {
        return ProfileRegistrySync.CHAT_DOCUMENT_ID.equals(id) || ProfileRegistrySync.WORK_DOCUMENT_ID.equals(id);
    }
    private static void requireDocument(String id) throws Failure { if (!allowedDocument(id)) throw new Failure("DOCUMENT_INVALID"); }

    private JSONObject get(String endpoint, String token, long deadline) throws Exception {
        if (token == null || token.isEmpty()) throw new Failure("AUTH_REQUIRED");
        URL url = new URL(endpoint);
        if (!"https".equals(url.getProtocol()) || !("www.googleapis.com".equals(url.getHost()) || "docs.googleapis.com".equals(url.getHost())))
            throw new Failure("DOCUMENT_INVALID");
        int remaining = (int)Math.min(TIMEOUT_MS, deadline - System.currentTimeMillis());
        if (remaining <= 0) throw new Failure("SYNC_TIMEOUT");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(remaining);
            connection.setReadTimeout(remaining);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status == 401) throw new Failure("AUTH_REQUIRED");
            if (status == 403 || status == 404) throw new Failure("ACCESS_DENIED");
            if (status != 200) throw new Failure(status >= 500 || status == 429 ? "NETWORK" : "DOCUMENT_INVALID");
            if (connection.getContentLengthLong() > MAX_RESPONSE_BYTES) throw new Failure("DOCUMENT_INVALID");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[8192];
                int length;
                while ((length = input.read(bytes)) != -1) {
                    if (System.currentTimeMillis() > deadline) throw new Failure("SYNC_TIMEOUT");
                    if (output.size() + length > MAX_RESPONSE_BYTES) throw new Failure("DOCUMENT_INVALID");
                    output.write(bytes, 0, length);
                }
                return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { connection.disconnect(); }
    }

    static String documentText(JSONObject document) throws Exception {
        JSONObject body;
        JSONArray tabs = document.optJSONArray("tabs");
        if (tabs != null) {
            if (tabs.length() != 1) throw new Failure("DOCUMENT_INVALID");
            JSONObject tab = tabs.getJSONObject(0);
            JSONArray children = tab.optJSONArray("childTabs");
            if (children != null && children.length() > 0) throw new Failure("DOCUMENT_INVALID");
            body = tab.getJSONObject("documentTab").getJSONObject("body");
        } else body = document.getJSONObject("body");
        StringBuilder text = new StringBuilder();
        appendContent(body.optJSONArray("content"), text, 0);
        if (text.toString().getBytes(StandardCharsets.UTF_8).length > RequestProfileRegistry.MAX_PROFILE_FILE_BYTES)
            throw new Failure("DOCUMENT_INVALID");
        return text.toString();
    }

    private static void appendContent(JSONArray content, StringBuilder text, int depth) throws Exception {
        if (depth > 8) throw new Failure("DOCUMENT_INVALID");
        if (content == null) return;
        for (int i = 0; i < content.length(); i++) {
            JSONObject item = content.getJSONObject(i);
            JSONObject paragraph = item.optJSONObject("paragraph");
            if (paragraph != null) {
                JSONArray elements = paragraph.optJSONArray("elements");
                if (elements != null) for (int j = 0; j < elements.length(); j++) {
                    JSONObject run = elements.getJSONObject(j).optJSONObject("textRun");
                    if (run != null) text.append(run.optString("content", ""));
                    if (text.length() > RequestProfileRegistry.MAX_PROFILE_FILE_BYTES) throw new Failure("DOCUMENT_INVALID");
                }
            }
            JSONObject table = item.optJSONObject("table");
            if (table != null) {
                JSONArray rows = table.getJSONArray("tableRows");
                for (int r = 0; r < rows.length(); r++) {
                    JSONArray cells = rows.getJSONObject(r).getJSONArray("tableCells");
                    for (int c = 0; c < cells.length(); c++) appendContent(cells.getJSONObject(c).optJSONArray("content"), text, depth + 1);
                }
            }
        }
    }
}
