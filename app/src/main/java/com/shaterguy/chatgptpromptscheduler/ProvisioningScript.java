package com.shaterguy.chatgptpromptscheduler;

/** Fail-closed scripts for app-owned project conversation creation. */
public final class ProvisioningScript {
    private ProvisioningScript() {}

    public static String prepare(String side, String projectUrl, String prompt, String jobId,
                                 String workModel, String reasoningEffort) {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = projectUrl;
        schedule.experience = OrchestrationStore.SIDE_WORK.equals(side) ? "work" : "chat";
        schedule.workModel = workModel;
        schedule.reasoningEffort = reasoningEffort;
        String expected = AutomationScript.jsQuote(prompt);
        String project = AutomationScript.jsQuote(TargetParser.projectId(projectUrl));
        String run = AutomationScript.jsQuote(jobId + ":" + side);
        return "(() => {" + base(expected, project)
                + "if(actualConversation&&promptAlreadyPresent)return result('CONFIRMED','이미 제출된 첫 요청 확인',{...routeDiagnostics});"
                + "if(actualConversation)return result('EXISTING_CONVERSATION','새 대화 대신 기존 대화가 열렸습니다.',routeDiagnostics);"
                + AutomationScript.preferenceScript(schedule, run)
                + composerLookup()
                + "if(!composer)return result('RETRY','입력창 대기',{...routeDiagnostics,mode:modeDiagnostics,model:modelDiagnostics,reasoning:reasoningDiagnostics});"
                + composerFunctions()
                + "if(same()){const send=findSend();if(!send)return result('RETRY','전송 버튼 대기',routeDiagnostics);if(send.disabled||send.getAttribute('aria-disabled')==='true')return result('RETRY','전송 버튼 활성화 대기',routeDiagnostics);return result('READY','첫 요청과 설정 확인 완료',{...routeDiagnostics,mode:modeDiagnostics,model:modelDiagnostics,reasoning:reasoningDiagnostics});}"
                + inputPrompt()
                + "return result('RETRY',same()?'입력 반영 확인 대기':'요구사항 입력 미반영',{...routeDiagnostics,mode:modeDiagnostics,model:modelDiagnostics,reasoning:reasoningDiagnostics});"
                + "})()";
    }

    public static String commit(String projectUrl, String prompt, String jobId, String side) {
        String expected = AutomationScript.jsQuote(prompt);
        String project = AutomationScript.jsQuote(TargetParser.projectId(projectUrl));
        String marker = AutomationScript.jsQuote("chatgpt-prompt-scheduler:bootstrap:" + jobId + ":" + side);
        return "(() => {" + base(expected, project)
                + "if(actualConversation&&promptAlreadyPresent)return result('CONFIRMED','첫 요청이 이미 존재합니다.',routeDiagnostics);"
                + "if(actualConversation)return result('TARGET_CONTEXT_MISMATCH','제출 전에 새 대화 경로를 벗어났습니다.',routeDiagnostics);"
                + composerLookup() + "if(!composer)return result('DOM_STRUCTURE_ERROR','입력창을 찾지 못했습니다.',routeDiagnostics);"
                + composerFunctions()
                + "if(!same())return result('DRAFT_CHANGED','제출 직전 입력값이 변경되었습니다.',routeDiagnostics);"
                + "const send=findSend();if(!send||send.disabled||send.getAttribute('aria-disabled')==='true')return result('SEND_UNAVAILABLE','전송 버튼을 사용할 수 없습니다.',routeDiagnostics);"
                + "const markerKey=" + marker + ";const value=JSON.stringify({at:Date.now(),url:location.href});let saved=false;try{sessionStorage.setItem(markerKey,value);saved=sessionStorage.getItem(markerKey)===value;}catch(_){}if(!saved)return result('SUBMIT_MARKER_FAILED','제출 경계 표식을 저장하지 못했습니다.',routeDiagnostics);send.click();return result('SUBMITTED','전송 클릭 완료',{...routeDiagnostics,marker:value});"
                + "})()";
    }

