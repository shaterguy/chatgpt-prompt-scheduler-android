package com.shaterguy.chatgptpromptscheduler;

/**
 * Finite Chat/Work mode bootstrap adapted from the stable SelfRun Drive mode gate.
 * It confirms the requested mode before model/reasoning controls are touched and keeps
 * that confirmation latched across transient picker re-renders unless the opposite mode
 * is explicitly observed.
 */
final class ModeBootstrapScript {
    private ModeBootstrapScript() {}

    static String inline(String requested, String runId) {
        return """
                const requestedMode=__REQUESTED__;
                const modeRunId=__RUN_ID__;
                const __cpmVisible=e=>!!e&&e.isConnected&&e.offsetParent!==null;
                const __cpmLabel=e=>exactText(e?.getAttribute?.('aria-label')||'')||exactText(e?.innerText||'');
                const __cpmOwner=e=>e?.closest?.('button,[role="button"],[role="radio"],[role="tab"],input[type="radio"],[aria-checked],[aria-selected],[aria-pressed]')||e||null;
                const __cpmSelectedDirect=e=>{
                  if(!e)return false;
                  const current=e.getAttribute?.('aria-current');
                  return e.getAttribute?.('aria-checked')==='true'||e.getAttribute?.('aria-pressed')==='true'||e.getAttribute?.('aria-selected')==='true'||(current!=null&&current!==''&&current!=='false')||(typeof e.checked==='boolean'&&e.checked)||e.dataset?.active==='true'||e.dataset?.selected==='true'||/^(checked|selected|active|on)$/.test(exactText(e.dataset?.state||''));
                };
                const __cpmSelectedState=e=>{
                  if(!e)return false;
                  const owner=__cpmOwner(e);
                  const owned=[e,owner].filter((node,index,all)=>node&&all.indexOf(node)===index);
                  if(owned.some(__cpmSelectedDirect))return true;
                  const selector='[aria-checked="true"],[aria-pressed="true"],[aria-selected="true"],[aria-current]:not([aria-current="false"]),[data-active="true"],[data-selected="true"],[data-state="checked"],[data-state="selected"],[data-state="active"],[data-state="on"],input[type="radio"]:checked';
                  if(owned.some(node=>!!node.querySelector?.(selector)))return true;
                  const parents=[e.parentElement,owner?.parentElement].filter((node,index,all)=>node&&all.indexOf(node)===index);
                  return parents.some(__cpmSelectedDirect);
                };
                const __cpmModeOf=s=>{const value=exactText(s);if(/new chat|새 채팅|새 대화|new conversation/i.test(value))return'';const tokens=value.split(/[^a-z0-9가-힣]+/).filter(Boolean);if(tokens.includes('chat')||tokens.includes('채팅'))return'chat';if(tokens.includes('work')||tokens.includes('작업'))return'work';return'';};
                const __cpmRawControls=[...document.querySelectorAll('button,[role="button"],[role="radio"],[role="tab"],input[type="radio"]')].filter(__cpmVisible).filter(e=>{if(e.closest('[role="menu"],[role="listbox"]'))return false;const value=__cpmModeOf(__cpmLabel(e));if(!value)return false;const role=e.getAttribute('role')||'',testId=exactText(e.dataset?.testid||'');return e.hasAttribute('aria-pressed')||e.hasAttribute('aria-checked')||e.hasAttribute('aria-selected')||role==='radio'||role==='tab'||e.matches('input[type="radio"]')||/mode|experience/.test(testId)||e.tagName==='BUTTON';});
                const __cpmGroups=[];for(const e of __cpmRawControls){let p=e.parentElement;for(let depth=0;p&&depth<4;depth++,p=p.parentElement){if(!__cpmGroups.includes(p))__cpmGroups.push(p);}}
                const __cpmGroup=__cpmGroups.find(group=>{const inside=__cpmRawControls.filter(e=>group.contains(e));return inside.some(e=>__cpmModeOf(__cpmLabel(e))==='chat')&&inside.some(e=>__cpmModeOf(__cpmLabel(e))==='work');})||null;
                const __cpmControls=__cpmGroup?__cpmRawControls.filter(e=>__cpmGroup.contains(e)):[];
                const __cpmChat=__cpmControls.find(e=>__cpmModeOf(__cpmLabel(e))==='chat')||null;
                const __cpmWork=__cpmControls.find(e=>__cpmModeOf(__cpmLabel(e))==='work')||null;
                const __cpmExactToggle=requestedMode==='work'?'work':'chatgpt';
                const __cpmExactRaw=document.querySelector('[data-tpp-toggle-value="'+__cpmExactToggle+'"]');
                const __cpmExactTarget=__cpmExactRaw&&__cpmVisible(__cpmExactRaw)?__cpmOwner(__cpmExactRaw):null;
                const __cpmHeuristicTarget=requestedMode==='work'?__cpmWork:__cpmChat;
                const mode=__cpmExactTarget||__cpmHeuristicTarget;
                const __cpmSource=__cpmExactTarget?'tpp-toggle':(__cpmHeuristicTarget?'grouped-heuristic':'none');
                const __cpmTargetFound=!!mode;
                const __cpmTargetSelected=__cpmSelectedState(mode);
                const __cpmSelectedModes=[...new Set(__cpmControls.filter(__cpmSelectedState).map(e=>__cpmModeOf(__cpmLabel(e))).filter(Boolean))];
                const __cpmCurrentMode=__cpmSelectedModes.length===1?__cpmSelectedModes[0]:(__cpmSelectedModes.length>1?'ambiguous':'unknown');
                const modeKey='chatgpt-prompt-scheduler:mode:'+modeRunId;
                const __cpmStageKey='chatgpt-prompt-scheduler:mode-stage:'+modeRunId;
                const __cpmTimeoutMs=20000,__cpmMaxAttempts=18,__cpmRetryMs=1200;
                let __cpmStage={stage:'MODE_PENDING',requested:'',confirmedMode:'',confirmedAt:0,regressionsBlocked:0};
                try{const raw=sessionStorage.getItem(__cpmStageKey)||localStorage.getItem(__cpmStageKey)||'';if(raw)__cpmStage={...__cpmStage,...JSON.parse(raw)};}catch(_){}
                const __cpmSaveStage=()=>{const value=JSON.stringify(__cpmStage);try{sessionStorage.setItem(__cpmStageKey,value);}catch(_){}try{localStorage.setItem(__cpmStageKey,value);}catch(_){}};
                if(__cpmStage.requested&&__cpmStage.requested!==requestedMode)__cpmStage={stage:'MODE_PENDING',requested:requestedMode,confirmedMode:'',confirmedAt:0,regressionsBlocked:0};
                __cpmStage.requested=requestedMode;
                let __cpmState={startedAt:0,attempts:0,clickAttempts:0,lastClickAt:0,lastAction:'',requested:''};
                try{const raw=sessionStorage.getItem(modeKey)||localStorage.getItem(modeKey)||'';if(raw)__cpmState={...__cpmState,...JSON.parse(raw)};}catch(_){}
                if(__cpmState.requested&&__cpmState.requested!==requestedMode)__cpmState={startedAt:0,attempts:0,clickAttempts:0,lastClickAt:0,lastAction:'',requested:requestedMode};
                const __cpmSaveState=()=>{const value=JSON.stringify(__cpmState);try{sessionStorage.setItem(modeKey,value);}catch(_){}try{localStorage.setItem(modeKey,value);}catch(_){}};
                const __cpmClearState=()=>{try{sessionStorage.removeItem(modeKey);}catch(_){}try{localStorage.removeItem(modeKey);}catch(_){}};
                let __cpmLatched=__cpmStage.stage==='MODE_CONFIRMED'&&__cpmStage.confirmedMode===requestedMode;
                const __cpmExplicitMode=__cpmCurrentMode==='chat'||__cpmCurrentMode==='work'?__cpmCurrentMode:'';
                const __cpmContradiction=__cpmLatched&&!!__cpmExplicitMode&&__cpmExplicitMode!==requestedMode;
                if(__cpmContradiction){__cpmStage={stage:'MODE_PENDING',requested:requestedMode,confirmedMode:'',confirmedAt:0,regressionsBlocked:Number(__cpmStage.regressionsBlocked)||0};__cpmSaveStage();__cpmClearState();__cpmState={startedAt:0,attempts:0,clickAttempts:0,lastClickAt:0,lastAction:'',requested:requestedMode};__cpmLatched=false;}
                const __cpmNow=Date.now();
                if(!(Number(__cpmState.startedAt)>0))__cpmState.startedAt=__cpmNow;
                if(!__cpmLatched)__cpmState.attempts=Math.max(0,Number(__cpmState.attempts)||0)+1;
                __cpmState.requested=requestedMode;
                const __cpmElapsedMs=Math.max(0,__cpmNow-Number(__cpmState.startedAt||__cpmNow));
                const __cpmRecentClick=Number(__cpmState.lastClickAt)>0&&__cpmNow-Number(__cpmState.lastClickAt)<__cpmRetryMs;
                const __cpmMouse=(element,type,buttons)=>{try{return element.dispatchEvent(new MouseEvent(type,{bubbles:true,cancelable:true,composed:true,button:0,buttons,view:window}));}catch(_){return false;}};
                const __cpmActivate=element=>{if(!element)return;element.focus?.();__cpmMouse(element,'pointerdown',1);if(__cpmSelectedState(element))return;__cpmMouse(element,'mousedown',1);if(__cpmSelectedState(element))return;__cpmMouse(element,'pointerup',0);__cpmMouse(element,'mouseup',0);if(!__cpmSelectedState(element))element.click?.();};
                const __cpmGroupedReadback=__cpmTargetFound&&__cpmTargetSelected&&__cpmCurrentMode===requestedMode&&__cpmSelectedModes.length===1;
                let __cpmReadback=__cpmLatched||__cpmGroupedReadback;
                let modeSelected=__cpmReadback;
                let modePrior=Number(__cpmState.clickAttempts)>0?JSON.stringify({clickAttempts:Number(__cpmState.clickAttempts)||0,lastClickAt:Number(__cpmState.lastClickAt)||0}):'';
                let modeClicked=false,modeAction='';
                if(mode&&!modeSelected&&!modePrior&&!__cpmRecentClick&&Number(__cpmState.clickAttempts)<2){modeAction='select-mode';__cpmState.clickAttempts=Math.max(0,Number(__cpmState.clickAttempts)||0)+1;__cpmState.lastClickAt=__cpmNow;__cpmState.lastAction=modeAction;__cpmSaveState();__cpmActivate(mode);modeClicked=true;modePrior=JSON.stringify({clickAttempts:__cpmState.clickAttempts,lastClickAt:__cpmState.lastClickAt});}
                else if(mode&&!modeSelected&&modePrior&&!__cpmRecentClick&&Number(__cpmState.clickAttempts)<2){modeAction='retry-mode';__cpmState.clickAttempts=Math.max(0,Number(__cpmState.clickAttempts)||0)+1;__cpmState.lastClickAt=__cpmNow;__cpmState.lastAction=modeAction;__cpmSaveState();__cpmActivate(mode);modeClicked=true;modePrior=JSON.stringify({clickAttempts:__cpmState.clickAttempts,lastClickAt:__cpmState.lastClickAt});}
                if(__cpmReadback&&!__cpmLatched){__cpmStage.stage='MODE_CONFIRMED';__cpmStage.confirmedMode=requestedMode;__cpmStage.confirmedAt=__cpmNow;__cpmSaveStage();__cpmLatched=true;}
                const __cpmRegressionBlocked=__cpmLatched&&(__cpmCurrentMode==='unknown'||__cpmCurrentMode==='ambiguous');
                if(__cpmRegressionBlocked){__cpmStage.regressionsBlocked=Math.max(0,Number(__cpmStage.regressionsBlocked)||0)+1;__cpmSaveStage();}
                if(__cpmLatched)__cpmReadback=true;
                modeSelected=__cpmReadback;
                const __cpmDiagnostics=()=>({requested:requestedMode,ready:modeSelected,candidateFound:__cpmTargetFound,candidateLabel:mode?__cpmLabel(mode):'',selected:modeSelected,clicked:modeClicked,priorClick:!!modePrior,stage:__cpmStage.stage,confirmedMode:__cpmStage.confirmedMode,currentMode:__cpmCurrentMode,modeLatched:__cpmLatched,stageRegressionBlocked:__cpmRegressionBlocked,explicitContradiction:__cpmContradiction,targetSource:__cpmSource,targetFound:__cpmTargetFound,targetSelected:__cpmTargetSelected,targetTag:mode?.tagName||'',targetRole:mode?.getAttribute?.('role')||'',targetToggleValue:mode?.getAttribute?.('data-tpp-toggle-value')||'',selectedModes:__cpmSelectedModes,action:modeAction,recentClick:__cpmRecentClick,modeAttempts:__cpmState.attempts,modeClickAttempts:__cpmState.clickAttempts,modeElapsedMs:__cpmElapsedMs,modeTimeoutMs:__cpmTimeoutMs});
                if(modeClicked)return result('RETRY','모드 전환 반영 대기',{...routeDiagnostics,mode:__cpmDiagnostics()});
                if(!modeSelected){__cpmSaveState();if(!__cpmTargetFound&&(__cpmElapsedMs>=__cpmTimeoutMs||__cpmState.attempts>=__cpmMaxAttempts))return result('CHAT_MODE_CONTROL_NOT_FOUND','실행 모드 선택기를 제한시간 안에 찾지 못했습니다.',{...routeDiagnostics,mode:__cpmDiagnostics()});if(__cpmTargetFound&&Number(__cpmState.clickAttempts)>=2&&(__cpmElapsedMs>=4800||__cpmState.attempts>=8))return result('CHAT_MODE_READBACK_FAILED','실행 모드 선택 후 실제 상태를 확인하지 못했습니다.',{...routeDiagnostics,mode:__cpmDiagnostics()});if(__cpmElapsedMs>=__cpmTimeoutMs||__cpmState.attempts>=__cpmMaxAttempts)return result('CHAT_MODE_READBACK_FAILED','실행 모드 실제 상태 확인이 제한시간을 초과했습니다.',{...routeDiagnostics,mode:__cpmDiagnostics()});return result('RETRY','실행 모드 실제 상태 대기',{...routeDiagnostics,mode:__cpmDiagnostics()});}
                const modeDiagnostics=__cpmDiagnostics();
                __cpmClearState();
                """
                .replace("__REQUESTED__", quote(requested))
                .replace("__RUN_ID__", quote(runId));
    }

    private static String quote(String value) {
        String safe = value == null ? "" : value;
        StringBuilder out = new StringBuilder(safe.length() + 16).append('"');
        for (int i = 0; i < safe.length(); i++) {
            char c = safe.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == '\u2028' || c == '\u2029') out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
