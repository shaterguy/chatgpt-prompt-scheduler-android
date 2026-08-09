package com.shaterguy.chatgptpromptscheduler;

/** Small WebView scripts for one-turn submission and stable assistant-signal observation. */
public final class OrchestrationScript {
    private OrchestrationScript() {}

    public static String prepare(String prompt) {
        String expected = jsQuote(prompt);
        return "(() => {" + common() +
                "if(!validHost())return out('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트가 아닙니다.');" +
                "if(!navigator.onLine)return out('NETWORK_ERROR','네트워크 연결이 끊어졌습니다.');if(!document.querySelector('main'))return out('DOM_STRUCTURE_ERROR','ChatGPT 대화 영역을 찾지 못했습니다.');" +
                "const expected=norm(" + expected + ");" +
                "const messages=[...document.querySelectorAll('[data-message-author-role=\"user\"],article[data-turn=\"user\"]')];" +
                "if(messages.some(e=>equiv(e.innerText||e.textContent,expected)))return out('ALREADY_SUBMITTED','동일 프롬프트가 이미 존재합니다.');" +
                "const selectors=['textarea#prompt-textarea','textarea[data-testid=\"prompt-textarea\"]','div#prompt-textarea[contenteditable=\"true\"]','main form [contenteditable=\"true\"][data-lexical-editor=\"true\"]','main form [contenteditable=\"true\"]'];" +
                "let composer=null;for(const s of selectors){composer=[...document.querySelectorAll(s)].find(e=>e.isConnected&&e.offsetParent!==null);if(composer)break;}" +
                "if(!composer&&visibleAuthGate())return out('AUTH_REQUIRED','명시적 로그인 화면이 표시되었습니다.');" +
                "if(!composer)return out('RETRY','입력창 대기');" +
                "const read=()=>norm('value'in composer?composer.value:(composer.innerText||composer.textContent||''));" +
                "const actual=read();if(actual&&!equiv(actual,expected)){let hash=2166136261;for(let i=0;i<actual.length;i++){hash^=actual.charCodeAt(i);hash=Math.imul(hash,16777619);}return out('DRAFT_PRESENT','중계 대화 입력창에 다른 초안이 있습니다.',{draft_length:actual.length,draft_fingerprint:(hash>>>0).toString(16)});}" +
                "if(!equiv(actual,expected)){composer.focus();if('value'in composer){const proto=Object.getPrototypeOf(composer);const descriptor=Object.getOwnPropertyDescriptor(proto,'value')||Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value');if(descriptor?.set)descriptor.set.call(composer,expected);else composer.value=expected;composer.dispatchEvent(new Event('input',{bubbles:true}));}else{const selection=window.getSelection();const range=document.createRange();range.selectNodeContents(composer);selection.removeAllRanges();selection.addRange(range);document.execCommand('insertText',false,expected);composer.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:expected}));}return out('RETRY','입력 반영 확인');}" +
                "return out('READY','입력 확인 완료');" +
                "})()";
    }