    public static String observe(String projectUrl, String prompt) {
        String expected = AutomationScript.jsQuote(prompt);
        String project = AutomationScript.jsQuote(TargetParser.projectId(projectUrl));
        return "(() => {" + base(expected, project)
                + "if(actualConversation&&promptAlreadyPresent)return result('CONFIRMED','첫 요청과 대화 URL 확인',{...routeDiagnostics,conversationId:actualConversation});"
                + "if(actualConversation&&!promptAlreadyPresent)return result('WRONG_CONVERSATION','요청과 일치하지 않는 프로젝트 대화입니다.',routeDiagnostics);"
                + "return result('RETRY','생성된 대화와 첫 요청 확인 대기',routeDiagnostics);"
                + "})()";
    }

    private static String base(String expected, String project) {
        return "const result=(status,detail='',diagnostics={})=>JSON.stringify({status,detail,url:location.href,diagnostics});"
                + "if(location.hostname!=='chatgpt.com'&&location.hostname!=='www.chatgpt.com')return result('TARGET_CONTEXT_MISMATCH','호스트 불일치');"
                + "const norm=s=>String(s??'').replace(/[\\u200B-\\u200D\\uFEFF]/g,'').replace(/\\u00a0/g,' ').replace(/\\r\\n?/g,'\\n').trim();"
                + "const canonical=s=>norm(s).replace(/[ \\t]+/g,' ').replace(/ *\\n+ */g,'\\n');"
                + "const expected=norm(" + expected + "),expectedProject=" + project + ";"
                + "const parts=location.pathname.split('/').filter(Boolean);const after=k=>{const i=parts.indexOf(k);return i>=0&&i+1<parts.length?parts[i+1]:'';};"
                + "const actualProject=after('g'),actualConversation=after('c');"
                + "const users=[...document.querySelectorAll('[data-message-author-role=\"user\"],article[data-turn=\"user\"]')];"
                + "const promptAlreadyPresent=users.some(e=>canonical(e.innerText||e.textContent||'')===canonical(expected));"
                + "const routeDiagnostics={expectedProject,actualProject,actualConversation,userMessages:users.length,promptAlreadyPresent};"
                + "if(actualProject!==expectedProject)return result('PROJECT_MISMATCH','지정 프로젝트가 아닙니다.',routeDiagnostics);"
                + "const authVisible=e=>!!e&&e.isConnected&&e.offsetParent!==null;const visibleAuthGate=[...document.querySelectorAll('[data-testid*=login],a[href*=\"/auth/login\"],button')].filter(authVisible).some(e=>/^(log in|sign up|로그인|가입)$/i.test(String(e.innerText||e.getAttribute('aria-label')||'').trim()));if(visibleAuthGate)return result('AUTH_REQUIRED','ChatGPT 로그인이 필요합니다.',routeDiagnostics);"
                + "const clip=(s,n=700)=>{s=String(s??'');return s.length>n?s.slice(0,n):s};";
    }

    private static String composerLookup() {
        return "const selectors=['textarea#prompt-textarea','textarea[data-testid=\"prompt-textarea\"]','div#prompt-textarea[contenteditable=\"true\"]','main form [contenteditable=\"true\"][data-lexical-editor=\"true\"]','main form [contenteditable=\"true\"]'];let composer=null;for(const s of selectors){composer=[...document.querySelectorAll(s)].find(e=>e&&e.isConnected&&e.offsetParent!==null);if(composer)break;}";
    }

    private static String composerFunctions() {
        return "const raw=()=>('value'in composer?composer.value:(composer.innerText||composer.textContent||''));const same=()=>canonical(raw())===canonical(expected);const findSend=()=>{const scope=composer.closest('form')||document;return [...scope.querySelectorAll('button')].find(b=>b.dataset.testid==='send-button'||b.dataset.testid==='composer-submit-button'||/send|보내기|submit/i.test((b.getAttribute('aria-label')||'')+' '+(b.title||'')));};";
    }

    private static String inputPrompt() {
        return "composer.focus();if('value'in composer){const proto=Object.getPrototypeOf(composer);const own=Object.getOwnPropertyDescriptor(proto,'value');const base=typeof HTMLTextAreaElement!=='undefined'?Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value'):null;const setter=own?.set||base?.set;if(setter)setter.call(composer,expected);else composer.value=expected;composer.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:expected}));composer.dispatchEvent(new Event('change',{bubbles:true}));}else{const selection=window.getSelection(),range=document.createRange();range.selectNodeContents(composer);selection.removeAllRanges();selection.addRange(range);try{document.execCommand('delete',false,null);document.execCommand('insertText',false,expected);}catch(_){composer.textContent=expected;composer.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:expected}));}}";
    }
}
