package com.shaterguy.chatgptpromptscheduler;

import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Set;

/** Document-start fetch/XHR interceptor for scheduled ChatGPT conversation submissions. */
final class RequestProfileScript {
    static final String ENGINE_VERSION = "scheduler-request-profile-engine-v1";
    static final Set<String> CHATGPT_ORIGINS = Set.of(
            "https://chatgpt.com", "https://www.chatgpt.com");

    private RequestProfileScript() {}

    static boolean isDocumentStartSupported() {
        return WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
    }

    static void installDocumentStart(WebView webView) {
        if (!isDocumentStartSupported()) {
            throw new IllegalStateException("DOCUMENT_START_SCRIPT_UNSUPPORTED");
        }
        WebViewCompat.addDocumentStartJavaScript(webView, documentStartScript(), CHATGPT_ORIGINS);
    }

    static String engineAvailableExpression() {
        return "window.__chatgptPromptSchedulerRequestProfileEngine?.version===" + jsQuote(ENGINE_VERSION);
    }

    /**
     * Emits idempotent absolute setup on each automation attempt. Invalid legacy values remain
     * loadable, but emit only a fixed failure and are rejected by ExecutionService before load.
     */
    static String activate(Schedule schedule) {
        if ("existing".equals(schedule.targetType)) {
            return "const modeDiagnostics={requested:'inherit',ready:true,action:'native-inherit'};"
                    + "const modelDiagnostics={requested:'inherit',ready:true,action:'native-inherit'};"
                    + "const reasoningDiagnostics={requested:'inherit',ready:true,action:'native-inherit'};";
        }

        String experience = Schedule.normalizedExperience(schedule.targetType, schedule.experience);
        String model = "work".equals(experience)
                ? Schedule.normalizedWorkModel(experience, schedule.workModel) : "";
        String reasoning = "work".equals(experience)
                ? Schedule.normalizedReasoningEffort(experience, schedule.reasoningEffort)
                : Schedule.normalizedChatReasoning(experience, schedule.chatReasoning);
        try {
            RequestProfileEngine.plan(new RequestProfileEngine.TargetProfile(
                    "work".equals(experience) ? RequestProfileEngine.Mode.WORK : RequestProfileEngine.Mode.CHAT,
                    model, reasoning));
        } catch (IllegalArgumentException invalid) {
            return "return result('REQUEST_PROFILE_INVALID','요청 프로필이 지원되지 않습니다.',"
                    + "{mode:" + jsQuote(experience) + ",model:" + jsQuote(model)
                    + ",reasoning:" + jsQuote(reasoning) + ",operation:'validate'});";
        }

        StringBuilder setup = new StringBuilder();
        setup.append("profileEngine.begin(").append(jsQuote(experience)).append(");");
        if ("work".equals(experience)) {
            setup.append("profileEngine.setWorkModel(").append(jsQuote(model)).append(");")
                    .append("profileEngine.setWorkReasoning(").append(jsQuote(reasoning)).append(");");
        } else {
            setup.append("profileEngine.setChatReasoning(").append(jsQuote(reasoning)).append(");");
        }

        return "const profileEngine=window.__chatgptPromptSchedulerRequestProfileEngine;"
                + "const profileAvailability={mode:" + jsQuote(experience) + ",model:" + jsQuote(model)
                + ",reasoning:" + jsQuote(reasoning) + ",operation:'activate'};"
                + "if(!profileEngine)return result('REQUEST_PROFILE_ENGINE_ABSENT','요청 프로필 엔진을 사용할 수 없습니다.',profileAvailability);"
                + "if(profileEngine.version!==" + jsQuote(ENGINE_VERSION)
                + ")return result('REQUEST_PROFILE_VERSION_MISMATCH','요청 프로필 엔진 버전이 일치하지 않습니다.',profileAvailability);"
                + "if(typeof profileEngine.begin!=='function'||typeof profileEngine.target!=='function')"
                + "return result('REQUEST_PROFILE_ENGINE_INVALID','요청 프로필 엔진을 사용할 수 없습니다.',profileAvailability);"
                + "try{" + setup + "}catch(_){return result('REQUEST_PROFILE_REJECTED','요청 프로필 설정이 거부되었습니다.',profileAvailability);}"
                + "const activeProfile=profileEngine.target();"
                + "if(!activeProfile||!activeProfile.ready||activeProfile.profileVersion!=="
                + jsQuote(RequestProfileEngine.PROFILE_VERSION)
                + ")return result('REQUEST_PROFILE_NOT_READY','요청 프로필이 준비되지 않았습니다.',profileAvailability);"
                + "const modeDiagnostics={requested:" + jsQuote(experience) + ",ready:true,action:'request-profile'};"
                + "const modelDiagnostics={requested:" + jsQuote(model.isEmpty() ? "chat" : model)
                + ",ready:true,action:'request-profile'};"
                + "const reasoningDiagnostics={requested:" + jsQuote(reasoning)
                + ",ready:true,action:'request-profile'};";
    }

