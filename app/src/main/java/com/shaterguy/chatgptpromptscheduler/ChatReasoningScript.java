package com.shaterguy.chatgptpromptscheduler;

/**
 * Meaning-based Chat reasoning selector adapted from SelfRun Drive 1.4.3.
 * The current Chat UI is traversed as reasoning trigger -> slider sheet -> Advanced ->
 * reasoning menu. Slider values are observed only to identify the intermediate sheet and
 * are never mutated directly.
 */
final class ChatReasoningScript {
    private ChatReasoningScript() {}

    static String inline(String selection, String runId) {
        String wanted = Schedule.normalizedChatReasoning("chat", selection);
        String modeReadbackGate = "if(modeDiagnostics.candidateFound&&!modeDiagnostics.selected)return result('RETRY','Chat 모드 선택 상태 확인 대기',{...routeDiagnostics,mode:modeDiagnostics});";
        if ("keep".equals(wanted)) {
            return modeReadbackGate + "const reasoningDiagnostics={requested:'keep',ready:true,action:'',skipped:true};";
        }
        String script = """
                const __cpsWanted=__WANTED__,__cpsRunId=__RUN_ID__;
                const __cpsReasoningOutcome=(()=>{
                  const __cpsLevel=source=>{
                    let value=exactText(source).replace(/^[✓✔☑●•·\\s]+/,'');
                    if(/^(extra high|very high|xhigh|maximum|매우\\s*높음|최대)(?:\\s|$)/.test(value))return'xhigh';
                    if(/^(pro|프로)(?:\\s|$)/.test(value))return'pro';
                    if(/^(medium|중간|표준|standard)(?:\\s|$)/.test(value))return'medium';
                    if(/^(high|높음|extended|확장)(?:\\s|$)/.test(value))return'high';
                    if(/^(instant|flash|빠른|즉시)(?:\\s|$)/.test(value))return'instant';
                    return'';
                  };
                  const __cpsPopupSelector='[role="menu"],[role="listbox"],[role="dialog"],[data-radix-popper-content-wrapper],[data-slot*="popover-content"],[data-slot*="menu-content"],[data-slot*="sheet-content"]';
                  const __cpsInteractive='button,[role="button"],[role="menuitem"],[role="menuitemradio"],[role="radio"],[role="option"],[aria-haspopup],[aria-expanded],[data-value]';
                  const __cpsForbidden=element=>/(send|submit|보내기|stop|중지|microphone|마이크|voice|음성|attach|첨부|upload|업로드|new chat|new conversation|새 채팅|새 대화)/.test(elementLabel(element)+' '+exactText(element?.dataset?.testid||''));
                  const __cpsOwner=element=>element?.closest?.(__cpsInteractive)||element||null;
                  const __cpsLabel=element=>{const owner=__cpsOwner(element)||element;return exactText(owner?.getAttribute?.('aria-label')||'')||elementLabel(owner);};
                  const __cpsActiveView=element=>!!element&&!element.closest?.('[inert],[aria-hidden="true"],[data-active="false"]');
                  const __cpsReasoningRowLabel=label=>/^(reasoning(?:\\s+(?:level|effort))?|추론(?:\\s*(?:수준|강도|정도)))(?:\\s|$)/.test(label);
                  const __cpsShowAdvancedLabel=label=>/^(?:show\\s+advanced(?:\\s+options)?|advanced(?:\\s+options)?|고급(?:\\s+옵션)?(?:\\s+표시)?)(?:\\s|$)/.test(label);
                  const __cpsDirectLevel=element=>{
                    const owner=__cpsOwner(element);if(!owner)return'';
                    const label=__cpsLabel(owner);
                    if(__cpsReasoningRowLabel(label)||/^(model|모델)(?:\\s|$)/.test(label))return'';
                    const role=exactText(owner.getAttribute?.('role')||'');
                    if(!/^(menuitemradio|radio|option|menuitem)$/.test(role)&&owner.tagName!=='BUTTON'&&!owner.hasAttribute?.('data-value'))return'';
                    return __cpsLevel(label);
                  };
                  const __cpsInput=composerInput||document.querySelector('#prompt-textarea')||[...document.querySelectorAll('textarea,[contenteditable="true"]')].filter(visible).sort((a,b)=>b.getBoundingClientRect().bottom-a.getBoundingClientRect().bottom)[0]||null;
                  const __cpsForm=__cpsInput?.closest?.('form')||null;
                  const __cpsNear=element=>{
                    if(!element||!__cpsInput)return false;
                    if(__cpsForm?.contains?.(element))return true;
                    const a=element.getBoundingClientRect?.(),b=__cpsInput.getBoundingClientRect?.();
                    return !!a&&!!b&&a.bottom>=b.top-280&&a.top<=b.bottom+280&&a.right>=b.left-380&&a.left<=b.right+380;
                  };
                  const __cpsTriggerScore=element=>{
                    const label=elementLabel(element),testid=exactText(element?.dataset?.testid||''),popup=exactText(element.getAttribute?.('aria-haspopup')||'');
                    let score=0;if(__cpsLevel(label))score+=170;if(/reason|thinking|추론/.test(label+' '+testid))score+=75;if(/model|모델|gpt|flash/.test(label+' '+testid))score+=30;
                    if(/^(menu|listbox|dialog|true)$/.test(popup))score+=45;if(element.hasAttribute?.('aria-expanded'))score+=35;if(element.hasAttribute?.('aria-controls')||element.hasAttribute?.('aria-owns'))score+=25;if(__cpsNear(element))score+=190;return score;
                  };
                  const __cpsTriggerEntries=[...document.querySelectorAll('button,[role="button"],[role="combobox"],[aria-haspopup],[aria-expanded],[data-testid*="model"],[data-testid*="reason"]')]
                    .filter(visible).filter(element=>!element.closest(__cpsPopupSelector)).filter(element=>!__cpsForbidden(element))
                    .map((element,index)=>({element,index,score:__cpsTriggerScore(element)})).filter(entry=>entry.score>0)
                    .sort((a,b)=>b.score-a.score||a.index-b.index);
                  const __cpsAnimatedMark=[...document.querySelectorAll('[data-animated-slider-trigger="true"]')].find(visible)||null;
                  const __cpsAnimatedTrigger=__cpsOwner(__cpsAnimatedMark);
                  const __cpsExactTrigger=__cpsAnimatedTrigger&&visible(__cpsAnimatedTrigger)&&!__cpsAnimatedTrigger.closest(__cpsPopupSelector)&&!__cpsForbidden(__cpsAnimatedTrigger)&&__cpsNear(__cpsAnimatedTrigger)?__cpsAnimatedTrigger:null;
                  const __cpsTrigger=__cpsExactTrigger||__cpsTriggerEntries[0]?.element||null;
                  const __cpsTriggerLevel=__cpsTrigger?__cpsLevel(__cpsLabel(__cpsTrigger)):'';
                  const __cpsTriggerOpen=!!__cpsTrigger&&(__cpsTrigger.getAttribute?.('aria-expanded')==='true'||exactText(__cpsTrigger.dataset?.state||'')==='open');
                  const __cpsControlledIds=__cpsTrigger?String(__cpsTrigger.getAttribute('aria-controls')||__cpsTrigger.getAttribute('aria-owns')||'').split(/\\s+/).filter(Boolean):[];
                  const __cpsControlled=__cpsControlledIds.map(id=>document.getElementById(id)).find(visible)||null;
                  const __cpsOpenPopups=[...document.querySelectorAll(__cpsPopupSelector)].filter(visible);
                  const __cpsPopupOwners=popup=>{const owners=[];for(const raw of popup.querySelectorAll(__cpsInteractive)){const owner=__cpsOwner(raw);if(owner&&visible(owner)&&__cpsActiveView(owner)&&!owners.includes(owner))owners.push(owner);}return owners;};
                  const __cpsReasoningPopup=popup=>{
                    if(!popup)return false;
                    if(!!popup.querySelector('[role="slider"],input[type="range"]'))return true;
                    const elements=__cpsPopupOwners(popup);
                    if(elements.some(element=>__cpsShowAdvancedLabel(__cpsLabel(element))&&!__cpsDirectLevel(element)))return true;
                    if(elements.some(element=>__cpsReasoningRowLabel(__cpsLabel(element))&&!__cpsDirectLevel(element)))return true;
                    const levels=[...new Set(elements.map(__cpsDirectLevel).filter(Boolean))];
                    return levels.length>=2;
                  };
                  const __cpsFallbackPopups=__cpsTriggerOpen?__cpsOpenPopups.filter(__cpsReasoningPopup):[];
                  const __cpsPopups=[__cpsControlled,...__cpsFallbackPopups].filter((popup,index,all)=>popup&&all.indexOf(popup)===index);
                  const __cpsPopupElements=[];
                  for(const popup of __cpsPopups)for(const owner of __cpsPopupOwners(popup))if(!__cpsPopupElements.includes(owner))__cpsPopupElements.push(owner);
                  const __cpsSliders=[...document.querySelectorAll('[role="slider"],input[type="range"]')].filter(visible);
                  const __cpsSliderObserved=__cpsSliders.some(slider=>__cpsPopups.some(popup=>popup.contains(slider)));
                  const __cpsAdvancedButtons=__cpsPopupElements.filter(element=>__cpsShowAdvancedLabel(__cpsLabel(element))&&!__cpsDirectLevel(element));
                  const __cpsAdvancedButton=__cpsAdvancedButtons.find(element=>__cpsPopups.some(popup=>popup.contains(element)&&!!popup.querySelector('[role="slider"],input[type="range"]')))||__cpsAdvancedButtons[0]||null;
                  const __cpsReasoningRows=__cpsPopupElements.filter(element=>__cpsReasoningRowLabel(__cpsLabel(element))&&!__cpsDirectLevel(element));
                  const __cpsReasoningRow=__cpsReasoningRows[0]||null;
                  const __cpsDirectEntries=__cpsPopupElements.map((element,index)=>({element,index,level:__cpsDirectLevel(element)})).filter(entry=>!!entry.level);
                  const __cpsWantedOption=__cpsDirectEntries.find(entry=>entry.level===__cpsWanted)||null;
                  const __cpsSelectedLevels=[...new Set(__cpsDirectEntries.filter(entry=>selectedState(entry.element)).map(entry=>entry.level))];
                  const __cpsStateKey='chatgpt-prompt-scheduler:chat-reasoning:'+__cpsRunId;
                  const __cpsNow=Date.now(),__cpsOverallTimeoutMs=24000,__cpsRenderTimeoutMs=9000,__cpsRetryMs=3600,__cpsMaxAttempts=28;
                  let __cpsState={startedAt:0,requested:'',attempts:0,triggerClicks:0,advancedClicks:0,reasoningClicks:0,optionClicks:0,closeAttempts:0,pending:false,lastAction:'',lastActionAt:0};
                  try{const saved=sessionStorage.getItem(__cpsStateKey)||localStorage.getItem(__cpsStateKey)||'';if(saved)__cpsState={...__cpsState,...JSON.parse(saved)};}catch(_){}
                  if(__cpsState.requested&&__cpsState.requested!==__cpsWanted)__cpsState={startedAt:0,requested:__cpsWanted,attempts:0,triggerClicks:0,advancedClicks:0,reasoningClicks:0,optionClicks:0,closeAttempts:0,pending:false,lastAction:'',lastActionAt:0};
                  if(!(Number(__cpsState.startedAt)>0))__cpsState.startedAt=__cpsNow;
                  __cpsState.requested=__cpsWanted;__cpsState.attempts=Math.max(0,Number(__cpsState.attempts)||0)+1;
                  const __cpsElapsedMs=Math.max(0,__cpsNow-Number(__cpsState.startedAt||__cpsNow));
                  const __cpsSinceActionMs=Number(__cpsState.lastActionAt)>0?Math.max(0,__cpsNow-Number(__cpsState.lastActionAt)):Number.MAX_SAFE_INTEGER;
                  const __cpsSave=()=>{const value=JSON.stringify(__cpsState);try{sessionStorage.setItem(__cpsStateKey,value);}catch(_){}try{localStorage.setItem(__cpsStateKey,value);}catch(_){}};
                  const __cpsClear=()=>{try{sessionStorage.removeItem(__cpsStateKey);}catch(_){}try{localStorage.removeItem(__cpsStateKey);}catch(_){}};
                  const __cpsStage=__cpsWantedOption?'OPTION':(__cpsAdvancedButton?'ADVANCED_BUTTON':(__cpsReasoningRow?'REASONING_MENU':(__cpsSliderObserved?'SLIDER_SHEET':(__cpsPopups.length?'UNKNOWN_POPUP':'TRIGGER'))));
                  const __cpsDiagnostics=extra=>({strategy:'advanced-menu',stage:__cpsStage,requested:__cpsWanted,triggerFound:!!__cpsTrigger,exactAnimatedTrigger:!!__cpsExactTrigger,triggerCandidates:__cpsTriggerEntries.length,triggerLabel:__cpsTrigger?__cpsLabel(__cpsTrigger):'',triggerLevel:__cpsTriggerLevel,triggerExpanded:__cpsTrigger?.getAttribute?.('aria-expanded')||'',triggerState:__cpsTrigger?.getAttribute?.('data-state')||'',triggerOpen:__cpsTriggerOpen,globalPopupCandidates:__cpsOpenPopups.length,fallbackPopupCandidates:__cpsFallbackPopups.length,popupCandidates:__cpsPopups.length,sliderObserved:__cpsSliderObserved,advancedButtonFound:!!__cpsAdvancedButton,reasoningRowFound:!!__cpsReasoningRow,directOptionCandidates:__cpsDirectEntries.length,wantedOptionFound:!!__cpsWantedOption,selectedLevels:__cpsSelectedLevels,attempts:__cpsState.attempts,triggerClicks:__cpsState.triggerClicks,advancedClicks:__cpsState.advancedClicks,reasoningClicks:__cpsState.reasoningClicks,optionClicks:__cpsState.optionClicks,closeAttempts:__cpsState.closeAttempts,pending:!!__cpsState.pending,lastAction:__cpsState.lastAction||'',elapsedMs:__cpsElapsedMs,overallTimeoutMs:__cpsOverallTimeoutMs,...extra});
                  const __cpsReady=(observed,extra={})=>{const diagnostics=__cpsDiagnostics({observed,...extra});__cpsClear();return{kind:'ready',detail:'Chat 추론 고급 메뉴 의미값 적용 확인',diagnostics};};
                  const __cpsWait=(detail,extra={})=>{__cpsSave();return{kind:'retry',detail,diagnostics:__cpsDiagnostics(extra)};};
                  const __cpsFail=(code,detail,extra={})=>{__cpsSave();return{kind:'error',code,detail,diagnostics:__cpsDiagnostics(extra)};};
                  const __cpsDesired=element=>element?.getAttribute?.('aria-expanded');
                  const __cpsReached=(element,want)=>__cpsDesired(element)!==null&&__cpsDesired(element)===String(want);
                  const __cpsMouse=(element,type,buttons)=>{try{return element.dispatchEvent(new MouseEvent(type,{bubbles:true,cancelable:true,composed:true,button:0,buttons,view:window}));}catch(_){return false;}};
                  const __cpsToggleMenu=(element,want)=>{if(!element)return;element.focus?.();const tracked=__cpsDesired(element)!==null;if(tracked&&__cpsReached(element,want))return;__cpsMouse(element,'pointerdown',1);if(tracked&&__cpsReached(element,want))return;__cpsMouse(element,'mousedown',1);if(tracked&&__cpsReached(element,want))return;__cpsMouse(element,'pointerup',0);__cpsMouse(element,'mouseup',0);if(!tracked||!__cpsReached(element,want))element.click?.();};
                  const __cpsActivate=element=>{const target=__cpsOwner(element)||element;if(!target)return;if(target.getAttribute?.('aria-expanded')!==null||target.hasAttribute?.('aria-haspopup'))__cpsToggleMenu(target,true);else{target.focus?.();__cpsMouse(target,'pointerdown',1);__cpsMouse(target,'mousedown',1);__cpsMouse(target,'pointerup',0);__cpsMouse(target,'mouseup',0);if(target.isConnected)target.click?.();}};
                  const __cpsClose=()=>{if(__cpsTrigger&&__cpsTrigger.getAttribute?.('aria-expanded')==='true'){__cpsToggleMenu(__cpsTrigger,false);return'trigger';}document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',code:'Escape',bubbles:true,cancelable:true}));return'escape';};
                  const __cpsMayClick=(count,max)=>Number(count)<1||(__cpsSinceActionMs>=__cpsRetryMs&&Number(count)<max);
                  if(__cpsTriggerLevel===__cpsWanted){
                    if(__cpsPopups.length===0)return __cpsReady(__cpsTriggerLevel,{action:'already-selected'});
                    if(__cpsMayClick(__cpsState.closeAttempts,3)){__cpsState.closeAttempts++;__cpsState.lastAction='close-current-match';__cpsState.lastActionAt=__cpsNow;__cpsSave();const method=__cpsClose();return __cpsWait('현재 추론 수준이 목표와 같아 열린 메뉴 닫힘 확인 대기',{action:'close-current-match',closeMethod:method});}
                    if(__cpsElapsedMs>=__cpsOverallTimeoutMs)return __cpsFail('CHAT_REASONING_MENU_CLOSE_FAILED','현재 추론 수준 확인 후 메뉴가 닫히지 않았습니다.',{action:'current-match-close-timeout'});
                    return __cpsWait('현재 추론 수준 확인 후 메뉴 닫힘 대기',{action:'wait-current-match-close'});
                  }
                  if(__cpsWantedOption&&selectedState(__cpsWantedOption.element)){
                    if(__cpsPopups.length===0)return __cpsReady(__cpsWanted,{action:'selected-option-readback'});
                    if(__cpsMayClick(__cpsState.closeAttempts,3)){__cpsState.closeAttempts++;__cpsState.lastAction='close-menu';__cpsState.lastActionAt=__cpsNow;__cpsSave();const method=__cpsClose();return __cpsWait('Chat 추론 메뉴 닫힘 확인 대기',{action:'close-menu',closeMethod:method});}
                    if(__cpsElapsedMs>=__cpsOverallTimeoutMs)return __cpsFail('CHAT_REASONING_MENU_CLOSE_FAILED','Chat 추론 선택 후 메뉴가 닫히지 않았습니다.',{action:'menu-close-timeout'});
                    return __cpsWait('Chat 추론 메뉴 닫힘 대기',{action:'wait-menu-close'});
                  }
                  if(__cpsWantedOption){
                    const action=(Number(__cpsState.advancedClicks)>0||Number(__cpsState.reasoningClicks)>0||__cpsSliderObserved)?'nested-option-click':'direct-option-click';
                    if(__cpsMayClick(__cpsState.optionClicks,2)){__cpsState.pending=true;__cpsState.optionClicks++;__cpsState.lastAction=action;__cpsState.lastActionAt=__cpsNow;__cpsSave();__cpsActivate(__cpsWantedOption.element);return __cpsWait('Chat 추론 메뉴 옵션 반영 대기',{action});}
                    if(__cpsElapsedMs>=__cpsOverallTimeoutMs||__cpsState.attempts>=__cpsMaxAttempts)return __cpsFail('CHAT_REASONING_READBACK_MISMATCH','Chat 추론 메뉴 옵션 선택 상태를 확인하지 못했습니다.',{action:'option-readback-timeout'});
                    return __cpsWait('Chat 추론 옵션 선택 상태 대기',{action:'wait-option-readback'});
                  }
                  if(__cpsState.pending){
                    if(__cpsTriggerLevel===__cpsWanted&&__cpsPopups.length===0)return __cpsReady(__cpsTriggerLevel,{action:'trigger-readback'});
                    if(__cpsElapsedMs>=__cpsOverallTimeoutMs||__cpsState.attempts>=__cpsMaxAttempts)return __cpsFail('CHAT_REASONING_READBACK_MISMATCH','Chat 추론 옵션 적용 후 의미값을 확인하지 못했습니다.',{action:'pending-readback-timeout'});
                    return __cpsWait('Chat 추론 옵션 적용 readback 대기',{action:'wait-pending-readback'});
                  }
                  if(__cpsAdvancedButton){
                    if(__cpsMayClick(__cpsState.advancedClicks,2)){__cpsState.advancedClicks++;__cpsState.lastAction='open-advanced-control';__cpsState.lastActionAt=__cpsNow;__cpsSave();__cpsActivate(__cpsAdvancedButton);return __cpsWait('추론 슬라이드의 고급 버튼 반영 대기',{action:'open-advanced-control'});}
                    if(__cpsElapsedMs>=__cpsOverallTimeoutMs||__cpsState.attempts>=__cpsMaxAttempts)return __cpsFail('CHAT_REASONING_OPTION_UNAVAILABLE','고급 메뉴 전환 후 추론 옵션을 찾지 못했습니다.',{action:'advanced-transition-timeout'});
                    return __cpsWait('고급 메뉴 전환 확인 대기',{action:'wait-advanced-transition'});
                  }
                  if(__cpsReasoningRow){
                    if(__cpsMayClick(__cpsState.reasoningClicks,2)){__cpsState.reasoningClicks++;__cpsState.lastAction='open-reasoning-menu';__cpsState.lastActionAt=__cpsNow;__cpsSave();__cpsActivate(__cpsReasoningRow);return __cpsWait('고급 메뉴 추론 수준 선택기 열기 반영 대기',{action:'open-reasoning-menu'});}
                    if(__cpsElapsedMs>=__cpsOverallTimeoutMs||__cpsState.attempts>=__cpsMaxAttempts)return __cpsFail('CHAT_REASONING_OPTION_UNAVAILABLE','추론 수준 메뉴에서 요청 옵션을 찾지 못했습니다.',{action:'reasoning-menu-timeout'});
                    return __cpsWait('추론 수준 메뉴 옵션 렌더링 대기',{action:'wait-reasoning-options'});
                  }
                  if(__cpsSliderObserved){
                    if(__cpsElapsedMs>=__cpsRenderTimeoutMs||__cpsState.attempts>=14)return __cpsFail('CHAT_REASONING_ADVANCED_CONTROL_NOT_FOUND','추론 슬라이드에서 고급 버튼을 찾지 못했습니다.',{action:'advanced-control-timeout'});
                    return __cpsWait('추론 슬라이드 고급 버튼 렌더링 대기',{action:'wait-advanced-control'});
                  }
                  if(__cpsPopups.length===0&&__cpsTrigger){
                    if(__cpsMayClick(__cpsState.triggerClicks,2)){__cpsState.triggerClicks++;__cpsState.lastAction='open-reasoning-sheet';__cpsState.lastActionAt=__cpsNow;__cpsSave();__cpsActivate(__cpsTrigger);return __cpsWait('현재 추론 정도 클릭 후 슬라이드 열림 대기',{action:'open-reasoning-sheet'});}
                    if(__cpsElapsedMs>=__cpsOverallTimeoutMs||__cpsState.attempts>=__cpsMaxAttempts)return __cpsFail('CHAT_REASONING_TRIGGER_NOT_FOUND','현재 추론 정도 제어를 제한시간 안에 열지 못했습니다.',{action:'trigger-timeout'});
                    return __cpsWait('현재 추론 정도 슬라이드 열림 확인 대기',{action:'wait-reasoning-sheet'});
                  }
                  if(__cpsDirectEntries.length>0)return __cpsFail('CHAT_REASONING_OPTION_UNAVAILABLE','현재 고급 추론 메뉴에 요청한 옵션이 없습니다.',{action:'requested-option-unavailable'});
                  if(__cpsPopups.length>0){
                    if(__cpsElapsedMs>=__cpsRenderTimeoutMs||__cpsState.attempts>=14)return __cpsFail('CHAT_REASONING_OPTION_UNAVAILABLE','열린 추론 UI에서 고급 버튼 또는 메뉴 옵션을 찾지 못했습니다.',{action:'unrecognized-popup-timeout'});
                    return __cpsWait('추론 UI 렌더링 대기',{action:'wait-popup-content'});
                  }
                  if(__cpsElapsedMs>=__cpsOverallTimeoutMs||__cpsState.attempts>=__cpsMaxAttempts)return __cpsFail('CHAT_REASONING_TRIGGER_NOT_FOUND','현재 추론 정도 제어를 찾지 못했습니다.',{action:'missing-trigger-timeout'});
                  return __cpsWait('현재 추론 정도 제어 탐색 대기',{action:'wait-trigger'});
                })();
                if(__cpsReasoningOutcome.kind==='retry')return result('RETRY',__cpsReasoningOutcome.detail,{...routeDiagnostics,mode:modeDiagnostics,model:modelDiagnostics,reasoning:__cpsReasoningOutcome.diagnostics});
                if(__cpsReasoningOutcome.kind==='error')return result(__cpsReasoningOutcome.code,__cpsReasoningOutcome.detail,{...routeDiagnostics,mode:modeDiagnostics,model:modelDiagnostics,reasoning:__cpsReasoningOutcome.diagnostics});
                const reasoningDiagnostics=__cpsReasoningOutcome.diagnostics;
                """;
        return modeReadbackGate + script
                .replace("__WANTED__", quote(wanted))
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
