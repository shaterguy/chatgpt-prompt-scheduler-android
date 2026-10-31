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
    static String build(RequestProfileEngine.TargetProfile target, String runId) {
        return "(()=>{const profile=" + profileJson(target) + ",key=" + q(key(target, runId))
                + ",phase=\"prepare\",markerKey=" + q("chatgpt-prompt-scheduler:submit:" + runId) + ";"
                + """
                  const result=(status,detail='',diagnostics={})=>JSON.stringify({status,detail,url:location.href,diagnostics});
                  if(location.origin!=='https://chatgpt.com')return result('TARGET_CONTEXT_MISMATCH','ChatGPT 호스트 확인 실패');
                  let marker='';try{marker=localStorage.getItem(markerKey)||sessionStorage.getItem(markerKey)||window[markerKey]||'';}catch(_){marker=window[markerKey]||'';}
                  if(marker)return result('SUBMITTED','동일 실행의 전송 클릭 기록을 확인했습니다.');
                  if(/^\\/auth\\/(?:login|signup|signin)(?:\\/|$)/.test(location.pathname))return result('AUTH_REQUIRED','ChatGPT 로그인이 필요합니다.');
                  const prior=window.__chatgptPromptSchedulerNativeProfile;
                  if(prior&&prior.key===key&&prior.phase===phase)return result(prior.status,prior.detail,{receipt:prior.receipt||null,error:prior.error||null});
                  const composer=[...document.querySelectorAll('#prompt-textarea,textarea[data-testid="prompt-textarea"],[contenteditable="true"][data-lexical-editor="true"],main form [contenteditable="true"]')].find(e=>e.isConnected&&e.offsetParent!==null);
                  if(!composer){
                    const auth=[...document.querySelectorAll('a[href],button')].some(e=>e.offsetParent!==null&&/^(?:log in|sign in|sign up|로그인|회원가입)$/i.test(String(e.innerText||e.textContent||'').trim()));
                    return result(auth?'AUTH_REQUIRED':'RETRY',auth?'ChatGPT 로그인이 필요합니다.':'입력창 대기');
                  }
                  const state={key,phase,status:'RETRY',detail:'모델·추론 설정 준비 중',receipt:null};
                  window.__chatgptPromptSchedulerNativeProfile=state;
                """
                + preferenceFunction() + environmentFunction()
                + """
                  (async()=>{
                    const env=await nativeEnvironment();
                    state.receipt=await applyPreference(env,profile);
                    state.status='PREPARED';state.detail='모델·추론 설정 저장 완료';
                  })().catch(e=>{
                    state.status='NATIVE_PREFERENCE_FAILED';
                    const code=String(e?.message||'');
                    state.receipt=e.receipt||null;
                    state.error={phase:e.phase||'runtime-discovery',status:typeof e.status==='number'?e.status:null,message:code.replace(/Bearer\\s+[^\\s]+/gi,'Bearer [redacted]').replace(/([?&](?:token|key|code|auth)[^=]*=)[^&\\s]+/gi,'$1[redacted]').slice(0,300)};
                    state.detail='모델·추론 설정 실패 ['+state.error.phase+']: '+state.error.message;
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
                  const receipt={mode:profile.mode,model:profile.model,effort:profile.thinking_effort,strategy,serverAcknowledged:null,adapterVersion:'0.1.0-dev9'};
                  let phase='server-patch';
                  try {
                  if(strategy!=='native'){
                    const endpoint=profile.mode==='work'?'/settings/user_tpp_last_used_model_config':'/settings/user_last_used_model_config';
                    const query={model_slug:profile.model};
                    if(!removesEffort)query.thinking_effort=profile.thinking_effort;
                    const saved=await env.api.safePatch(endpoint,{parameters:{query}});
                    phase='server-acknowledgement';
                    if(saved?.success!==true)throw new Error('설정 저장 성공 응답을 받지 못했습니다.');
                    receipt.serverAcknowledged=true;
                  }
                  if(strategy!=='server'){
                    phase='native-selection';
                    if(profile.mode==='chat'){
                      env.atoms.u('chatgpt-last-selected-model-v1',selection);
                      env.atoms.u('chatgpt-last-selected-home-model-v1',selection);
                    }else{
                      let prior={};try{prior=JSON.parse(env.cookie.a('oai-tpp-model-settings')||'{}')||{};}catch{}
                      const cookie={...prior,modelSettings:{defaultModelSlug:prior.modelSettings?.defaultModelSlug??null,lastUsedModelSlug:profile.model},thinkingEffort:profile.thinking_effort,selectionMode:'model'};
                      if(profile.thinking_effort!=='ultra')cookie.lastStartedModelConfig={modelSlug:profile.model,thinkingEffort:profile.thinking_effort};
                      await env.cookie.c('oai-tpp-model-settings',JSON.stringify(cookie),{maxAge:2592000});
                      let stored=null;try{stored=JSON.parse(env.cookie.a('oai-tpp-model-settings')||'null');}catch{}
                      receipt.workCookieMatches=stored?.modelSettings?.lastUsedModelSlug===profile.model&&stored?.thinkingEffort===profile.thinking_effort&&stored?.selectionMode==='model';
                      if(!receipt.workCookieMatches)throw new Error('WORK_COOKIE_READBACK_MISMATCH');
                      env.atoms.u('chatgpt-last-selected-tpp-model-v1',selection);
                      env.atoms.u('chatgpt-work-model-picker-selection-mode-v1','model');
                    }
                    phase='composer-mode';
                    await env.cookie.c('codex_composer_mode',profile.mode,{maxAge:2592000});
                    const actual=env.cookie.a('codex_composer_mode');
                    receipt.modeReadback=['chat','work'].includes(actual)?actual:null;
                    receipt.modeMatches=actual===profile.mode;
                    if(!receipt.modeMatches)throw new Error('COMPOSER_MODE_READBACK_MISMATCH');
                    phase='native-publish';
                    await env.atoms.f();
                    phase='durable-flush';
                    await env.flush();
                  }
                  return receipt;
                  } catch(e) {
                    const error=e instanceof Error?e:new Error(typeof e?.message==='string'?e.message:'NATIVE_CALL_REJECTED');
                    if(typeof e?.status==='number')error.status=e.status;
                    error.phase=phase;error.receipt=receipt;throw error;
                  }
                }
                """;
    }

    private static String environmentFunction() {
        return """
                // Self-contained so the exact adapter can also be embedded in Android WebView.
                async function resolveNativeEnvironment(ctx=window){
                  const trace={adapterVersion:'0.1.0-dev9',phase:'assets',modules:{},sources:[],changes:[]};
                  const fail=(code)=>{const e=new Error(code);e.phase=trace.phase;e.diagnostics=trace;throw e;};
                  const origin=ctx.location.origin;
                  if(origin!=='https://chatgpt.com')fail('CHATGPT_ORIGIN_REQUIRED');
                  const valid=x=>{try{const u=new URL(x,ctx.location.href);return u.origin===origin&&/^\\/cdn\\/assets\\/[^/]+\\.js$/.test(u.pathname);}catch{return false;}};
                  const assets=[...new Set([...ctx.document.querySelectorAll('script[src],link[rel="modulepreload"][href]')].map(e=>e.src||e.href).concat(ctx.performance.getEntriesByType('resource').map(e=>e.name)))].filter(valid).map(x=>new URL(x,ctx.location.href).href);
                  trace.assetCount=assets.length;
                  const cached=new Map();
                  const source=async url=>{
                    if(cached.has(url))return cached.get(url);
                    const r=await ctx.fetch(url,{credentials:'same-origin',signal:AbortSignal.timeout(10000)});
                    if(!r.ok)throw Error('ASSET_HTTP_'+r.status);
                    const s=await r.text();if(s.length>8000000)throw Error('ASSET_TOO_LARGE');
                    cached.set(url,s);return s;
                  };
                  let runtime=assets.find(x=>/\\/633146\\.[a-f0-9]+\\.js$/.test(new URL(x).pathname));
                  trace.phase='runtime-discovery';
                  if(!runtime){
                    for(const candidate of assets.slice(0,48)){
                      try{
                        const s=await source(candidate),m=s.match(/import\\s*\\{[^}]*\\b__webpack_require__\\b[^}]*\\}\\s*from\\s*["']([^"']+)["']/);
                        if(m&&valid(new URL(m[1],candidate).href)){runtime=new URL(m[1],candidate).href;break;}
                        if(/export\\s*\\{[^}]*\\b__webpack_require__\\b/.test(s)){runtime=candidate;break;}
                      }catch(e){trace.sources.push({asset:new URL(candidate).pathname,error:String(e.message).slice(0,100)});}
                    }
                  }
                  if(!runtime){
                    // An early-loaded chunk may only reference another chunk which imports the
                    // runtime. Follow actual same-origin asset imports, never a guessed hash.
                    const dependencies=[];
                    for(const [url,s] of cached){
                      for(const match of s.matchAll(/(?:from\\s*|import\\s*)["']([^"']+\\.js)["']/g)){
                        const dep=new URL(match[1],url).href;
                        if(valid(dep)&&!cached.has(dep)&&!dependencies.includes(dep))dependencies.push(dep);
                      }
                    }
                    for(const candidate of dependencies.slice(0,24)){
                      try{
                        const s=await source(candidate),m=s.match(/import\\s*\\{[^}]*\\b__webpack_require__\\b[^}]*\\}\\s*from\\s*["']([^"']+)["']/);
                        if(m&&valid(new URL(m[1],candidate).href)){runtime=new URL(m[1],candidate).href;break;}
                        if(/export\\s*\\{[^}]*\\b__webpack_require__\\b/.test(s)){runtime=candidate;break;}
                      }catch(e){trace.sources.push({asset:new URL(candidate).pathname,error:String(e.message).slice(0,100)});}
                    }
                    trace.dependencyAssetCount=dependencies.length;
                  }
                  if(!runtime){
                    trace.scannedAssetCount=cached.size;
                    const challenge=ctx.document.querySelector?.('#challenge-running,#challenge-form,script[src*="challenges.cloudflare.com"]');
                    fail(challenge?'CHATGPT_VERIFICATION_PAGE':'RUNTIME_NOT_FOUND');
                  }
                  trace.runtime=new URL(runtime).pathname;
                  const imported=ctx.importModule?await ctx.importModule(runtime):await import(runtime);
                  const req=imported.__webpack_require__;
                  if(typeof req!=='function')fail('WEBPACK_REQUIRE_MISSING');
                  trace.phase='module-resolution';
                  const predicates={api:m=>m?.Request&&typeof m.Request.safePatch==='function'&&typeof m.Request.safeGet==='function',atoms:m=>['u','k','f'].every(k=>typeof m?.[k]==='function'),cookie:m=>['a','b','c'].every(k=>typeof m?.[k]==='function'),flush:m=>typeof m?.flushBrowserHostStorageWrites==='function'};
                  const legacy={api:'Kwur',atoms:'kpIY',cookie:'ZhRn',flush:'YAwS'},resolved={};
                  for(const [role,id] of Object.entries(legacy)){
                    try{const m=req(id);if(predicates[role](m)){resolved[role]=m;trace.modules[role]={id,route:'baseline',exports:Object.keys(m).slice(0,40)};}}catch{}
                  }
                  // Only instantiate factories with an exact semantic marker. Never enumerate
                  // and execute all application modules or infer renamed storage setters.
                  for(const [id,factory] of Object.entries(req.m||{})){
                    const s=String(factory);let role=null;
                    if(!resolved.api&&s.includes('safePatch')&&s.includes('safeGet')&&s.includes('Request'))role='api';
                    if(!resolved.flush&&s.includes('flushBrowserHostStorageWrites'))role='flush';
                    if(!role)continue;
                    try{const m=req(id);if(predicates[role](m)){resolved[role]=m;trace.modules[role]={id,route:'semantic-marker',exports:Object.keys(m).slice(0,40)};trace.changes.push({role,previousId:legacy[role],currentId:id});}}catch{}
                  }
                  // Static factory snippets identify actual endpoint/caller changes without
                  // including request headers, session cookies, responses or conversation data.
                  for(const [id,factory] of Object.entries(req.m||{})){
                    const s=String(factory);
                    for(const marker of ['/settings/user_last_used_model_config','/settings/user_tpp_last_used_model_config']){
                      const pos=s.indexOf(marker);
                      if(pos>=0&&trace.sources.length<16)trace.sources.push({moduleId:id,marker,code:s.slice(Math.max(0,pos-220),pos+marker.length+400)});
                    }
                  }
                  const missing=Object.keys(legacy).filter(k=>!resolved[k]);
                  if(missing.length){trace.missing=missing;fail('NATIVE_MODULE_CONTRACT_CHANGED');}
                  const api=resolved.api.Request;
                  trace.apiSignatures={safePatch:Function.prototype.toString.call(api.safePatch).slice(0,1800),safeGet:Function.prototype.toString.call(api.safeGet).slice(0,1000)};
                  trace.cookieSignatures={set:Function.prototype.toString.call(resolved.cookie.c).slice(0,1800),get:Function.prototype.toString.call(resolved.cookie.a).slice(0,1000)};
                  trace.cookieContract={reader:'a',rawReader:'b',writer:'c'};
                  const writerSource=Function.prototype.toString.call(resolved.cookie.c);
                  if(!writerSource.includes('setCookie')||!writerSource.includes('deleteCookie'))fail('COOKIE_WRITER_CONTRACT_CHANGED');
                  trace.phase='ready';
                  return {origin,api,atoms:resolved.atoms,cookie:resolved.cookie,flush:resolved.flush.flushBrowserHostStorageWrites,diagnostics:trace};
                }

                async function nativeEnvironment(){return resolveNativeEnvironment();}
                """;
    }

    private static String q(String value) { return RequestProfileScript.jsQuote(value); }
}
