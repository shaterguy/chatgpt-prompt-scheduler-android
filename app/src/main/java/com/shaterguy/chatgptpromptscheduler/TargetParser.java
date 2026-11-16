package com.shaterguy.chatgptpromptscheduler;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TargetParser {
    private static final Pattern CONVERSATION_PATH =
            Pattern.compile("(?:/g/[A-Za-z0-9_-]+)?/c/([A-Za-z0-9_-]{1,160})/?");

    private TargetParser() {}

    static String canonicalConversationUrl(String url) {
        if (!isSupported(url)) return null;
        URI uri = URI.create(url);
        if (uri.getRawUserInfo() != null || uri.getPort() != -1) return null;
        Matcher match = CONVERSATION_PATH.matcher(uri.getRawPath());
        return match.matches() ? "https://chatgpt.com/c/" + match.group(1) : null;
    }

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
        for (int i = 0; i < parts.length - 1; i++) {
            if ("g".equals(parts[i]) && !parts[i + 1].isBlank()) {
                String raw = parts[i + 1];
                String canonical = ProjectUrlPolicy.canonicalProjectId(raw);
                return canonical.isEmpty() ? raw : canonical;
            }
        }
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

    public static boolean matchesVerifiedConversation(String targetType, String expectedUrl, String actualUrl) {
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
                    && isServerConversationId(actualConversation);
            case "general" -> actualProject == null && isServerConversationId(actualConversation);
            default -> false;
        };
    }

    private static boolean isServerConversationId(String id) {
        return id != null && !id.regionMatches(true, 0, "local-chatgpt:", 0, "local-chatgpt:".length());
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