    /**
     * First-turn-only preparation: it intentionally replaces any composer draft and does not
     * suppress a new start merely because the same Job ID appeared in an older user turn.
     */
    public static String prepareInitialStart(String prompt) {
        String expected = jsQuote(prompt);
        return "(() => {" + common() +
                "if(!validHost())return out('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트가 아닙니다.');" +
                "if(!navigator.onLine)return out('NETWORK_ERROR','네트워크 연결이 끊어졌습니다.');if(!document.querySelector('main'))return out('DOM_STRUCTURE_ERROR','ChatGPT 대화 영역을 찾지 못했습니다.');" +
                "const expected=norm(" + expected + ");" +
                "const messages=[...document.querySelectorAll('[data-message-author-role=\"user\"],article[data-turn=\"user\"]')];" +
                "const matching=messages.filter(e=>equiv(e.innerText||e.textContent,expected)).length;" +
                "const selectors=['textarea#prompt-textarea','textarea[data-testid=\"prompt-textarea\"]','div#prompt-textarea[contenteditable=\"true\"]','main form [contenteditable=\"true\"][data-lexical-editor=\"true\"]','main form [contenteditable=\"true\"]'];" +
                "let composer=null;for(const s of selectors){composer=[...document.querySelectorAll(s)].find(e=>e.isConnected&&e.offsetParent!==null);if(composer)break;}" +
                "if(!composer&&visibleAuthGate())return out('AUTH_REQUIRED','명시적 로그인 화면이 표시되었습니다.');" +
                "if(!composer)return out('RETRY','입력창 대기');" +
                "const read=()=>norm('value'in composer?composer.value:(composer.innerText||composer.textContent||''));" +
                "const actual=read();if(!equiv(actual,expected)){composer.focus();if('value'in composer){const proto=Object.getPrototypeOf(composer);const descriptor=Object.getOwnPropertyDescriptor(proto,'value')||Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value');if(descriptor?.set)descriptor.set.call(composer,expected);else composer.value=expected;composer.dispatchEvent(new Event('input',{bubbles:true}));}else{const selection=window.getSelection();const range=document.createRange();range.selectNodeContents(composer);selection.removeAllRanges();selection.addRange(range);document.execCommand('delete',false,null);document.execCommand('insertText',false,expected);composer.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:expected}));}return out('RETRY','시작 문구로 입력창 덮어쓰기',{matching_user_turns:matching});}" +
                "return out('READY','시작 문구 입력 확인 완료',{matching_user_turns:matching});" +
                "})()";
    }

    public static String commit(String prompt) {
        String expected = jsQuote(prompt);
        return "(() => {" + common() +
                "if(!validHost())return out('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트가 아닙니다.');" +
                "if(visibleAuthGate())return out('AUTH_REQUIRED','명시적 로그인 화면이 표시되었습니다.');" +
                "const expected=norm(" + expected + ");" +
                "const messages=[...document.querySelectorAll('[data-message-author-role=\"user\"],article[data-turn=\"user\"]')];" +
                "if(messages.some(e=>equiv(e.innerText||e.textContent,expected)))return out('ALREADY_SUBMITTED','동일 프롬프트가 이미 존재합니다.');" +
                "const selectors=['textarea#prompt-textarea','textarea[data-testid=\"prompt-textarea\"]','div#prompt-textarea[contenteditable=\"true\"]','main form [contenteditable=\"true\"][data-lexical-editor=\"true\"]','main form [contenteditable=\"true\"]'];" +
                "let composer=null;for(const s of selectors){composer=[...document.querySelectorAll(s)].find(e=>e.isConnected&&e.offsetParent!==null);if(composer)break;}" +
                "if(!composer)return out('AMBIGUOUS','전송 커밋 중 입력창을 찾지 못했습니다.');" +
                "const actual=norm('value'in composer?composer.value:(composer.innerText||composer.textContent||''));" +
                "if(!equiv(actual,expected))return out('AMBIGUOUS','전송 커밋 중 입력값이 달라졌습니다.');" +
                "const form=composer.closest('form');const buttons=[...(form||document).querySelectorAll('button')];" +
                "const send=buttons.find(b=>b.dataset.testid==='send-button'||b.dataset.testid==='composer-submit-button'||/send|보내기|submit/i.test((b.getAttribute('aria-label')||'')+' '+(b.title||'')));" +
                "if(!send||send.disabled||send.getAttribute('aria-disabled')==='true')return out('AMBIGUOUS','전송 버튼이 준비되지 않았습니다.');" +
                "send.click();return out('SUBMITTED','전송 클릭 완료');" +
                "})()";
    }

