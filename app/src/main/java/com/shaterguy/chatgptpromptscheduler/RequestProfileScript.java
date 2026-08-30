package com.shaterguy.chatgptpromptscheduler;

import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.List;
import java.util.Set;

/** Document-start fetch/XHR interceptor for scheduled ChatGPT conversation submissions. */
final class RequestProfileScript {
    static final String ENGINE_VERSION = "scheduler-request-profile-engine-v2";
    static final Set<String> CHATGPT_ORIGINS = Set.of("https://chatgpt.com", "https://www.chatgpt.com");

    private RequestProfileScript() {}

    static boolean isDocumentStartSupported() { return WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT); }

    static void installDocumentStart(WebView webView) {
        if (!isDocumentStartSupported()) throw new IllegalStateException("DOCUMENT_START_SCRIPT_UNSUPPORTED");
        WebViewCompat.addDocumentStartJavaScript(webView, documentStartScript(), CHATGPT_ORIGINS);
    }

    static String engineAvailableExpression() {
        return "window.__chatgptPromptSchedulerRequestProfileEngine?.version===" + jsQuote(ENGINE_VERSION);
    }

    static String activate(Schedule schedule) {
        if ("existing".equals(schedule.targetType)) return nativeInherit("inherit", "inherit", "inherit");
        String experience = Schedule.normalizedExperience(schedule.targetType, schedule.experience);
        String model = "work".equals(experience) ? Schedule.normalizedWorkModel(experience, schedule.workModel) : "";
        String reasoning = "work".equals(experience)
                ? Schedule.normalizedReasoningEffort(experience, schedule.reasoningEffort)
                : Schedule.normalizedChatReasoning(experience, schedule.chatReasoning);
        if (("chat".equals(experience) && ("keep".equals(reasoning) || "inherit".equals(reasoning)))
                || ("work".equals(experience) && ("inherit".equals(model) || "inherit".equals(reasoning)))) {
            return nativeInherit(experience, model.isEmpty() ? "chat" : model, reasoning);
        }
        RequestProfileEngine.ProfilePlan plan;
        try {
            RequestProfileEngine.TargetProfile target = RequestProfileEngine.forSchedule(schedule);
            if (target == null) return nativeInherit(experience, model.isEmpty() ? "chat" : model, reasoning);
            plan = RequestProfileEngine.plan(target);
        } catch (IllegalArgumentException invalid) {
            return "return result('REQUEST_PROFILE_INVALID','요청 프로필이 지원되지 않습니다.',"
                    + "{mode:" + jsQuote(experience) + ",model:" + jsQuote(model)
                    + ",reasoning:" + jsQuote(reasoning) + ",operation:'validate'});";
        }
        String operations = operationsJs(plan.operations);
        return "const profileEngine=window.__chatgptPromptSchedulerRequestProfileEngine;"
                + "const profileAvailability={mode:" + jsQuote(experience) + ",model:" + jsQuote(model)
                + ",reasoning:" + jsQuote(reasoning) + ",operation:'activate'};"
                + "if(!profileEngine)return result('REQUEST_PROFILE_ENGINE_ABSENT','요청 프로필 엔진을 사용할 수 없습니다.',profileAvailability);"
                + "if(profileEngine.version!==" + jsQuote(ENGINE_VERSION)
                + ")return result('REQUEST_PROFILE_VERSION_MISMATCH','요청 프로필 엔진 버전이 일치하지 않습니다.',profileAvailability);"
                + "if(typeof profileEngine.configure!=='function'||typeof profileEngine.target!=='function')"
                + "return result('REQUEST_PROFILE_ENGINE_INVALID','요청 프로필 엔진을 사용할 수 없습니다.',profileAvailability);"
                + "try{profileEngine.configure(" + jsQuote(experience) + "," + jsQuote(model) + ","
                + jsQuote(reasoning) + "," + operations + ");}catch(_){return result('REQUEST_PROFILE_REJECTED','요청 프로필 설정이 거부되었습니다.',profileAvailability);}"
                + "const activeProfile=profileEngine.target();"
                + "if(!activeProfile||!activeProfile.ready||activeProfile.profileVersion!==" + jsQuote(RequestProfileEngine.PROFILE_VERSION)
                + ")return result('REQUEST_PROFILE_NOT_READY','요청 프로필이 준비되지 않았습니다.',profileAvailability);"
                + "const modeDiagnostics={requested:" + jsQuote(experience) + ",ready:true,action:'request-profile'};"
                + "const modelDiagnostics={requested:" + jsQuote(model.isEmpty() ? "chat" : model) + ",ready:true,action:'request-profile'};"
                + "const reasoningDiagnostics={requested:" + jsQuote(reasoning) + ",ready:true,action:'request-profile'};";
    }

    private static String nativeInherit(String mode, String model, String reasoning) {
        return "const modeDiagnostics={requested:" + jsQuote(mode) + ",ready:true,action:'native-inherit'};"
                + "const modelDiagnostics={requested:" + jsQuote(model) + ",ready:true,action:'native-inherit'};"
                + "const reasoningDiagnostics={requested:" + jsQuote(reasoning) + ",ready:true,action:'native-inherit'};";
    }

    private static String operationsJs(List<RequestProfileEngine.Operation> operations) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < operations.size(); i++) {
            if (i > 0) out.append(',');
            RequestProfileEngine.Operation op = operations.get(i);
            out.append('[').append(jsQuote(op.kind == RequestProfileEngine.OperationKind.SET ? "set" : "remove"))
                    .append(',').append(jsQuote(op.path));
            if (op.kind == RequestProfileEngine.OperationKind.SET) out.append(',').append(jsQuote(op.value));
            out.append(']');
        }
        return out.append(']').toString();
    }

    static String documentStartScript() {
        return """
                (()=>{
                  if(window.__chatgptPromptSchedulerRequestProfileEngine?.version===__ENGINE_VERSION__)return;
                  const CONTROL=['model','thinking_effort','conversation_origin','service_tier'];
                  const state={target:null,last:{ok:false,reason:'not_attempted'}};
                  const fail=reason=>{state.last={ok:false,reason:String(reason||'profile_failure').slice(0,80)};throw new Error('REQUEST_PROFILE:'+state.last.reason);};
                  const norm=value=>String(value??'').trim().toLowerCase();
                  const validateOps=operations=>{if(!Array.isArray(operations)||operations.length!==CONTROL.length)fail('operation_count_invalid');const seen=new Set();const out=[];for(const raw of operations){if(!Array.isArray(raw)||(raw.length!==2&&raw.length!==3))fail('operation_shape_invalid');const kind=norm(raw[0]),path=String(raw[1]??'');if(!CONTROL.includes(path)||seen.has(path))fail('control_allowlist_violation');seen.add(path);if(kind==='set'){if(raw.length!==3||typeof raw[2]!=='string'||raw[2].length<1||raw[2].length>128)fail('control_value_invalid');out.push(['set',path,raw[2]]);}else if(kind==='remove'){if(raw.length!==2)fail('remove_value_forbidden');out.push(['remove',path]);}else fail('unknown_operation');}if(seen.size!==CONTROL.length)fail('operation_set_incomplete');return out;};
                  const configure=(mode,model,reasoning,operations)=>{const m=norm(mode),mo=norm(model),r=norm(reasoning);if(m!=='chat'&&m!=='work')fail('unsupported_mode');if(!r)fail('reasoning_missing');if(m==='work'&&!mo)fail('work_model_missing');const ops=validateOps(operations);state.target={mode:m,model:m==='chat'?'chat':mo,reasoning:r,profileVersion:__PROFILE_VERSION__,ready:true,operations:ops};state.last={ok:true,reason:'target_ready',mode:m,model:state.target.model,reasoning:r,operation:'configure'};return true;};
                  const chatOps=reasoning=>{const r=norm(reasoning);if(r==='instant')return[['set','model','gpt-5-6'],['remove','thinking_effort'],['remove','conversation_origin'],['remove','service_tier']];if(r==='medium')return[['set','model','gpt-5-6-thinking'],['set','thinking_effort','standard'],['remove','conversation_origin'],['remove','service_tier']];if(r==='high')return[['set','model','gpt-5-6-thinking'],['set','thinking_effort','extended'],['remove','conversation_origin'],['remove','service_tier']];if(r==='xhigh')return[['set','model','gpt-5-6-thinking'],['set','thinking_effort','max'],['remove','conversation_origin'],['remove','service_tier']];if(r==='pro')return[['set','model','gpt-5-6-pro'],['set','thinking_effort','standard'],['remove','conversation_origin'],['remove','service_tier']];fail('unsupported_chat_reasoning');};
                  const workOps=(model,reasoning)=>{const key=norm(model)+'/'+norm(reasoning);const values={
                    'luna/max':['gpt-5.6-luna-wm','max','standard'],
                    'sol/high':['gpt-5.6-sol-wm','extended','standard'],'sol/max':['gpt-5.6-sol-wm','max','standard'],'sol/ultra':['gpt-5.6-sol-wm','ultra','standard'],'sol/xhigh':['gpt-5.6-sol-wm','xhigh','standard'],
                    'terra/high':['gpt-5.6-terra-wm','extended','standard'],'terra/max':['gpt-5.6-terra-wm','max','standard'],'terra/ultra':['gpt-5.6-terra-wm','ultra',null],'terra/xhigh':['gpt-5.6-terra-wm','xhigh','standard']
                  }[key];if(!values)fail('unsupported_work_profile');return[['set','model',values[0]],['set','thinking_effort',values[1]],['set','conversation_origin','tpp'],values[2]===null?['remove','service_tier']:['set','service_tier',values[2]]];};
                  const begin=mode=>{const m=norm(mode);if(m!=='chat'&&m!=='work')fail('unsupported_mode');state.target={mode:m,model:'',reasoning:'',profileVersion:__PROFILE_VERSION__,ready:false,operations:null};return true;};
                  const setChatReasoning=reasoning=>configure('chat','',reasoning,chatOps(reasoning));
                  const setWorkModel=model=>{if(!state.target||state.target.mode!=='work')fail('target_mode_not_initialized');state.target.model=norm(model);state.target.ready=false;return true;};
                  const setWorkReasoning=reasoning=>{if(!state.target||state.target.mode!=='work'||!state.target.model)fail('work_model_missing');return configure('work',state.target.model,reasoning,workOps(state.target.model,reasoning));};
                  const plan=()=>{const target=state.target;if(!target||!target.ready)fail('target_not_ready');if(target.profileVersion!==__PROFILE_VERSION__)fail('profile_version_mismatch');return validateOps(target.operations);};
                  const sameOrigin=url=>{try{return new URL(url,location.href).origin===location.origin;}catch(_){return false;}};
                  const conversationRoute=url=>{try{const path=new URL(url,location.href).pathname;return path==='/backend-api/conversation'||path==='/backend-api/conversation/'||path==='/backend-api/f/conversation'||path==='/backend-api/f/conversation/';}catch(_){return false;}};
                  const strip=object=>{const copy={...object};for(const key of CONTROL)delete copy[key];return copy;};
                  const patchObject=(body,url)=>{if(!conversationRoute(url))fail('conversation_route_not_allowed');if(!body||typeof body!=='object'||Array.isArray(body)||!Array.isArray(body.messages))fail('unknown_conversation_schema');const before=JSON.stringify(strip(body));const output={...body};const operations=plan();for(const [kind,path,value] of operations){if(kind==='set')output[path]=value;else delete output[path];}if(JSON.stringify(strip(output))!==before)fail('data_plane_changed');const target=state.target;state.last={ok:true,reason:'patched',mode:target.mode,model:target.model,reasoning:target.reasoning,operation:'patch'};return output;};
                  const patchText=(url,method,text)=>{if(norm(method)!=='post'||!sameOrigin(url)||!conversationRoute(url))return null;if(typeof text!=='string')fail('non_text_conversation_body');let body;try{body=JSON.parse(text);}catch(_){fail('invalid_conversation_json');}return JSON.stringify(patchObject(body,url));};
                  const nativeFetch=window.fetch.bind(window);
                  const fetchProbe=(input,init)=>{try{const requestInput=typeof Request!=='undefined'&&input instanceof Request;const url=requestInput?input.url:String(input??'');const method=init&&init.method!==undefined?init.method:(requestInput?input.method:'GET');return{url,method,eligible:norm(method)==='post'&&sameOrigin(url)&&conversationRoute(url)};}catch(_){return{url:'',method:'',eligible:false};}};
                  window.fetch=async function(input,init){const probe=fetchProbe(input,init);if(!probe.eligible)return nativeFetch(input,init);let request;try{const source=typeof Request!=='undefined'&&input instanceof Request?input.clone():input;request=new Request(source,init);}catch(_){fail('request_construction_failed');}let text;try{text=await request.clone().text();}catch(_){fail('request_body_unreadable');}const patched=patchText(request.url,request.method,text);if(patched===null)fail('target_patch_not_applied');try{return nativeFetch(new Request(request,{body:patched}));}catch(_){fail('patched_request_construction_failed');}};
                  const nativeOpen=XMLHttpRequest.prototype.open,nativeSend=XMLHttpRequest.prototype.send;
                  const metadata=new WeakMap();
                  XMLHttpRequest.prototype.open=function(method,url,...rest){metadata.set(this,{method:String(method||''),url:String(url||'')});return nativeOpen.call(this,method,url,...rest);};
                  XMLHttpRequest.prototype.send=function(body){const request=metadata.get(this)||{method:'',url:''};const patched=patchText(request.url,request.method,body);return nativeSend.call(this,patched===null?body:patched);};
                  window.__chatgptPromptSchedulerRequestProfileEngine={version:__ENGINE_VERSION__,configure,begin,setChatReasoning,setWorkModel,setWorkReasoning,diagnostics:()=>({...state.last}),target:()=>state.target?{mode:state.target.mode,model:state.target.model,reasoning:state.target.reasoning,profileVersion:state.target.profileVersion,ready:state.target.ready}:null};
                })();
                """
                .replace("__ENGINE_VERSION__", jsQuote(ENGINE_VERSION))
                .replace("__PROFILE_VERSION__", jsQuote(RequestProfileEngine.PROFILE_VERSION));
    }

    private static String jsQuote(String value) {
        StringBuilder output = new StringBuilder((value == null ? 0 : value.length()) + 16).append('"');
        String source = value == null ? "" : value;
        for (int i = 0; i < source.length(); i++) {
            char character = source.charAt(i);
            switch (character) {
                case '\\': output.append("\\\\"); break;
                case '"': output.append("\\\""); break;
                case '\n': output.append("\\n"); break;
                case '\r': output.append("\\r"); break;
                case '\t': output.append("\\t"); break;
                case '\b': output.append("\\b"); break;
                case '\f': output.append("\\f"); break;
                default:
                    if (character < 0x20 || character == '\u2028' || character == '\u2029') output.append(String.format("\\u%04x", (int) character));
                    else output.append(character);
            }
        }
        return output.append('"').toString();
    }
}
