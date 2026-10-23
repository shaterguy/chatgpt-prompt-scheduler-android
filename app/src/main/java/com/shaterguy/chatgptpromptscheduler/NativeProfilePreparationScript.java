package com.shaterguy.chatgptpromptscheduler;

/** Saves ChatGPT's own last-used preferences before entering a fresh composer. */
final class NativeProfilePreparationScript {
    private NativeProfilePreparationScript() {}

    static String homeUrl(RequestProfileEngine.Mode mode) {
        return "https://chatgpt.com/?surface=" + (mode == RequestProfileEngine.Mode.WORK ? "work" : "chat")
                + "&disable_auto_send=1";
    }

    static String targetUrl(Schedule schedule, RequestProfileEngine.Mode mode) {
        if ("project".equals(schedule.targetType)) {
            ProjectUrlPolicy.ProjectRef ref = ProjectUrlPolicy.parseProject(schedule.targetUrl);
            if (ref == null) throw new IllegalArgumentException("PROJECT_URL_INVALID");
            return ref.canonicalUrl;
        }
        return homeUrl(mode);
    }

    static String profileJson(RequestProfileEngine.TargetProfile target) {
        String model = null, effort = null, effortOperation = null;
        for (RequestProfileEngine.Operation op : RequestProfileEngine.plan(target).operations) {
            if ("model".equals(op.path) && op.kind == RequestProfileEngine.OperationKind.SET) model = op.value;
            if ("thinking_effort".equals(op.path)) {
                effort = op.value;
                effortOperation = op.kind.name();
            }
        }
        if (model == null || effortOperation == null) throw new IllegalArgumentException("NATIVE_PROFILE_INVALID");
        return "{\"mode\":" + q(target.mode == RequestProfileEngine.Mode.WORK ? "work" : "chat")
                + ",\"model\":" + q(model) + ",\"thinking_effort\":" + (effort == null ? "null" : q(effort))
                + ",\"effortOperation\":" + q(effortOperation) + "}";
    }

    static String key(RequestProfileEngine.TargetProfile target, String runId) {
        return runId + "|" + profileJson(target);
    }

    /** evaluateJavascript does not await Promises: start once, then poll the same document state. */
    static String build(RequestProfileEngine.TargetProfile target, String runId, boolean apply) {
        return "(()=>{const profile=" + profileJson(target) + ",key=" + q(key(target, runId))
                + ",phase=" + q(apply ? "prepare" : "verify") + ",markerKey=" + q("chatgpt-prompt-scheduler:submit:" + runId) + ";"
                + """
                  const result=(status,detail='',diagnostics={})=>JSON.stringify({status,detail,url:location.href,diagnostics});
                  if(location.origin!=='https://chatgpt.com')return result('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트 확인 실패');
                  let marker='';try{marker=localStorage.getItem(markerKey)||sessionStorage.getItem(markerKey)||window[markerKey]||'';}catch(_){marker=window[markerKey]||'';}
                  if(marker)return result('SUBMITTED','동일 실행의 전송 클릭 기록을 확인했습니다.');
                  if(/^\\/auth\\/(?:login|signup|signin)(?:\\/|$)/.test(location.pathname))return result('AUTH_REQUIRED','ChatGPT 로그인이 필요합니다.');
                  if(document.readyState!=='complete')return result('RETRY','페이지 로딩 완료 대기');
                  const prior=window.__chatgptPromptSchedulerNativeProfile;
                  if(prior&&prior.key===key&&prior.phase===phase)return result(prior.status,prior.detail,{receipt:prior.receipt||null});
                  const composer=[...document.querySelectorAll('#prompt-textarea,textarea[data-testid="prompt-textarea"],[contenteditable="true"][data-lexical-editor="true"],main form [contenteditable="true"]')].find(e=>e.isConnected&&e.offsetParent!==null);
                  if(!composer){
                    const auth=[...document.querySelectorAll('a[href],button')].some(e=>e.offsetParent!==null&&/^(?:log in|sign in|sign up|로그인|회원가입)$/i.test(String(e.innerText||e.textContent||'').trim()));
                    return result(auth?'AUTH_REQUIRED':'RETRY',auth?'ChatGPT 로그인이 필요합니다.':'입력창 대기');
                  }
                  const state={key,phase,status:'RETRY',detail:'모델·추론 설정 준비 중',receipt:null};
                  window.__chatgptPromptSchedulerNativeProfile=state;
                """
                + preferenceFunction() + environmentFunction() + readbackFunction()
                + """
                  (async()=>{
                    const env=await nativeEnvironment();
                    if(phase==='prepare'){
                      state.receipt=await applyPreference(env,profile);
                      state.status='PREPARED';state.detail='모델·추론 설정 저장 완료';
                    }else{
                      state.receipt={mode:profile.mode,model:profile.model,effort:profile.thinking_effort,nativeMatches:matchesPreference(env,profile)};
                      if(!state.receipt.nativeMatches)throw Error('NATIVE_SELECTION_MISMATCH');
                      state.status='READY';state.detail='진입 화면의 모델·추론 설정 확인 완료';
                    }
                  })().catch(e=>{
                    state.status='NATIVE_PREFERENCE_FAILED';
                    const code=String(e?.message||'');
                    state.detail=['SERVER_READBACK_MISMATCH','NATIVE_SELECTION_MISMATCH','NATIVE_RUNTIME_UNAVAILABLE'].includes(code)?code:'모델·추론 설정 저장 또는 확인 실패';
                  });
                  return result(state.status,state.detail,{receipt:state.receipt});
                })()
                """;
    }

