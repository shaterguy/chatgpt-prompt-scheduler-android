package com.shaterguy.chatgptpromptscheduler;

final class ProjectDirectoryNavigationScript {
    static final String DIRECTORY_URL = "https://chatgpt.com/projects";

    private ProjectDirectoryNavigationScript() {}

    static String entryUrl(Schedule schedule) {
        if (schedule != null && "project".equals(schedule.targetType)) return schedule.targetUrl;
        return schedule == null ? "https://chatgpt.com/" : schedule.targetUrl;
    }

    static boolean needsDirectoryStep(Schedule schedule, String actualUrl) {
        return schedule != null && "project".equals(schedule.targetType)
                && actualUrl != null && actualUrl.startsWith(DIRECTORY_URL);
    }

    static String build(String projectTargetUrl, int candidateIndex) {
        ProjectUrlPolicy.ProjectRef ref = ProjectUrlPolicy.parseProject(projectTargetUrl);
        String expectedId = jsQuote(ref == null ? "" : ref.projectId);
        int ordinal = Math.max(0, candidateIndex);
        return "(()=>{" +
                "const result=(status,detail='',diagnostics={})=>JSON.stringify({status,detail,url:location.href,diagnostics});" +
                "const visible=e=>!!e&&e.isConnected&&e.offsetParent!==null;" +
                "if(location.protocol!=='https:'||location.hostname!=='chatgpt.com')return result('TARGET_CONTEXT_MISMATCH','호스트 불일치');" +
                "if(/^\\/auth\\/(?:login|signup|signin)(?:\\/|$)/.test(location.pathname))return result('AUTH_REQUIRED','로그인이 필요합니다.');" +
                "if(location.pathname!=='/projects')return result('NAVIGATE_DIRECTORY','프로젝트 목록 화면으로 이동합니다.');" +
                "const expected=" + expectedId + ";" +
                "if(!expected)return result('TARGET_CONTEXT_MISMATCH','프로젝트 ID가 없습니다.');" +
                "const normalize=id=>{const end=36;if(id.length>end+1&&id.startsWith('g-p-')&&id.charAt(end)==='-'&&/^[0-9a-fA-F]{32}$/.test(id.slice(4,end)))return id.slice(0,end);return id;};" +
                "const matches=[...document.querySelectorAll('a[href]')].filter(visible).filter(a=>{try{const url=new URL(a.getAttribute('href'),location.origin);const p=url.pathname.split('/');return url.origin===location.origin&&p[1]==='g'&&normalize(p[2]||'')===expected&&p[3]==='project';}catch(e){return false;}});" +
                "if(!matches.length)return result('RETRY','프로젝트 링크 대기',{expected,links:document.querySelectorAll('a[href]').length,matchingLinks:0});" +
                "const ordinal=" + ordinal + ";" +
                "if(ordinal>=matches.length)return result('PROJECT_NOT_FOUND','프로젝트 링크 후보를 모두 확인했습니다.',{expected,matchingLinks:matches.length,ordinal});" +
                "const chosen=matches[ordinal];chosen.click();" +
                "return result('PROJECT_ROW_CLICKED','프로젝트 링크를 클릭했습니다.',{expected,matchingLinks:matches.length,ordinal});" +
                "})()";
    }

    private static String jsQuote(String value) {
        String safe = value == null ? "" : value;
        StringBuilder out = new StringBuilder(safe.length() + 16).append('"');
        for (int i = 0; i < safe.length(); i++) {
            char c = safe.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                default:
                    if (c < 0x20 || c == '\u2028' || c == '\u2029') out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