    /** Clicks the exact startup prompt without applying historical-turn duplicate suppression. */
    public static String commitInitialStart(String prompt) {
        String expected = jsQuote(prompt);
        return "(() => {" + common() +
                "if(!validHost())return out('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트가 아닙니다.');" +
                "if(visibleAuthGate())return out('AUTH_REQUIRED','명시적 로그인 화면이 표시되었습니다.');" +
                "const expected=norm(" + expected + ");" +
                "const selectors=['textarea#prompt-textarea','textarea[data-testid=\"prompt-textarea\"]','div#prompt-textarea[contenteditable=\"true\"]','main form [contenteditable=\"true\"][data-lexical-editor=\"true\"]','main form [contenteditable=\"true\"]'];" +
                "let composer=null;for(const s of selectors){composer=[...document.querySelectorAll(s)].find(e=>e.isConnected&&e.offsetParent!==null);if(composer)break;}" +
                "if(!composer)return out('AMBIGUOUS','전송 커밋 중 입력창을 찾지 못했습니다.');" +
                "const actual=norm('value'in composer?composer.value:(composer.innerText||composer.textContent||''));" +
                "if(!equiv(actual,expected))return out('AMBIGUOUS','전송 커밋 중 입력값이 달라졌습니다.');" +
                "const form=composer.closest('form');const buttons=[...(form||document).querySelectorAll('button')];" +
                "const send=buttons.find(b=>b.dataset.testid==='send-button'||b.dataset.testid==='composer-submit-button'||/send|보내기|submit/i.test((b.getAttribute('aria-label')||'')+' '+(b.title||'')));" +
                "if(!send||send.disabled||send.getAttribute('aria-disabled')==='true')return out('AMBIGUOUS','전송 버튼이 준비되지 않았습니다.');" +
                "send.click();return out('SUBMITTED','시작 문구 전송 클릭 완료');" +
                "})()";
    }

    public static String recoverSubmission(String prompt) {
        String expected = jsQuote(prompt);
        return "(() => {" + common() +
                "if(!validHost())return out('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트가 아닙니다.');" +
                "const expected=norm(" + expected + ");" +
                "const messages=[...document.querySelectorAll('[data-message-author-role=\"user\"],article[data-turn=\"user\"]')];" +
                "if(messages.some(e=>equiv(e.innerText||e.textContent,expected)))return out('SUBMITTED','전송된 사용자 턴을 확인했습니다.');" +
                "return out('RECOVERY_ABSENT','전송 사용자 턴이 아직 확인되지 않습니다.');" +
                "})()";
    }

    public static String recoverInitialStartSubmission(String prompt, int baselineCount) {
        return confirmInitialStartSubmission(prompt, baselineCount, "RECOVERY_ABSENT");
    }

    /** Confirmation after a click result was received; does not click or rewrite the composer. */
    public static String confirmSubmission(String prompt) {
        String expected = jsQuote(prompt);
        return "(() => {" + common() +
                "if(!validHost())return out('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트가 아닙니다.');" +
                "const expected=norm(" + expected + ");" +
                "const messages=[...document.querySelectorAll('[data-message-author-role=\"user\"],article[data-turn=\"user\"]')];" +
                "if(messages.some(e=>equiv(e.innerText||e.textContent,expected)))return out('SUBMITTED','전송된 사용자 턴을 확인했습니다.');" +
                "const selectors=['textarea#prompt-textarea','textarea[data-testid=\"prompt-textarea\"]','div#prompt-textarea[contenteditable=\"true\"]','main form [contenteditable=\"true\"][data-lexical-editor=\"true\"]','main form [contenteditable=\"true\"]'];" +
                "let composer=null;for(const s of selectors){composer=[...document.querySelectorAll(s)].find(e=>e.isConnected&&e.offsetParent!==null);if(composer)break;}" +
                "return out('RETRY','전송된 사용자 턴 반영 대기');" +
                "})()";
    }

    public static String confirmInitialStartSubmission(String prompt, int baselineCount) {
        return confirmInitialStartSubmission(prompt, baselineCount, "RETRY");
    }