    static String preferenceFunction() {
        return """
                async function applyPreference(env,profile,strategy='combined'){
                  if(env.origin!=='https://chatgpt.com')throw new Error('ChatGPT 화면에서 실행해야 합니다.');
                  if(!['chat','work'].includes(profile?.mode)||!['combined','native','server'].includes(strategy))throw new Error('테스트 조합이 올바르지 않습니다.');
                  const removesEffort=profile.mode==='chat'&&profile.effortOperation==='REMOVE'&&profile.thinking_effort===null;
                  if(typeof profile.model!=='string'||!/^[-a-zA-Z0-9_.]{1,128}$/.test(profile.model)||(!removesEffort&&!['standard','extended','min','max','ultra','xhigh','zero'].includes(profile.thinking_effort)))throw new Error('프로필 원본값이 올바르지 않습니다.');
                  const selection={slug:profile.model,thinkingEffort:profile.thinking_effort};
                  const receipt={mode:profile.mode,model:profile.model,effort:profile.thinking_effort,strategy,serverAcknowledged:null,serverReadback:null,nativeMatches:null};
                  if(strategy!=='native'){
                    const endpoint=profile.mode==='work'?'/settings/user_tpp_last_used_model_config':'/settings/user_last_used_model_config';
                    const query={model_slug:profile.model};
                    if(!removesEffort)query.thinking_effort=profile.thinking_effort;
                    const saved=await env.api.safePatch(endpoint,{parameters:{query}});
                    if(saved?.success!==true)throw new Error('설정 저장 성공 응답을 받지 못했습니다.');
                    receipt.serverAcknowledged=true;
                    if(profile.mode==='work'){
                      try{
                        const raw=await env.api.safeGet('/settings/user',{additionalHeaders:{'Oai-Ep-Cache-Bypass':'true'}});
                        const prefs=raw?.settings?.tpp_last_used_model_config;
                        const model=prefs?.slugs?.web??null,effort=prefs?.juices?.web?.[profile.model]??null;
                        receipt.serverReadback={model,effort,matches:model===profile.model&&effort===profile.thinking_effort};
                      }catch{receipt.serverReadback={unavailable:true};}
                    }
                  }
                  if(strategy!=='server'){
                    if(profile.mode==='chat'){
                      env.atoms.u('chatgpt-last-selected-model-v1',selection);
                      env.atoms.u('chatgpt-last-selected-home-model-v1',selection);
                      const actual=env.atoms.k('chatgpt-last-selected-model-v1',null),home=env.atoms.k('chatgpt-last-selected-home-model-v1',null);
                      receipt.nativeMatches=actual?.slug===selection.slug&&actual?.thinkingEffort===selection.thinkingEffort&&home?.slug===selection.slug&&home?.thinkingEffort===selection.thinkingEffort;
                    }else{
                      let prior={};try{prior=JSON.parse(env.cookie.a('oai-tpp-model-settings')||'{}')||{};}catch{}
                      const cookie={...prior,modelSettings:{defaultModelSlug:prior.modelSettings?.defaultModelSlug??null,lastUsedModelSlug:profile.model},thinkingEffort:profile.thinking_effort,selectionMode:'model'};
                      if(profile.thinking_effort!=='ultra')cookie.lastStartedModelConfig={modelSlug:profile.model,thinkingEffort:profile.thinking_effort};
                      if(env.cookie.b('oai-tpp-model-settings',JSON.stringify(cookie),{maxAge:2592000})!==true)throw new Error('Work 선택값을 저장하지 못했습니다.');
                      env.atoms.u('chatgpt-last-selected-tpp-model-v1',selection);
                      env.atoms.u('chatgpt-work-model-picker-selection-mode-v1','model');
                      let actual=null;try{actual=JSON.parse(env.cookie.a('oai-tpp-model-settings')||'null');}catch{}
                      receipt.nativeMatches=actual?.modelSettings?.lastUsedModelSlug===profile.model&&actual?.thinkingEffort===profile.thinking_effort&&actual?.selectionMode==='model';
                    }
                    if(env.cookie.b('codex_composer_mode',profile.mode,{maxAge:2592000})!==true)throw new Error('Chat·Work 화면 설정을 저장하지 못했습니다.');
                    env.atoms.f();
                    await env.flush();
                  }
                  if(receipt.serverReadback?.matches===false)throw Error('SERVER_READBACK_MISMATCH');
                  if(receipt.nativeMatches!==true)throw Error('NATIVE_SELECTION_MISMATCH');
                  return receipt;
                }
                """;
    }

