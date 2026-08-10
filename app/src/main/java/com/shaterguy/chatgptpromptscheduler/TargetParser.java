package com.shaterguy.chatgptpromptscheduler;

import java.net.URI;
import java.net.URISyntaxException;

public final class TargetParser {
    public enum ConversationTargetState { MATCH, TRANSIENT, DIFFERENT }

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
                    && expectedConversation.equals(actualConversation);
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
     * Conversation IDs are the canonical room identity. A temporary home/project-root/about:blank
     * route is recoverable; only a concrete different /c/{id} proves a room change.
     */
    public static ConversationTargetState classifyConversationTarget(String expectedUrl, String actualUrl) {
        String expectedConversation = conversationId(expectedUrl);
        if (expectedConversation == null) return ConversationTargetState.DIFFERENT;
        if (actualUrl == null || actualUrl.isBlank() || "about:blank".equalsIgnoreCase(actualUrl))
            return ConversationTargetState.TRANSIENT;
        if (!isSupported(actualUrl)) return ConversationTargetState.DIFFERENT;
        String actualConversation = conversationId(actualUrl);
        if (expectedConversation.equals(actualConversation)) return ConversationTargetState.MATCH;
        if (actualConversation != null) return ConversationTargetState.DIFFERENT;
        // A supported route without a concrete conversation ID is never proof
        // that another conversation was selected. It may be the home screen,
        // project root, a project new-chat surface, or a transient SPA route.
        // Only an observed /c/{different-id} is a target-change proof.
        return ConversationTargetState.TRANSIENT;
    }

    public static boolean isTransientConversationRoute(String expectedUrl, String actualUrl) {
        return classifyConversationTarget(expectedUrl, actualUrl) == ConversationTargetState.TRANSIENT;
    }

    /**
     * Startup-only identity check. ChatGPT may normalize a project conversation URL to another
     * SPA path while retaining the same /c/{conversationId}; that is still the same room.
     */
    public static boolean matchesConversationIdentity(String expectedUrl, String actualUrl) {
        return classifyConversationTarget(expectedUrl, actualUrl) == ConversationTargetState.MATCH;
    }

    /** Accepts only the configured project and any conversation created inside that project. */
    public static boolean matchesProjectIdentity(String expectedProjectUrl, String actualUrl) {
        if (!isSupported(expectedProjectUrl) || !isSupported(actualUrl)) return false;
        String expectedProject = projectId(expectedProjectUrl);
        String actualProject = projectId(actualUrl);
        return expectedProject != null && expectedProject.equals(actualProject);
    }

    public static boolean isProjectHome(String url) {
        if (!isSupported(url) || projectId(url) == null || conversationId(url) != null) return false;
        String path = URI.create(url).getPath();
        return path != null && path.matches("/g/[^/]+/?");
    }

    /** A project-owned new-chat surface has the project identity but no conversation ID. */
    public static boolean isProjectNewChatSurface(String expectedProjectUrl, String actualUrl) {
        if (!isSupported(expectedProjectUrl) || !isSupported(actualUrl)) return false;
        String expectedProject = projectId(expectedProjectUrl);
        return expectedProject != null && expectedProject.equals(projectId(actualUrl))
                && conversationId(actualUrl) == null && !isProjectHome(actualUrl);
    }

    /** A project bootstrap route is recoverable while it has no concrete conversation ID. */
    public static boolean isTransientProjectRoute(String expectedProjectUrl, String actualUrl) {
        if (actualUrl == null || actualUrl.isBlank() || "about:blank".equalsIgnoreCase(actualUrl)) return true;
        if (!isSupported(expectedProjectUrl) || !isSupported(actualUrl)) return false;
        String expectedProject = projectId(expectedProjectUrl);
        return expectedProject != null && expectedProject.equals(projectId(actualUrl))
                && conversationId(actualUrl) == null;
    }

    /** ChatGPT's global home and new-chat routes contain neither project nor conversation IDs. */
    public static boolean isGlobalNewChatSurface(String url) {
        if (!isSupported(url) || projectId(url) != null || conversationId(url) != null) return false;
        return isHomePath(url);
    }

    public static boolean isProjectConversation(String projectUrl, String conversationUrl) {
        return matchesProjectIdentity(projectUrl, conversationUrl) && conversationId(conversationUrl) != null;
    }

    public static String mismatchDetail(String targetType, String expectedUrl, String actualUrl) {
        return "type=" + targetType
                + " expected_project=" + value(projectId(expectedUrl))
                + " expected_conversation=" + value(conversationId(expectedUrl))
                + " actual_project=" + value(projectId(actualUrl))
                + " actual_conversation=" + value(conversationId(actualUrl));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static boolean isHomePath(String url) {
        if (!isSupported(url)) return false;
        String path = URI.create(url).getPath();
        return path == null || path.isBlank() || "/".equals(path)
                || "/new-chat".equals(path) || "/new-chat/".equals(path);
    }
}
