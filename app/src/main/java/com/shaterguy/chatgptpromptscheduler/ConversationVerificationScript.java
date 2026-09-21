package com.shaterguy.chatgptpromptscheduler;

public final class ConversationVerificationScript {
    private ConversationVerificationScript() {}

    public static String build(Schedule schedule, String stampedPrompt) {
        String tail = stampedPrompt.length() > 120 ? stampedPrompt.substring(0, 120) : stampedPrompt;
        String type = jsQuote(schedule.targetType);
        String expectedUrl = jsQuote(schedule.targetUrl);
        String expectedProject = jsQuote(valueOrEmpty(TargetParser.projectId(schedule.targetUrl)));
        String expectedConversation = jsQuote(valueOrEmpty(TargetParser.conversationId(schedule.targetUrl)));
        return "(() => {" +
                "const result=(status,detail='',diagnostics={})=>JSON.stringify({status,detail,url:location.href,diagnostics});" +
                "if(location.hostname!=='chatgpt.com'&&location.hostname!=='www.chatgpt.com')return result('TARGET_CONTEXT_MISMATCH','호스트 불일치 actual='+location.href);" +
                "const norm=s=>String(s??'').replace(/[\\u200B-\\u200D\\uFEFF]/g,'').replace(/\\u00a0/g,' ').replace(/\\r\\n?/g,'\\n').replace(/[ \\t]+/g,' ').trim();" +
                "const canonical=s=>norm(s).replace(/ *\\n+ */g,'\\n');" +
                "const expected=norm(" + jsQuote(tail) + ");" +
                "const users=[...document.querySelectorAll('[data-message-author-role=\"user\"],article[data-turn=\"user\"]')];" +
                "const expectedCanonical=canonical(expected),userTexts=users.map(e=>canonical(e.innerText||e.textContent));" +
                "const occurrences=(text,needle)=>{if(!needle)return 0;let count=0,index=0;while((index=text.indexOf(needle,index))>=0){count++;index+=needle.length;}return count;};" +
                "const matchCounts=userTexts.map(text=>occurrences(text,expectedCanonical));" +
                "const promptAlreadyPresent=matchCounts.some(count=>count===1);" +
                "const expectedType=" + type + ",expectedUrl=" + expectedUrl + ",expectedProject=" + expectedProject + ",expectedConversation=" + expectedConversation + ";" +
                "const parts=location.pathname.split('/').filter(Boolean);" +
                "const segmentAfter=k=>{const i=parts.indexOf(k);return i>=0&&i+1<parts.length?parts[i+1]:'';};" +
                "const canonicalProject=value=>{const prefix='g-p-',tokenLength=32,end=prefix.length+tokenLength;if(value.length>160||!value.startsWith(prefix)||value.length<=end+1||value.charAt(end)!=='-')return value;const token=value.slice(prefix.length,end),slug=value.slice(end+1);return /^[0-9a-fA-F]{32}$/.test(token)&&/^[A-Za-z0-9_-]+$/.test(slug)?value.slice(0,end):value;};" +
                "const rawActualProject=segmentAfter('g'),actualProject=canonicalProject(rawActualProject),actualConversation=segmentAfter('c');" +
                "const homePath=location.pathname==='/'||location.pathname==='';" +
                "const routeDiagnostics={expectedType,expectedProject,expectedConversation,rawActualProject,actualProject,actualConversation,userMessages:users.length,promptAlreadyPresent};" +
                "if(expectedType==='existing'){if(!expectedConversation||actualConversation!==expectedConversation||(expectedProject?actualProject!==expectedProject:!!actualProject))return result('TARGET_CONTEXT_MISMATCH','expected='+expectedUrl+' actual='+location.href,routeDiagnostics);}" +
                "else if(expectedType==='project'){if(!expectedProject||actualProject!==expectedProject)return result('TARGET_CONTEXT_MISMATCH','expected='+expectedUrl+' actual='+location.href,routeDiagnostics);if(!actualConversation)return result('RETRY','실제 대화 주소 생성 대기',routeDiagnostics);}" +
                "else if(expectedType==='general'){if(actualProject)return result('TARGET_CONTEXT_MISMATCH','expected='+expectedUrl+' actual='+location.href,routeDiagnostics);if(!actualConversation){if(homePath)return result('RETRY','실제 대화 주소 생성 대기',routeDiagnostics);return result('TARGET_CONTEXT_MISMATCH','expected='+expectedUrl+' actual='+location.href,routeDiagnostics);}}" +
                "else return result('TARGET_CONTEXT_MISMATCH','지원하지 않는 대상 유형',routeDiagnostics);" +
                "return promptAlreadyPresent?result('VERIFIED','프롬프트 전송과 실제 대화 주소 확인',{...routeDiagnostics,userMessages:users.length,matchCounts,maxMatchCount:Math.max(0,...matchCounts)}):result('RETRY','전송된 사용자 메시지 대기',{...routeDiagnostics,userMessages:users.length,matchCounts,maxMatchCount:Math.max(0,...matchCounts),readyState:document.readyState});" +
                "})()";
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String jsQuote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
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
