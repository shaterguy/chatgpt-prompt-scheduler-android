package com.shaterguy.chatgptpromptscheduler;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ProfileDriveClientTest {
    @Test public void extractsOneDocumentTabInParagraphOrder() throws Exception {
        JSONObject body = new JSONObject().put("content", new JSONArray().put(paragraph("{\"profiles\":" )).put(paragraph("[]}\n")));
        JSONObject tab = new JSONObject().put("documentTab",new JSONObject().put("body",body));
        JSONObject doc = new JSONObject().put("tabs",new JSONArray().put(tab));
        assertEquals("{\"profiles\":[]}\n", ProfileDriveClient.documentText(doc));
    }

    @Test public void rejectsAmbiguousTabsAndOversizedText() throws Exception {
        JSONObject tab = new JSONObject().put("documentTab",new JSONObject().put("body",new JSONObject().put("content",new JSONArray().put(paragraph("{}")))));
        assertThrows(Exception.class, () -> ProfileDriveClient.documentText(new JSONObject().put("tabs",new JSONArray().put(tab).put(tab))));
        JSONObject huge = new JSONObject().put("body",new JSONObject().put("content",new JSONArray().put(paragraph("x".repeat(RequestProfileRegistry.MAX_PROFILE_FILE_BYTES+1)))));
        assertThrows(Exception.class, () -> ProfileDriveClient.documentText(huge));
    }

    @Test public void onlyCanonicalDocumentIdsAreAccepted() {
        assertTrue(ProfileDriveClient.allowedDocument(ProfileRegistrySync.CHAT_DOCUMENT_ID));
        assertTrue(ProfileDriveClient.allowedDocument(ProfileRegistrySync.WORK_DOCUMENT_ID));
        assertFalse(ProfileDriveClient.allowedDocument("arbitrary-file"));
        assertFalse(ProfileDriveClient.allowedDocument("https://attacker.example"));
    }

    private static JSONObject paragraph(String text) throws Exception {
        return new JSONObject().put("paragraph",new JSONObject().put("elements",new JSONArray().put(new JSONObject().put("textRun",new JSONObject().put("content",text)))));
    }
}
