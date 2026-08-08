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
                "if(messages.some(e=>norm(e.innerText||e.textContent)===expected))return out('ALREADY_SUBMITTED','동일 프롬프트가 이미 존재합니다.');" +
                "const selectors=['textarea#prompt-textarea','textarea[data-testid=\"prompt-textarea\"]','div#prompt-textarea[contenteditable=\"true\"]','[contenteditable=\"true\"][data-lexical-editor=\"true\"]','main form [contenteditable=\"true\"]'];" +
                "let composer=null;for(const s of selectors){composer=[...document.querySelectorAll(s)].find(e=>e.isConnected&&e.offsetParent!==null);if(composer)break;}" +
                "if(!composer&&visibleAuthGate())return out('AUTH_REQUIRED','명시적 로그인 화면이 표시되었습니다.');" +
                "if(!composer)return out('RETRY','입력창 대기');" +
                "const read=()=>norm('value'in composer?composer.value:(composer.innerText||composer.textContent||''));" +
                "const actual=read();if(actual&&actual!==expected)return out('DRAFT_PRESENT','중계 대화 입력창에 다른 초안이 있습니다.');" +
                "if(actual!==expected){composer.focus();if('value'in composer){const proto=Object.getPrototypeOf(composer);const descriptor=Object.getOwnPropertyDescriptor(proto,'value')||Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value');if(descriptor?.set)descriptor.set.call(composer,expected);else composer.value=expected;composer.dispatchEvent(new Event('input',{bubbles:true}));}else{const selection=window.getSelection();const range=document.createRange();range.selectNodeContents(composer);selection.removeAllRanges();selection.addRange(range);document.execCommand('insertText',false,expected);composer.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:expected}));}return out('RETRY','입력 반영 확인');}" +
                "return out('READY','입력 확인 완료');" +
                "})()";
    }

    public static String commit(String prompt) {
        String expected = jsQuote(prompt);
        return "(() => {" + common() +
                "if(!validHost())return out('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트가 아닙니다.');" +
                "if(visibleAuthGate())return out('AUTH_REQUIRED','명시적 로그인 화면이 표시되었습니다.');" +
                "const expected=norm(" + expected + "); +
                "const messages=[...document.querySelectorAll('[data-message-author-role=\"user\"],article[data-turn=\"user\"]')];" +
                "if(messages.some(e=>norm(e.innerText||e.textContent)===expected))return out('ALREADY_SUBMITTED','동일 프롬프트가 이미 존재합니다.');" +
                "const selectors=['textarea#prompt-textarea','textarea[data-testid=\"prompt-textarea\"]','div#prompt-textarea[contenteditable=\"true\"]','[contenteditable=\"true\"][data-lexical-editor=\"true\"]','main form [contenteditable=\"true\"]'];" +
                "let composer=null;for(const s of selectors){composer=[...document.querySelectorAll(s)].find(e=>e.isConnected&&e.offsetParent!==null);if(composer)break;}" +
                "if(!composer)return out('AMBIGUOUS','전송 커밋 중 입력창을 찾지 못했습니다.');" +
                "const actual=norm('value'in composer?composer.value:(composer.innerText||composer.textContent||''));" +
                "if(actual!==expected)return out('AMBIGUOUS','전송 커밋 중 입력값이 달라졌습니다.');" +
                "const form=composer.closest('form');const buttons=[...(form||document).querySelectorAll('button')];" +
                "const send=buttons.find(b=>b.dataset.testid==='send-button'||b.dataset.testid==='composer-submit-button'||/send|보내기|submit/i.test((b.getAttribute('aria-label')||'')+' '+(b.title||'')));" +
                "if(!send||send.disabled||send.getAttribute('aria-disabled')==='true')return out('AMBIGUOUS','전송 버튼이 준비되지 않았습니다.');" +
                "send.click();return out('SUBMITTED','전송 클릭 완료');" +
                "})()";
    }

    public static String recoverSubmission(String prompt) {
        String expected = jsQuote(prompt);
        return "(() => {" + common() +
                "if(!validHost())return out('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트가 아닙니다.');" +
                "const expected=norm(" + expected + ");" +
                "const messages=[...document.querySelectorAll('[data-message-author-role=\"user\"],article[data-turn=\"user\"]')];" +
                "if(messages.some(e=>norm(e.innerText||e.textContent)===expected))return out('SUBMITTED','전송된 사용자 턴을 확인했습니다.');" +
                "return out('RECOVERY_ABSENT','전송 사용자 턴이 아직 확인되지 않습니다.');" +
                "})()";
    }

    /** Confirmation after a click result was received; does not click or rewrite the composer. */
    public static String confirmSubmission(String prompt) {
        String expected = jsQuote(prompt);
        return "(() => {" + common() +
                "if(!validHost())return out('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트가 아닙니다.');" +
                "const expected=norm(" + expected + ");" +
                "const messages=[...document.querySelectorAll('[data-message-author-role=\"user\"],article[data-turn=\"user\"]')];" +
                "if(messages.some(e=>norm(e.innerText||e.textContent)===expected))return out('SUBMITTED','전송된 사용자 턴을 확인했습니다.');" +
                "const selectors=['textarea#prompt-textarea','textarea[data-testid=\"prompt-textarea\"]','div#prompt-textarea[contenteditable=\"true\"]','[contenteditable=\"true\"][data-lexical-editor=\"true\"]','main form [contenteditable=\"true\"]'];" +
                "let composer=null;for(const s of selectors){composer=[...document.querySelectorAll(s)].find(e=>e.isConnected&&e.offsetParent!==null);if(composer)break;}" +
                "return out('RETRY','전송된 사용자 턴 반영 대기');" +
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
                "let userIndex=-1;for(let i=0;i<turns.length;i++){const role=turns[i].getAttribute('data-message-author-role')||turns[i].getAttribute('data-turn')||turns[i].querySelector('[data-message-author-role]')?.getAttribute('data-message-author-role');if(role==='user'&&norm(turns[i].innerText||turns[i].textContent)===expected)userIndex=i;}" +
                "if(userIndex<0)return out('USER_TURN_MISSING','전송 확인된 사용자 턴이 현재 DOM에 없습니다.');" +
                "let assistant=null;for(let i=userIndex+1;i<turns.length;i++){const role=turns[i].getAttribute('data-message-author-role')||turns[i].getAttribute('data-turn')||turns[i].querySelector('[data-message-author-role]')?.getAttribute('data-message-author-role');if(role==='user')break;if(role==='assistant'){assistant=turns[i];break;}}" +
                "if(!assistant)return out('RETRY','어시스턴트 응답 대기');" +
                "const stop=[...document.querySelectorAll('button')].some(b=>b.offsetParent!==null&&(b.dataset.testid==='stop-button'||/stop generating|응답 중지|생성 중지/i.test((b.getAttribute('aria-label')||'')+' '+(b.title||''))));" +
                "const streaming=assistant.getAttribute('aria-busy')==='true'||assistant.getAttribute('data-is-streaming')==='true'||!!assistant.querySelector('[aria-busy=\"true\"],[data-is-streaming=\"true\"],[class*=\"spinner\" i],[class*=\"loading\" i]');" +
                "if(stop||streaming)return out('RETRY','어시스턴트 응답 생성 중');" +
                "const text=norm(assistant.innerText||assistant.textContent);if(!text)return out('RETRY','어시스턴트 응답 본문 대기');" +
                "if(text.length>65536)return out('RESPONSE_TOO_LARGE','응답이 64 KiB 제한을 초과했습니다.');" +
                "let hash=2166136261;for(let i=0;i<text.length;i++){hash^=text.charCodeAt(i);hash=Math.imul(hash,16777619);}" +
                "return out('CANDIDATE','응답 후보 확인',{text,fingerprint:text.length+':'+(hash>>>0).toString(16)});" +
                "})()";
    }

    private static String common() {
        return "const norm=s=>String(s??'').replace(/[\\u200B-\\u200D\\uFEFF]/g,'').replace(/\\u00a0/g,' ').replace(/\\r\\n?/g,'\\n').trim();" +
                "const out=(status,detail='',data={})=>JSON.stringify({status,detail,...data});" +
                "const visible=e=>{if(!e||!e.isConnected)return false;const r=e.getBoundingClientRect();const s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';};" +
                "const authLabel=e=>norm(e?.innerText||e?.textContent||e?.value||e?.getAttribute('aria-label')||'').toLowerCase();" +
                "const exactAuthLabel=t=>/^(log in|login|sign up|signup|로그인|회원가입|가입하기|로그인하기)$/.test(t);" +
                "const visibleAuthGate=()=>{const main=document.querySelector('main');if(!main)return false;const hasConversation=!!main.querySelector('[data-message-author-role=\"user\"],[data-message-author-role=\"assistant\"],article[data-turn=\"user\"],article[data-turn=\"assistant\"]');const authRoot=[...main.querySelectorAll('form,[role=\"dialog\"],[data-testid*=" +
                "\"auth\" i],[data-testid*=\"login\" i],[data-testid*=\"signup\" i],[id*=\"login\" i],[id*=\"signup\" i]')].some(e=>visible(e)&&(/login|sign.?up|auth|로그인|회원가입/i.test(authLabel(e))||!!e.querySelector('input[type=\"password\"],input[type=\"email\"]')));const authCta=[...main.querySelectorAll('button,a,[role=\"button\"],input[type=\"submit\"]')].some(e=>visible(e)&&exactAuthLabel(authLabel(e)));return authRoot||(!hasConversation&&authCta);};" +
                "const validHost=()=>location.protocol==='https:'&&(location.hostname==='chatgpt.com'||location.hostname==='www.chatgpt.com');";
    }

    static String jsQuote(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n")
                .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029") + "\"";
    }
}