    private static String confirmInitialStartSubmission(String prompt, int baselineCount, String absentStatus) {
        String expected = jsQuote(prompt);
        int baseline = Math.max(0, baselineCount);
        return "(() => {" + common() +
                "if(!validHost())return out('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트가 아닙니다.');" +
                "const expected=norm(" + expected + ");const baseline=" + baseline + ";" +
                "const messages=[...document.querySelectorAll('[data-message-author-role=\"user\"],article[data-turn=\"user\"]')];" +
                "const matching=messages.filter(e=>equiv(e.innerText||e.textContent,expected)).length;" +
                "if(matching>baseline)return out('SUBMITTED','새 시작 사용자 턴을 확인했습니다.',{matching_user_turns:matching});" +
                "return out('" + absentStatus + "','새 시작 사용자 턴 반영 대기',{matching_user_turns:matching});" +
                "})()";
    }

    /** Finds and clicks exactly one visible stop-generation control; it never submits a prompt. */
    public static String stopGeneration() {
        return "(() => {" + common() +
                "if(!validHost())return out('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트가 아닙니다.');" +
                "if(!navigator.onLine)return out('NETWORK_ERROR','네트워크 연결이 끊어졌습니다.');" +
                "const candidates=[...document.querySelectorAll('main button')].filter(b=>visible(b)&&(b.dataset.testid==='stop-button'||/stop generating|응답 중지|생성 중지/i.test((b.getAttribute('aria-label')||'')+' '+(b.title||''))));" +
                "if(candidates.length===0)return out('STOP_GENERATION_UNAVAILABLE','생성 중지 버튼을 찾지 못했습니다.');" +
                "if(candidates.length!==1)return out('STOP_GENERATION_AMBIGUOUS','생성 중지 버튼이 여러 개입니다.');" +
                "const stop=candidates[0];if(stop.disabled||stop.getAttribute('aria-disabled')==='true')return out('STOP_GENERATION_AMBIGUOUS','생성 중지 버튼이 비활성입니다.');" +
                "stop.click();return out('STOP_GENERATION_CLICKED','생성 중지 클릭 완료');" +
                "})()";
    }

    public static String observe(String prompt) {
        String expected = jsQuote(prompt);
        return "(() => {" + common() +
                "if(!validHost())return out('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트가 아닙니다.');" +
                "if(!navigator.onLine)return out('NETWORK_ERROR','네트워크 연결이 끊어졌습니다.');" +
                "if(visibleAuthGate())return out('AUTH_REQUIRED','명시적 로그인 화면이 표시되었습니다.');" +
                "if(!document.querySelector('main'))return out('DOM_STRUCTURE_ERROR','ChatGPT 대화 영역을 찾지 못했습니다.');" +
                "const expected=norm(" + expected + ");" +
                "const turns=[...document.querySelectorAll('article,[data-message-author-role]')].filter((e,i,a)=>!a.some((p,j)=>j<i&&p.contains(e)));" +
                "let userIndex=-1;for(let i=0;i<turns.length;i++){const role=turns[i].getAttribute('data-message-author-role')||turns[i].getAttribute('data-turn')||turns[i].querySelector('[data-message-author-role]')?.getAttribute('data-message-author-role');if(role==='user'&&equiv(turns[i].innerText||turns[i].textContent,expected))userIndex=i;}" +
                "if(userIndex<0)return out('USER_TURN_MISSING','전송 확인된 사용자 턴이 현재 DOM에 없습니다.',{assistant_present:false,streaming:false,stop_available:false});" +
                "let assistant=null;for(let i=userIndex+1;i<turns.length;i++){const role=turns[i].getAttribute('data-message-author-role')||turns[i].getAttribute('data-turn')||turns[i].querySelector('[data-message-author-role]')?.getAttribute('data-message-author-role');if(role==='user')break;if(role==='assistant'){assistant=turns[i];break;}}" +
                "if(!assistant)return out('RETRY','어시스턴트 응답 대기',{assistant_present:false,streaming:false,stop_available:false});" +
                "const stop=[...document.querySelectorAll('button')].some(b=>b.offsetParent!==null&&(b.dataset.testid==='stop-button'||/stop generating|응답 중지|생성 중지/i.test((b.getAttribute('aria-label')||'')+' '+(b.title||''))));" +
                "const streaming=assistant.getAttribute('aria-busy')==='true'||assistant.getAttribute('data-is-streaming')==='true'||!!assistant.querySelector('[aria-busy=\"true\"],[data-is-streaming=\"true\"],[class*=\"spinner\" i],[class*=\"loading\" i]');" +
                "const meta={assistant_present:true,streaming,stop_available:stop};" +
                "if(stop||streaming)return out('RETRY','어시스턴트 응답 생성 중',meta);" +
                "const text=norm(assistant.innerText||assistant.textContent);if(!text)return out('RETRY','어시스턴트 응답 본문 대기',meta);" +
                "if(text.length>65536)return out('RESPONSE_TOO_LARGE','응답이 64 KiB 제한을 초과했습니다.');" +
                "let hash=2166136261;for(let i=0;i<text.length;i++){hash^=text.charCodeAt(i);hash=Math.imul(hash,16777619);}" +
                "return out('CANDIDATE','응답 후보 확인',{...meta,text,fingerprint:text.length+':'+(hash>>>0).toString(16)});" +
                "})()";
    }