    static String readbackFunction() {
        return """
                function matchesPreference(env,p){
                  const same=v=>v?.slug===p.model&&v?.thinkingEffort===p.thinking_effort;
                  if(env.cookie.a('codex_composer_mode')!==p.mode)return false;
                  if(p.mode==='chat')return same(env.atoms.k('chatgpt-last-selected-model-v1',null))&&same(env.atoms.k('chatgpt-last-selected-home-model-v1',null));
                  let c=null;try{c=JSON.parse(env.cookie.a('oai-tpp-model-settings')||'null');}catch{}
                  return same(env.atoms.k('chatgpt-last-selected-tpp-model-v1',null))&&env.atoms.k('chatgpt-work-model-picker-selection-mode-v1',null)==='model'&&c?.modelSettings?.lastUsedModelSlug===p.model&&c?.thinkingEffort===p.thinking_effort&&c?.selectionMode==='model';
                }
                """;
    }

    private static String environmentFunction() {
        return """
                async function nativeEnvironment(){
                  const resources=performance.getEntriesByType('resource').map(e=>e.name);
                  const elements=[...document.querySelectorAll('script[src],link[rel="modulepreload"][href]')].map(e=>e.src||e.href);
                  const assets=[...new Set([...elements,...resources])].filter(x=>{try{const u=new URL(x,location.href);return u.origin===location.origin&&/^\\/cdn\\/assets\\/[^/]+\\.js$/.test(u.pathname);}catch{return false;}});
                  let runtime=assets.find(x=>/\\/633146\\.[a-f0-9]+\\.js$/.test(new URL(x).pathname));
                  if(!runtime){
                    for(const candidate of assets.slice(0,4)){
                      const source=await fetch(candidate,{credentials:'same-origin'}).then(r=>{if(!r.ok)throw Error('NATIVE_RUNTIME_UNAVAILABLE');return r.text();});
                      const match=source.match(/import\\s*\\{[^}]*\\b__webpack_require__\\b[^}]*\\}\\s*from\\s*["']([^"']+)["']/);
                      if(match){runtime=new URL(match[1],candidate).href;break;}
                    }
                  }
                  if(!runtime||new URL(runtime).origin!==location.origin||!/^\\/cdn\\/assets\\/[^/]+\\.js$/.test(new URL(runtime).pathname))throw Error('NATIVE_RUNTIME_UNAVAILABLE');
                  const native=await import(runtime),req=native.__webpack_require__;
                  if(typeof req!=='function')throw Error('NATIVE_RUNTIME_UNAVAILABLE');
                  const api=req('Kwur').Request,atoms=req('kpIY'),cookie=req('ZhRn'),flush=req('YAwS').flushBrowserHostStorageWrites;
                  if(typeof api?.safePatch!=='function'||typeof atoms.u!=='function'||typeof atoms.k!=='function'||typeof atoms.f!=='function'||typeof cookie.a!=='function'||typeof cookie.b!=='function'||typeof flush!=='function')throw Error('NATIVE_RUNTIME_UNAVAILABLE');
                  atoms.k('chatgpt-last-selected-model-v1',null);
                  return {origin:location.origin,api,atoms,cookie,flush};
                }
                """;
    }

    private static String q(String value) { return RequestProfileScript.jsQuote(value); }
}
