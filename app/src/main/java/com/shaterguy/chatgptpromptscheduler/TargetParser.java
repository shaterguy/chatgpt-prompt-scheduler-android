package com.shaterguy.chatgptpromptscheduler;

import java.net.URI;
import java.net.URISyntaxException;

public final class TargetParser {
    private TargetParser() {}

    public static boolean isSupported(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && ("chatgpt.com".equalsIgnoreCase(host) || "www.chatgpt.com".equalsIgnoreCase(host));
        } catch (URISyntaxException | NullPointerException e) {
            return false;
        }
    }

    public static String conversationId(String url) {
        if (!isSupported(url)) return null;
        String[] parts = URI.create(url).getPath().split("/");
        for (int i = 0; i < parts.length - 1; i++) if ("c".equals(parts[i]) && !parts[i + 1].isBlank()) return parts[i + 1];
        return null;
    }

    public static String projectId(String url) {
        if (!isSupported(url)) return null;
        String[] parts = URI.create(url).getPath().split("/");
        for (int i = 0; i < parts.length - 1; i++) if ("g".equals(parts[i]) && !parts[i + 1].isBlank()) return parts[i + 1];
        return null;
    }

    public static boolean matchesTarget(String targetType, String expectedUrl, String actualUrl) {
        if (!isSupported(expectedUrl) || !isSupported(actualUrl)) return false;
        String expectedProject = projectId(expectedUrl);
        String expectedConversation = conversationId(expectedUrl);
        String actualProject = projectId(actualUrl);
        String actualConversation = conversationId(actualUrl);

        return switch (targetType) {
            case "existing" -> expectedConversation != null
                    && expectedConversation.equals(actualConversation)
                    && (expectedProject == null ? actualProject == null : expectedProject.equals(actualProject));
            case "project" -> expectedProject != null
                    && expectedProject.equals(actualProject)
                    && actualConversation == null;
            case "general" -> actualProject == null
                    && actualConversation == null
                    && isHomePath(actualUrl);
            default -> false;
        };
    }

    /**
     * Startup-only identity check. ChatGPT may normalize a project conversation URL to another
     * SPA path while retaining the same /c/{conversationId}; that is still the same room.
     */
    public static boolean matchesConversationIdentity(String expectedUrl, String actualUrl) {
        if (!isSupported(expectedUrl) || !isSupported(actualUrl)) return false;
        String expectedConversation = conversationId(expectedUrl);
        String actualConversation = conversationId(actualUrl);
        return expectedConversation != null && expectedConversation.equals(actualConversation);
    }

    public static String mismatchDetail(String targetType, String expectedUrl, String actualUrl) {
        return "type=" + targetType + " expected=" + expectedUrl + " actual=" + (actualUrl == null ? "" : actualUrl);
    }

    private static boolean isHomePath(String url) {
        if (!isSupported(url)) return false;
        String path = URI.create(url).getPath();
        return path == null || path.isBlank() || "/".equals(path);
    }
}