    /**
     * Reads one conversation without submitting anything. Only structural signal candidates and
     * fixed prompt kinds are returned; assistant/user body text is never returned by this scan.
     */
    public static String reconcileScan(String expectedJobId) {
        String job = jsQuote(expectedJobId);
        return "(() => {" + common() +
                "if(!validHost())return out('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트가 아닙니다.');" +
                "if(!navigator.onLine)return out('NETWORK_ERROR','네트워크 연결이 끊어졌습니다.');" +
                "const main=document.querySelector('main');if(!main)return out('RETRY','대화 영역 대기');" +
                "if(visibleAuthGate())return out('AUTH_REQUIRED','명시적 로그인 화면이 표시되었습니다.');" +
                "const job=" + job + ";" +
                "const roleOf=e=>e?.getAttribute('data-message-author-role')||e?.getAttribute('data-turn')||e?.querySelector('[data-message-author-role]')?.getAttribute('data-message-author-role')||'';" +
                "const cleanMessage=e=>{const copy=e.cloneNode(true);copy.querySelectorAll('pre,code,blockquote').forEach(n=>n.remove());return norm(copy.innerText||copy.textContent||'');};" +
                "const promptOf=txt=>{const lines=norm(txt).split('\\n').map(norm).filter(Boolean);const line=lines.length?lines[lines.length-1]:'';if(!line.startsWith('[AUTOMATION_')||!line.endsWith(']'))return null;const t=line.slice(1,-1).split(/\\s+/);if(t[1]!==job)return null;const validSeq=t.length===4&&/^S\\d{3}$/.test(t[2])&&/^R\\d{3}$/.test(t[3]);if(t[0]==='AUTOMATION_START'&&t.length===2)return {kind:'AUTOMATION_START',raw:line};if((t[0]==='AUTOMATION_WORK_STEP'||t[0]==='AUTOMATION_CHAT_REVIEW'||t[0]==='AUTOMATION_CONTINUE_SAME')&&validSeq)return {kind:t[0],raw:line,step:t[2],round:t[3]};if(t[0]==='AUTOMATION_USER_RESOLVED'&&t.length===3&&/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(t[2]))return {kind:'AUTOMATION_USER_RESOLVED',raw:line};return null;};" +
                "const signalOf=e=>{const lines=cleanMessage(e).split('\\n').map(norm).filter(Boolean);const line=lines.length?lines[lines.length-1]:'';return line.startsWith('[AR_')&&line.endsWith(']')&&line.length<=320?line:'';};" +
                "const roots=[...main.querySelectorAll('article,[data-message-author-role]')];" +
                "const turns=roots.filter((e,i,a)=>!a.some((p,j)=>j<i&&p.contains(e)));" +
                "const stopButtons=[...main.querySelectorAll('button')].filter(b=>visible(b)&&(b.dataset.testid==='stop-button'||/stop generating|응답 중지|생성 중지/i.test((b.getAttribute('aria-label')||'')+' '+(b.title||''))));" +
                "const busy=turns.some(e=>roleOf(e)==='assistant'&&(e.getAttribute('aria-busy')==='true'||e.getAttribute('data-is-streaming')==='true'||!!e.querySelector('[aria-busy=\"true\"],[data-is-streaming=\"true\"],[class*=\"spinner\" i],[class*=\"loading\" i]')));" +
                "const generating=busy||stopButtons.length>0;const candidates=[];" +
                "for(let i=0;i<turns.length;i++){if(roleOf(turns[i])!=='assistant')continue;const signal=signalOf(turns[i]);if(!signal)continue;let predecessorIndex=-1;let predecessor=null;let predecessorSignal='';for(let j=i-1;j>=0;j--){if(roleOf(turns[j])==='user'){predecessor=promptOf(cleanMessage(turns[j]));predecessorIndex=j;if(predecessor&&predecessor.kind==='AUTOMATION_USER_RESOLVED'&&j>0){for(let k=j-1;k>=0;k--){if(roleOf(turns[k])==='assistant'){predecessorSignal=signalOf(turns[k]);break;}}}break;}}if(predecessor)candidates.push({signal,predecessor:predecessor.raw,predecessor_kind:predecessor.kind,predecessor_signal:predecessorSignal,predecessor_index:predecessorIndex,message_index:i});}" +
                "return out('SCAN','대화 상태 수집 완료',{main_present:true,generating,stop_available:stopButtons.length>0,candidates});" +
                "})()";
    }

