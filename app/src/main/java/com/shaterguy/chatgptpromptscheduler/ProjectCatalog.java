package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Stores only canonical project URLs and bounded user-visible project names. */
final class ProjectCatalog {
    private static final String PREFS = "scheduler_project_catalog_v1";
    private static final String KEY_URLS = "urls";
    private static final String KEY_NAME_PREFIX = "project_name:";
    private static final int MAX_ENTRIES = 50;
    private static final int MAX_DISPLAY_NAME_LENGTH = 120;
    private final SharedPreferences prefs;

    ProjectCatalog(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<ProjectUrlPolicy.ProjectRef> entries() {
        Set<String> raw = prefs.getStringSet(KEY_URLS, Collections.emptySet());
        LinkedHashSet<String> canonical = canonicalize(raw);
        ArrayList<ProjectUrlPolicy.ProjectRef> out = new ArrayList<>();
        for (String value : canonical) {
            ProjectUrlPolicy.ProjectRef ref = ProjectUrlPolicy.parseProject(value);
            if (ref != null && !contains(out, ref.projectId)) out.add(ref);
        }
        if (raw == null || !canonical.equals(raw)) prefs.edit().putStringSet(KEY_URLS, canonical).commit();
        out.sort((a, b) -> displayName(a).compareToIgnoreCase(displayName(b)));
        return out;
    }

    boolean addVisitedProject(String rawUrl, String displayName) {
        ProjectUrlPolicy.ProjectRef ref = ProjectUrlPolicy.parseProject(rawUrl);
        if (ref == null) return false;
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        boolean alreadyPresent = false;
        for (ProjectUrlPolicy.ProjectRef prior : entries()) {
            if (prior.projectId.equals(ref.projectId)) alreadyPresent = true;
            urls.add(prior.canonicalUrl);
        }
        if (!alreadyPresent) urls.add(ref.canonicalUrl);
        while (urls.size() > MAX_ENTRIES) urls.remove(urls.iterator().next());

        String cleanedName = normalizeDisplayName(displayName);
        String priorName = normalizeDisplayName(prefs.getString(nameKey(ref.projectId), ""));
        boolean nameChanged = !cleanedName.isEmpty() && !cleanedName.equals(priorName);
        if (alreadyPresent && !nameChanged) return false;

        SharedPreferences.Editor editor = prefs.edit().putStringSet(KEY_URLS, urls);
        if (nameChanged) editor.putString(nameKey(ref.projectId), cleanedName);
        return editor.commit();
    }

    int clearAll() {
        int count = entries().size();
        if (!prefs.edit().clear().commit()) throw new IllegalStateException("프로젝트 목록을 삭제하지 못했습니다.");
        return count;
    }

    String displayName(ProjectUrlPolicy.ProjectRef ref) {
        if (ref == null) return "프로젝트";
        String stored = normalizeDisplayName(prefs.getString(nameKey(ref.projectId), ""));
        return stored.isEmpty() ? fallbackDisplayName(ref.projectId) : stored;
    }

    static String normalizeDisplayName(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder out = new StringBuilder(Math.min(value.length(), MAX_DISPLAY_NAME_LENGTH));
        boolean pendingSpace = false;
        for (int i = 0; i < value.length() && out.length() < MAX_DISPLAY_NAME_LENGTH; i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c)) { pendingSpace = out.length() > 0; continue; }
            if (pendingSpace && out.length() < MAX_DISPLAY_NAME_LENGTH) out.append(' ');
            pendingSpace = false;
            if (out.length() < MAX_DISPLAY_NAME_LENGTH) out.append(c);
        }
        return out.toString().trim();
    }

    private static LinkedHashSet<String> canonicalize(Set<String> raw) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        if (raw != null) for (String value : raw) {
            ProjectUrlPolicy.ProjectRef ref = ProjectUrlPolicy.parseProject(value);
            if (ref != null) urls.add(ref.canonicalUrl);
        }
        while (urls.size() > MAX_ENTRIES) urls.remove(urls.iterator().next());
        return urls;
    }

    private static String fallbackDisplayName(String projectId) {
        String id = projectId == null ? "" : projectId;
        return "프로젝트 " + (id.length() > 18 ? id.substring(0, 18) : id);
    }

    private static String nameKey(String projectId) { return KEY_NAME_PREFIX + projectId; }
    private static boolean contains(List<ProjectUrlPolicy.ProjectRef> entries, String id) {
        for (ProjectUrlPolicy.ProjectRef entry : entries) if (entry.projectId.equals(id)) return true;
        return false;
    }
}
