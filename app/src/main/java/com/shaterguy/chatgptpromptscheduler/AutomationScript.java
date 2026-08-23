package com.shaterguy.chatgptpromptscheduler;

public final class AutomationScript {
    private AutomationScript() {}

    public static String build(Schedule schedule, String stampedPrompt, String runId, int attempt) {
        String prompt = jsQuote(stampedPrompt);
        String run = jsQuote(runId);
        return "(() => {" +
                "const clip=(s,n=700)=>{s=String(s??'');return s.length>n?s.slice(0,n)+'…('+s.length+')':s};" +
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
                preferenceScript(schedule, runId) +
                "const selectors=['textarea#prompt-textarea','textarea[data-testid=\"prompt-textarea\"]','div#prompt-textarea[contenteditable=\"true\"]','[contenteditable=\"true\"][data-lexical-editor=\"true\"]','main form [contenteditable=\"true\"]'];" +
                "let selector='';let composer=null;for(const s of selectors){const candidates=[...document.querySelectorAll(s)];const found=candidates.find(e=>e&&e.isConnected&&e.offsetParent!==null);if(found){selector=s;composer=found;break;}}" +
                "if(!composer)return result('RETRY','입력창 대기',{...routeDiagnostics,mode:modeDiagnostics,model:modelDiagnostics,reasoning:reasoningDiagnostics,selectors,readyState:document.readyState,activeTag:document.activeElement?.tagName||'',forms:document.forms.length});" +
                "const raw=()=>('value'in composer?composer.value:(composer.innerText||composer.textContent||''));" +
                "const same=()=>canonical(raw())===canonical(expected);" +
                "const diag=(phase,strategy='')=>({phase,strategy,attempt:" + Math.max(0, attempt) + ",selector,tag:composer.tagName,contentEditable:composer.getAttribute('contenteditable')||'',connected:composer.isConnected,visible:composer.offsetParent!==null,activeIsComposer:document.activeElement===composer,hasFocus:document.hasFocus(),actualLength:norm(raw()).length,expectedLength:expected.length,actualPreview:clip(norm(raw())),htmlPreview:clip(composer.innerHTML||''),readyState:document.readyState,route:routeDiagnostics,mode:modeDiagnostics,model:modelDiagnostics,reasoning:reasoningDiagnostics});" +
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

    private static String preferenceScript(Schedule schedule, String runId) {
        if ("existing".equals(schedule.targetType)) {
            return "const modeDiagnostics={requested:'inherit',ready:true,action:'',skipped:true};" +
                    "const modelDiagnostics={requested:'inherit',ready:true,action:'',skipped:true};" +
                    "const reasoningDiagnostics={requested:'inherit',ready:true,action:'',skipped:true};";
        }
        String run = jsQuote(runId);
        String requestedMode = "work".equals(schedule.experience) ? "work" : "chat";
        String modeLabels = "work".equals(schedule.experience) ? "['work','작업']" : "['chat','채팅']";
        String requestedModel = Schedule.normalizedWorkModel(schedule.experience, schedule.workModel);
        String requestedEffort = Schedule.normalizedReasoningEffort(schedule.experience, schedule.reasoningEffort);
        String requestedChatReasoning = Schedule.normalizedChatReasoning(schedule.experience, schedule.chatReasoning);
        String common = "const modeKey='chatgpt-prompt-scheduler:mode:' + " + run + ";" +
                "const exactText=s=>String(s??'').replace(/\\s+/g,' ').trim().toLowerCase();" +
                "const desiredModeLabels=" + modeLabels + ";" +
                "const forbiddenMode=/new chat|새 채팅|새 대화|new conversation/i;" +
                "const modeCandidates=[...document.querySelectorAll('button,[role=\"button\"],[role=\"menuitemradio\"],[role=\"radio\"],[role=\"tab\"]')];" +
                "const mode=modeCandidates.find(e=>{const inner=exactText(e.innerText||'');const aria=exactText(e.getAttribute('aria-label')||'');const combined=exactText(inner+' '+aria);if(forbiddenMode.test(combined))return false;const role=e.getAttribute('role')||'';const testId=exactText(e.dataset?.testid||'');const strong=e.hasAttribute('aria-pressed')||e.hasAttribute('aria-checked')||['menuitemradio','radio','tab'].includes(role)||e.getAttribute('aria-haspopup')==='menu'||/mode|experience/.test(testId);return strong&&(desiredModeLabels.includes(inner)||desiredModeLabels.includes(aria));});" +
                "let modePrior='';try{modePrior=sessionStorage.getItem(modeKey)||'';}catch(_){}" +
                "const modeSelected=!!mode&&(mode.getAttribute('aria-pressed')==='true'||mode.getAttribute('aria-checked')==='true'||/active|selected|checked/.test(exactText(mode.dataset?.state||'')));" +
                "const modeDiagnostics={requested:" + jsQuote(requestedMode) + ",candidateFound:!!mode,candidateLabel:mode?clip(exactText((mode.innerText||'')+' '+(mode.getAttribute('aria-label')||'')),120):'',selected:modeSelected,clicked:false,priorClick:!!modePrior};" +
                "if(mode&&!modeSelected&&!modePrior){const value=JSON.stringify({at:Date.now(),label:modeDiagnostics.candidateLabel});try{sessionStorage.setItem(modeKey,value);}catch(_){}window[modeKey]=value;mode.click();modeDiagnostics.clicked=true;}" +
                "if(modeDiagnostics.clicked)return result('RETRY','모드 전환 반영 대기',{...routeDiagnostics,mode:modeDiagnostics});" +
                "const elementLabel=e=>exactText(e?.innerText||'')||exactText(e?.getAttribute?.('aria-label')||'');" +
                "const visible=e=>!!e&&e.isConnected&&e.offsetParent!==null;" +
                "const composerInput=document.querySelector('#prompt-textarea')||[...document.querySelectorAll('textarea,[contenteditable=\"true\"]')].filter(visible).sort((a,b)=>b.getBoundingClientRect().bottom-a.getBoundingClientRect().bottom)[0]||null;" +
                "const composerForm=composerInput?.closest?.('form')||null;" +
                "const inComposer=e=>{if(!e||!composerInput)return false;if(composerForm)return composerForm.contains(e);const a=e.getBoundingClientRect(),b=composerInput.getBoundingClientRect();return a.bottom>=b.top-240&&a.top<=b.bottom+240&&a.right>=b.left-320&&a.left<=b.right+320;};" +
                "const selectedState=e=>!!e&&(e.getAttribute('aria-checked')==='true'||e.getAttribute('aria-pressed')==='true'||e.getAttribute('aria-selected')==='true'||/^(checked|selected|active|on)$/.test(exactText(e.dataset?.state||'')));" +
                "const openMenu=e=>{if(!e)return;e.focus?.();const init={bubbles:true,cancelable:true,composed:true,button:0,buttons:1,pointerId:1,pointerType:'mouse',isPrimary:true};if(typeof PointerEvent==='function')e.dispatchEvent(new PointerEvent('pointerdown',init));else e.dispatchEvent(new MouseEvent('mousedown',init));};";
        if ("work".equals(schedule.experience)) {
            return common + modelScript(requestedModel) + reasoningScript(requestedEffort);
        }
        return common +
                "const modelDiagnostics={requested:'inherit',ready:true,action:'',skipped:true};" +
                ChatReasoningScript.inline(requestedChatReasoning, runId);
    }

    private static String modelScript(String requestedModel) {
        if ("inherit".equals(requestedModel)) {
            return "const modelDiagnostics={requested:'inherit',ready:true,action:'',skipped:true};";
        }
        return "const desiredModel=" + jsQuote(requestedModel) + ";" +
                "const modelOf=s=>{const value=exactText(s);const match=value.match(/(?:^|\\s)(sol|terra|luna)(?:\\s|$)/);return match?match[1]:'';};" +
                "const directModelLabel=/^(?:(?:gpt-?)?5(?:\\.6)?\\s+)?(?:sol|terra|luna)(\\s|$)/;" +
                "const modelOptions=[...document.querySelectorAll('[role=\"menuitemradio\"],[role=\"radio\"],[role=\"option\"],[role=\"menuitem\"]')].filter(visible).filter(e=>{const role=e.getAttribute('role')||'',label=elementLabel(e);return !!modelOf(label)&&(role!=='menuitem'||directModelLabel.test(label));});" +
                "const desiredModelOption=modelOptions.find(e=>modelOf(elementLabel(e))===desiredModel);" +
                "const modelLevelItem=[...document.querySelectorAll('[role=\"menuitem\"]')].filter(visible).find(e=>/^(model|모델)(\\s|$)/.test(elementLabel(e)));" +
                "const workSettingsTrigger=[...document.querySelectorAll('button[aria-haspopup=\"menu\"],[role=\"button\"][aria-haspopup=\"menu\"]')].filter(visible).filter(inComposer).find(e=>!!modelOf(elementLabel(e)));" +
                "const modelTriggerExpanded=!!workSettingsTrigger&&workSettingsTrigger.getAttribute('aria-expanded')==='true';" +
                "const currentModel=workSettingsTrigger?modelOf(elementLabel(workSettingsTrigger)):'';" +
                "let modelReady=false,modelAction='';" +
                "if(desiredModelOption){if(selectedState(desiredModelOption)){modelReady=true;if(modelTriggerExpanded){openMenu(workSettingsTrigger);modelAction='close-selected-model-menu';}}else{desiredModelOption.click();modelAction='select-model';}}" +
                "else if(workSettingsTrigger&&currentModel===desiredModel){modelReady=true;}" +
                "else if(modelLevelItem){modelLevelItem.click();modelAction='open-model-menu';}" +
                "else if(workSettingsTrigger&&!modelTriggerExpanded){openMenu(workSettingsTrigger);modelAction='open-work-settings-menu';}" +
                "const modelDiagnostics={requested:desiredModel,ready:modelReady,action:modelAction,current:currentModel,triggerLabel:workSettingsTrigger?clip(elementLabel(workSettingsTrigger),160):'',triggerExpanded:modelTriggerExpanded,triggerInComposer:inComposer(workSettingsTrigger),levelItemFound:!!modelLevelItem,optionFound:!!desiredModelOption};" +
                "if(modelAction)return result('RETRY','Work 모델 반영 대기',{...routeDiagnostics,mode:modeDiagnostics,model:modelDiagnostics});" +
                "if(!modelReady)return result('RETRY','Work 모델 선택 요소 대기',{...routeDiagnostics,mode:modeDiagnostics,model:modelDiagnostics});";
    }

    private static String reasoningScript(String requestedEffort) {
        if ("inherit".equals(requestedEffort)) {
            return "const reasoningDiagnostics={requested:'inherit',ready:true,action:'',skipped:true};";
        }
        return "const desiredEffort=" + jsQuote(requestedEffort) + ";" +
                "const effortOf=s=>{const value=exactText(s);if(['울트라','ultra'].some(v=>value.includes(v)))return'ultra';if(['매우 높음','extra high','very high','xhigh'].some(v=>value.includes(v)))return'xhigh';if(['maximum','max','최대'].some(v=>value.includes(v)))return'max';if(['medium','중간'].some(v=>value.includes(v)))return'medium';if(['light','가벼움'].some(v=>value.includes(v)))return'light';if(['high','높음'].some(v=>value.includes(v)))return'high';return'';};" +
                "const directEffortLabel=/^(ultra|울트라|very high|extra high|xhigh|매우 높음|maximum|max|최대|medium|중간|light|가벼움|high|높음)(\\s|$)/;" +
                "const effortOptions=[...document.querySelectorAll('[role=\"menuitemradio\"],[role=\"radio\"],[role=\"option\"],[role=\"menuitem\"]')].filter(visible).filter(e=>{const role=e.getAttribute('role')||'',label=elementLabel(e);return !!effortOf(label)&&(role!=='menuitem'||directEffortLabel.test(label));});" +
                "const desiredEffortOption=effortOptions.find(e=>effortOf(elementLabel(e))===desiredEffort);" +
                "const reasoningLevelItem=[...document.querySelectorAll('[role=\"menuitem\"]')].filter(visible).find(e=>/^(reasoning (level|effort)|추론 (수준|강도|정도))(\\s|$)/.test(elementLabel(e)));" +
                "const reasoningTrigger=[...document.querySelectorAll('button[aria-haspopup=\"menu\"],[role=\"button\"][aria-haspopup=\"menu\"]')].filter(visible).filter(inComposer).find(e=>!!effortOf(elementLabel(e)));" +
                "const reasoningTriggerExpanded=!!reasoningTrigger&&reasoningTrigger.getAttribute('aria-expanded')==='true';" +
                "const currentEffort=reasoningTrigger?effortOf(elementLabel(reasoningTrigger)):'';" +
                "let reasoningReady=false,reasoningAction='';" +
                "if(desiredEffortOption){if(selectedState(desiredEffortOption)){reasoningReady=true;if(reasoningTriggerExpanded){openMenu(reasoningTrigger);reasoningAction='close-selected-effort-menu';}}else{desiredEffortOption.click();reasoningAction='select-effort';}}" +
                "else if(reasoningTrigger&&currentEffort===desiredEffort){reasoningReady=true;if(reasoningTriggerExpanded){openMenu(reasoningTrigger);reasoningAction='close-selected-effort-menu';}}" +
                "else if(reasoningLevelItem){reasoningLevelItem.click();reasoningAction='open-effort-menu';}" +
                "else if(reasoningTrigger&&!reasoningTriggerExpanded){openMenu(reasoningTrigger);reasoningAction='open-reasoning-menu';}" +
                "const reasoningDiagnostics={requested:desiredEffort,ready:reasoningReady,action:reasoningAction,current:currentEffort,triggerLabel:reasoningTrigger?clip(elementLabel(reasoningTrigger),160):'',triggerExpanded:reasoningTriggerExpanded,triggerInComposer:inComposer(reasoningTrigger),levelItemFound:!!reasoningLevelItem,optionFound:!!desiredEffortOption};" +
                "if(reasoningAction)return result('RETRY','추론 강도 반영 대기',{...routeDiagnostics,mode:modeDiagnostics,model:modelDiagnostics,reasoning:reasoningDiagnostics});" +
                "if(!reasoningReady)return result('RETRY','추론 강도 선택 요소 대기',{...routeDiagnostics,mode:modeDiagnostics,model:modelDiagnostics,reasoning:reasoningDiagnostics});";
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