    /** Checks whether a selected raw automation prompt already exists in the target DOM. */
    public static String reconcileTarget(String expectedPrompt, String expectedJobId) {
        String prompt = jsQuote(expectedPrompt);
        String job = jsQuote(expectedJobId);
        return "(() => {" + common() +
                "if(!validHost())return out('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트가 아닙니다.');" +
                "if(!navigator.onLine)return out('NETWORK_ERROR','네트워크 연결이 끊어졌습니다.');" +
                "const main=document.querySelector('main');if(!main)return out('RETRY','대화 영역 대기');" +
                "if(visibleAuthGate())return out('AUTH_REQUIRED','명시적 로그인 화면이 표시되었습니다.');" +
                "const expected=" + prompt + ";const job=" + job + ";" +
                "const roleOf=e=>e?.getAttribute('data-message-author-role')||e?.getAttribute('data-turn')||e?.querySelector('[data-message-author-role]')?.getAttribute('data-message-author-role')||'';" +
                "const cleanMessage=e=>{const copy=e.cloneNode(true);copy.querySelectorAll('pre,code,blockquote').forEach(n=>n.remove());return norm(copy.innerText||copy.textContent||'');};" +
                "const controlOf=txt=>{const lines=norm(txt).split('\\n').map(norm).filter(Boolean);const line=lines.length?lines[lines.length-1]:'';if(!line.startsWith('[AUTOMATION_')||!line.endsWith(']'))return '';const t=line.slice(1,-1).split(/\\s+/);return t[1]===job?line:'';};" +
                "const roots=[...main.querySelectorAll('article,[data-message-author-role]')];const turns=roots.filter((e,i,a)=>!a.some((p,j)=>j<i&&p.contains(e)));" +
                "const stops=[...main.querySelectorAll('button')].filter(b=>visible(b)&&(b.dataset.testid==='stop-button'||/stop generating|응답 중지|생성 중지/i.test((b.getAttribute('aria-label')||'')+' '+(b.title||''))));" +
                "const busy=turns.some(e=>{const r=roleOf(e);return r==='assistant'&&(e.getAttribute('aria-busy')==='true'||e.getAttribute('data-is-streaming')==='true'||!!e.querySelector('[aria-busy=\"true\"],[data-is-streaming=\"true\"],[class*=\"spinner\" i],[class*=\"loading\" i]'));});" +
                "const matches=[];for(let i=0;i<turns.length;i++){if(roleOf(turns[i])==='user'&&controlOf(cleanMessage(turns[i]))===expected)matches.push(i);}" +
                "if(matches.length===0){if(busy||stops.length>0)return out('TARGET_GENERATING','대상 대화가 생성 중입니다.',{prompt_present:false,generating:true});return out('TARGET_PROMPT_ABSENT','대응 프롬프트가 없습니다.',{prompt_present:false,generating:false});}" +
                "if(matches.length>1)return out('TARGET_PROMPT_MULTIPLE','대응 프롬프트가 여러 개입니다.',{prompt_present:true,multiple:true,generating:busy||stops.length>0});" +
                "const match=matches[0];" +
                "let response=false;let responseGenerating=false;for(let i=match+1;i<turns.length;i++){const role=roleOf(turns[i]);if(role==='user')break;if(role==='assistant'){response=true;responseGenerating=responseGenerating||turns[i].getAttribute('aria-busy')==='true'||turns[i].getAttribute('data-is-streaming')==='true'||!!turns[i].querySelector('[aria-busy=\"true\"],[data-is-streaming=\"true\"],[class*=\"spinner\" i],[class*=\"loading\" i]');}}" +
                "if(responseGenerating||busy||stops.length>0)return out('TARGET_PROMPT_PRESENT_GENERATING','대응 프롬프트 뒤 응답 생성 중입니다.',{prompt_present:true,has_response:response,generating:true});" +
                "return out(response?'TARGET_PROMPT_PRESENT_WITH_RESPONSE':'TARGET_PROMPT_PRESENT_NO_RESPONSE','대응 프롬프트 존재',{prompt_present:true,has_response:response,generating:false});" +
                "})()";
    }

