package com.shaterguy.chatgptpromptscheduler;

public final class AutomationScript {
    private AutomationScript() {}

    public static String build(Schedule schedule, String stampedPrompt, String runId, int attempt) {
        String prompt = jsQuote(stampedPrompt);
        String run = jsQuote(runId);
        return "(() => {" +
                "" +
                "const result=(status,detail='',diagnostics={})=>JSON.stringify({status,detail,url:location.href,diagnostics});" +
                "if(location.hostname!=='chatgpt.com'&&location.hostname!=='www.chatgpt.com')return result('TARGET_CONTEXT_MISMATCH','호스트 불일치 actual='+location.href);" +
                "const markerKey='chatgpt-prompt-scheduler:submit:' + " + run + ";" +
                "const readMarker=()=>{try{return localStorage.getItem(markerKey)||sessionStorage.getItem(markerKey)||window[markerKey]||'';}catch(_){return window[markerKey]||'';}};" +
                "const priorMarker=readMarker();if(priorMarker)return result('SUBMITTED','동일 실행의 전송 클릭 기록을 확인했습니다.',{marker:priorMarker,recoveredAfterNavigation:true});" +
                "const norm=s=>String(s??'').replace(/[\\u200B-\\u200D\\uFEFF]/g,'').replace(/\\u00a0/g,' ').replace(/\\r\\n?/g,'\\n').replace(/[\\u2028\\u2029]/g,'\\n').trim();" +
                "const canonical=s=>norm(s).replace(/[ \\t]+/g,' ').replace(/ *\\n+ */g,'\\n');" +
                "const expected=norm(" + prompt + ");" +
                "const users=[...document.querySelectorAll('[data-message-author-role=\"user\"],article[data-turn=\"user\"]')];" +
                "const expectedCanonical=canonical(expected),userTexts=users.map(e=>canonical(e.innerText||e.textContent));" +
                "const occurrences=(text,needle)=>{if(!needle)return 0;let count=0,index=0;while((index=text.indexOf(needle,index))>=0){count++;index+=needle.length;}return count;};" +
                "const matchCounts=userTexts.map(text=>occurrences(text,expectedCanonical));" +
                "const promptAlreadyPresent=matchCounts.some(count=>count===1);" +
                targetGuard(schedule, false) +
                "if(expectedType!=='existing'&&promptAlreadyPresent)return result('SUBMITTED','동일 실행 프롬프트가 이미 새 대화에 존재합니다.',{...routeDiagnostics,recoveredAfterNavigation:true});" +
                "const body=(document.body?.innerText||'').toLowerCase();" +
                "if(body.includes('log in')||body.includes('sign up')||body.includes('로그인'))return result('AUTH_REQUIRED','ChatGPT 로그인이 필요합니다.');" +
                RequestProfileScript.activate(schedule) +
                "const selectors=['textarea#prompt-textarea','textarea[data-testid=\"prompt-textarea\"]','div#prompt-textarea[contenteditable=\"true\"]','[contenteditable=\"true\"][data-lexical-editor=\"true\"]','main form [contenteditable=\"true\"]'];" +
                "let selector='';let composer=null;for(const s of selectors){const candidates=[...document.querySelectorAll(s)];const found=candidates.find(e=>e&&e.isConnected&&e.offsetParent!==null);if(found){selector=s;composer=found;break;}}" +
                "if(!composer)return result('RETRY','입력창 대기',{...routeDiagnostics,mode:modeDiagnostics,model:modelDiagnostics,reasoning:reasoningDiagnostics,selectors,readyState:document.readyState,activeTag:document.activeElement?.tagName||'',forms:document.forms.length});" +
                "const raw=()=>('value'in composer?composer.value:(composer.innerText||composer.textContent||''));" +
                "const same=()=>canonical(raw())===canonical(expected);" +
                "const diag=(phase,strategy='')=>({phase,strategy,attempt:" + Math.max(0, attempt) + ",selector,tag:composer.tagName,contentEditable:composer.getAttribute('contenteditable')||'',connected:composer.isConnected,visible:composer.offsetParent!==null,activeIsComposer:document.activeElement===composer,hasFocus:document.hasFocus(),readyState:document.readyState,route:routeDiagnostics,mode:modeDiagnostics,model:modelDiagnostics,reasoning:reasoningDiagnostics});" +
                "if(same()){const form=composer.closest('form');const buttons=[...(form||document).querySelectorAll('button')];const send=buttons.find(b=>b.dataset.testid==='send-button'||b.dataset.testid==='composer-submit-button'||/send|보내기|submit/i.test((b.getAttribute('aria-label')||'')+' '+(b.title||'')));if(!send)return result('RETRY','전송 버튼 대기',diag('ready','send-not-found'));if(send.disabled||send.getAttribute('aria-disabled')==='true')return result('RETRY','전송 버튼 활성화 대기',diag('ready','send-disabled'));const marker=JSON.stringify({at:Date.now(),url:location.href});let persisted=false;try{localStorage.setItem(markerKey,marker);persisted=localStorage.getItem(markerKey)===marker;}catch(_){}if(!persisted){try{sessionStorage.setItem(markerKey,marker);persisted=sessionStorage.getItem(markerKey)===marker;}catch(_){}}window[markerKey]=marker;if(!persisted&&!window[markerKey])return result('SUBMIT_MARKER_FAILED','전송 중복 방지 상태를 저장하지 못했습니다.',diag('ready','marker-save-failed'));send.click();return result('SUBMITTED','전송 클릭을 한 번 수행했습니다.',{...diag('submitted','marker-before-click'),marker,persisted});}" +
                "const fire=(type,inputType,data)=>{try{return composer.dispatchEvent(new InputEvent(type,{bubbles:true,cancelable:type==='beforeinput',inputType,data}));}catch(_){return composer.dispatchEvent(new Event(type,{bubbles:true,cancelable:type==='beforeinput'}));}};" +
                "const selectAll=()=>{composer.focus();const selection=window.getSelection();if(!selection)return false;const range=document.createRange();range.selectNodeContents(composer);selection.removeAllRanges();selection.addRange(range);return true;};" +
                "const nativeSet=value=>{const proto=Object.getPrototypeOf(composer);const own=Object.getOwnPropertyDescriptor(proto,'value');const base=typeof HTMLTextAreaElement!=='undefined'?Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value'):null;const setter=own?.set||base?.set;if(setter)setter.call(composer,value);else composer.value=value;fire('input','insertText',value);composer.dispatchEvent(new Event('change',{bubbles:true}));};" +
                "const execInsert=()=>{selectAll();let deleted=false,inserted=false;try{deleted=document.execCommand('delete',false,null);}catch(_){}try{inserted=document.execCommand('insertText',false,expected);}catch(_){}return {deleted,inserted};};" +
                "let strategy='';composer.focus();if('value'in composer){strategy='native-value';nativeSet(expected);}else{strategy='single-execCommand';execInsert();}" +
                "return result('RETRY',same()?'입력 반영 확인 대기':'예약 프롬프트 입력 미반영',diag('input-attempt',strategy));" +
                "})()";
    }