    static String documentStartScript() {
        return """
                (()=>{
                  if(window.__chatgptPromptSchedulerRequestProfileEngine?.version===__ENGINE_VERSION__)return;
                  const CONTROL=['model','thinking_effort','conversation_origin','service_tier'];
                  const state={target:null,last:{ok:false,reason:'not_attempted'}};
                  const fail=reason=>{state.last={ok:false,reason:String(reason||'profile_failure').slice(0,80)};throw new Error('REQUEST_PROFILE:'+state.last.reason);};
                  const norm=value=>String(value??'').trim().toLowerCase();
                  const begin=mode=>{const normalized=norm(mode);if(normalized!=='chat'&&normalized!=='work')fail('unsupported_mode');state.target={mode:normalized,model:'',reasoning:'',profileVersion:__PROFILE_VERSION__,ready:false};state.last={ok:true,reason:'target_begun',mode:normalized,operation:'begin'};return true;};
                  const requireTarget=mode=>{const target=state.target;if(!target||target.mode!==mode)fail('target_mode_not_initialized');return target;};
                  const setChatReasoning=reasoning=>{const target=requireTarget('chat'),value=norm(reasoning);if(value==='pro')fail('chat_pro_uncaptured');if(!['instant','medium','high','xhigh'].includes(value))fail('unsupported_chat_reasoning');target.model='chat';target.reasoning=value;target.ready=true;state.last={ok:true,reason:'target_ready',mode:'chat',model:'chat',reasoning:value,operation:'set_chat_reasoning'};return true;};
                  const setWorkModel=model=>{const target=requireTarget('work'),value=norm(model);if(!['sol','terra','luna'].includes(value))fail('unsupported_work_model');target.model=value;target.reasoning='';target.ready=false;state.last={ok:true,reason:'work_model_set',mode:'work',model:value,operation:'set_work_model'};return true;};
                  const setWorkReasoning=reasoning=>{const target=requireTarget('work'),value=norm(reasoning);if(!target.model)fail('work_model_missing');if(!['light','medium','high','xhigh','max','ultra'].includes(value))fail('unsupported_work_reasoning');if(target.model==='luna'&&value==='ultra')fail('luna_ultra_unsupported');target.reasoning=value;target.ready=true;state.last={ok:true,reason:'target_ready',mode:'work',model:target.model,reasoning:value,operation:'set_work_reasoning'};return true;};
                  const plan=()=>{const target=state.target;if(!target||!target.ready)fail('target_not_ready');if(target.profileVersion!==__PROFILE_VERSION__)fail('profile_version_mismatch');if(target.mode==='chat'){const effort={medium:'standard',high:'extended',xhigh:'max'}[target.reasoning];if(target.reasoning==='instant')return[['set','model','gpt-5-6'],['remove','thinking_effort'],['remove','conversation_origin'],['remove','service_tier']];if(!effort)fail('unsupported_chat_reasoning');return[['set','model','gpt-5-6-thinking'],['set','thinking_effort',effort],['remove','conversation_origin'],['remove','service_tier']];}const model={sol:'gpt-5.6-sol-wm',terra:'gpt-5.6-terra-wm',luna:'gpt-5.6-luna-wm'}[target.model];const effort={light:'min',medium:'standard',high:'extended',xhigh:'xhigh',max:'max',ultra:'ultra'}[target.reasoning];if(!model||!effort)fail('unsupported_work_profile');if(target.model==='luna'&&target.reasoning==='ultra')fail('luna_ultra_unsupported');return[['set','model',model],['set','thinking_effort',effort],['set','conversation_origin','tpp'],['set','service_tier','standard']];};
                  const sameOrigin=url=>{try{return new URL(url,location.href).origin===location.origin;}catch(_){return false;}};
                  const conversationRoute=url=>{try{const path=new URL(url,location.href).pathname;return path==='/backend-api/conversation'||path==='/backend-api/conversation/'||path==='/backend-api/f/conversation'||path==='/backend-api/f/conversation/';}catch(_){return false;}};
                  const strip=object=>{const copy={...object};for(const key of CONTROL)delete copy[key];return copy;};
                  const patchObject=(body,url)=>{if(!conversationRoute(url))fail('conversation_route_not_allowed');if(!body||typeof body!=='object'||Array.isArray(body)||!Array.isArray(body.messages))fail('unknown_conversation_schema');const before=JSON.stringify(strip(body));const output={...body};const operations=plan();for(const [kind,path,value] of operations){if(!CONTROL.includes(path))fail('control_allowlist_violation');if(kind==='set')output[path]=value;else if(kind==='remove')delete output[path];else fail('unknown_operation');}if(JSON.stringify(strip(output))!==before)fail('data_plane_changed');const target=state.target;state.last={ok:true,reason:'patched',mode:target.mode,model:target.model,reasoning:target.reasoning,operation:'patch'};return output;};
                  const patchText=(url,method,text)=>{if(norm(method)!=='post'||!sameOrigin(url)||!conversationRoute(url))return null;if(typeof text!=='string')fail('non_text_conversation_body');let body;try{body=JSON.parse(text);}catch(_){fail('invalid_conversation_json');}return JSON.stringify(patchObject(body,url));};
                  const nativeFetch=window.fetch.bind(window);
                  const fetchProbe=(input,init)=>{try{const requestInput=typeof Request!=='undefined'&&input instanceof Request;const url=requestInput?input.url:String(input??'');const method=init&&init.method!==undefined?init.method:(requestInput?input.method:'GET');return{url,method,eligible:norm(method)==='post'&&sameOrigin(url)&&conversationRoute(url)};}catch(_){return{url:'',method:'',eligible:false};}};
                  window.fetch=async function(input,init){const probe=fetchProbe(input,init);if(!probe.eligible)return nativeFetch(input,init);let request;try{const source=typeof Request!=='undefined'&&input instanceof Request?input.clone():input;request=new Request(source,init);}catch(_){fail('request_construction_failed');}let text;try{text=await request.clone().text();}catch(_){fail('request_body_unreadable');}const patched=patchText(request.url,request.method,text);if(patched===null)fail('target_patch_not_applied');try{return nativeFetch(new Request(request,{body:patched}));}catch(_){fail('patched_request_construction_failed');}};
                  const nativeOpen=XMLHttpRequest.prototype.open,nativeSend=XMLHttpRequest.prototype.send;
                  const metadata=new WeakMap();
                  XMLHttpRequest.prototype.open=function(method,url,...rest){metadata.set(this,{method:String(method||''),url:String(url||'')});return nativeOpen.call(this,method,url,...rest);};
                  XMLHttpRequest.prototype.send=function(body){const request=metadata.get(this)||{method:'',url:''};const patched=patchText(request.url,request.method,body);return nativeSend.call(this,patched===null?body:patched);};
                  window.__chatgptPromptSchedulerRequestProfileEngine={version:__ENGINE_VERSION__,begin,setChatReasoning,setWorkModel,setWorkReasoning,diagnostics:()=>({...state.last}),target:()=>state.target?{mode:state.target.mode,model:state.target.model,reasoning:state.target.reasoning,profileVersion:state.target.profileVersion,ready:state.target.ready}:null};
                })();
                """
                .replace("__ENGINE_VERSION__", jsQuote(ENGINE_VERSION))
                .replace("__PROFILE_VERSION__", jsQuote(RequestProfileEngine.PROFILE_VERSION));
    }

    private static String jsQuote(String value) {
        StringBuilder output = new StringBuilder(value.length() + 16).append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\': output.append("\\\\"); break;
                case '"': output.append("\\\""); break;
                case '\n': output.append("\\n"); break;
                case '\r': output.append("\\r"); break;
                case '\t': output.append("\\t"); break;
                case '\b': output.append("\\b"); break;
                case '\f': output.append("\\f"); break;
                default:
                    if (character < 0x20 || character == '\u2028' || character == '\u2029') {
                        output.append(String.format("\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
            }
        }
        return output.append('"').toString();
    }
}