    private static String common() {
        return "const norm=s=>String(s??'').replace(/[\\u200B-\\u200D\\uFEFF]/g,'').replace(/\\u00a0/g,' ').replace(/\\r\\n?/g,'\\n').trim();" +
                "const equiv=(a,b)=>norm(a).replace(/\\s+/g,' ')===norm(b).replace(/\\s+/g,' ');" +
                "const out=(status,detail='',data={})=>JSON.stringify({status,detail,...data});" +
                "const visible=e=>{if(!e||!e.isConnected)return false;const r=e.getBoundingClientRect();const s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';};" +
                "const authLabel=e=>norm(e?.innerText||e?.textContent||e?.value||e?.getAttribute('aria-label')||'').toLowerCase();" +
                "const exactAuthLabel=t=>/^(log in|login|sign up|signup|로그인|회원가입|가입하기|로그인하기)$/.test(t);" +
                "const visibleAuthGate=()=>{const main=document.querySelector('main');if(!main)return false;const hasConversation=!!main.querySelector('[data-message-author-role=user],[data-message-author-role=assistant],article[data-turn=user],article[data-turn=assistant]');const authRoot=[...main.querySelectorAll('form,[role=dialog],[data-testid*=auth i],[data-testid*=login i],[data-testid*=signup i],[id*=login i],[id*=signup i]')].some(e=>visible(e)&&(/login|sign.?up|auth|로그인|회원가입/i.test(authLabel(e))||!!e.querySelector('input[type=password],input[type=email]')));const authCta=[...main.querySelectorAll('button,a,[role=button],input[type=submit]')].some(e=>visible(e)&&exactAuthLabel(authLabel(e)));return authRoot||(!hasConversation&&authCta);};" +
                "const validHost=()=>location.protocol==='https:'&&(location.hostname==='chatgpt.com'||location.hostname==='www.chatgpt.com');";
    }
    static String jsQuote(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n")
                .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029") + "\"";
    }
}