    public static String verify(Schedule schedule, String stampedPrompt) {
        String tail = stampedPrompt.length() > 120 ? stampedPrompt.substring(0, 120) : stampedPrompt;
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
                targetGuard(schedule, true) +
                "return promptAlreadyPresent?result('VERIFIED','프롬프트 전송 확인',{...routeDiagnostics,userMessages:users.length,matchCounts,maxMatchCount:Math.max(0,...matchCounts)}):result('RETRY','전송된 사용자 메시지 대기',{...routeDiagnostics,userMessages:users.length,matchCounts,maxMatchCount:Math.max(0,...matchCounts),readyState:document.readyState});" +
                "})()";
    }

    private static String targetGuard(Schedule schedule, boolean afterSubmit) {
        String type = jsQuote(schedule.targetType);
        String expectedUrl = jsQuote(schedule.targetUrl);
        String expectedProject = jsQuote(valueOrEmpty(TargetParser.projectId(schedule.targetUrl)));
        String expectedConversation = jsQuote(valueOrEmpty(TargetParser.conversationId(schedule.targetUrl)));
        String after = afterSubmit ? "true" : "false";
        return "const expectedType=" + type + ",expectedUrl=" + expectedUrl + ",expectedProject=" + expectedProject + ",expectedConversation=" + expectedConversation + ",afterSubmit=" + after + ";" +
                "const parts=location.pathname.split('/').filter(Boolean);" +
                "const segmentAfter=k=>{const i=parts.indexOf(k);return i>=0&&i+1<parts.length?parts[i+1]:'';};" +
                "const canonicalProject=value=>{const prefix='g-p-',tokenLength=32,end=prefix.length+tokenLength;if(value.length>160||!value.startsWith(prefix)||value.length<=end+1||value.charAt(end)!=='-')return value;const token=value.slice(prefix.length,end),slug=value.slice(end+1);return /^[0-9a-fA-F]{32}$/.test(token)&&/^[A-Za-z0-9_-]+$/.test(slug)?value.slice(0,end):value;};" +
                "const rawActualProject=segmentAfter('g'),actualProject=canonicalProject(rawActualProject),actualConversation=segmentAfter('c');" +
                "const homePath=location.pathname==='/'||location.pathname==='';" +
                "const routeDiagnostics={expectedType,expectedProject,expectedConversation,rawActualProject,actualProject,actualConversation,afterSubmit,userMessages:users.length,promptAlreadyPresent};" +
                "let targetOk=false;" +
                "if(expectedType==='existing')targetOk=!!expectedConversation&&actualConversation===expectedConversation&&(expectedProject?actualProject===expectedProject:!actualProject);" +
                "else if(expectedType==='project')targetOk=!!expectedProject&&actualProject===expectedProject&&(!actualConversation||afterSubmit||promptAlreadyPresent||users.length===0);" +
                "else if(expectedType==='general')targetOk=!actualProject&&((!actualConversation&&homePath)||(!!actualConversation&&(afterSubmit||promptAlreadyPresent||users.length===0)));" +
                "if(!targetOk)return result('TARGET_CONTEXT_MISMATCH','expected='+expectedUrl+' actual='+location.href,routeDiagnostics);";
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
