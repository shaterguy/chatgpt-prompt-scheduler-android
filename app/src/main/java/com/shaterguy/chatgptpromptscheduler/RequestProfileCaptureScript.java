package com.shaterguy.chatgptpromptscheduler;

import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.util.Locale;
import java.util.Set;

/** Capture-only adaptation of SelfRun's one-shot outgoing conversation control capture. */
final class RequestProfileCaptureScript {
    static final String BRIDGE = "SchedulerProfileCapture";
    static final Set<String> ORIGINS = Set.of("https://chatgpt.com", "https://www.chatgpt.com");

    private RequestProfileCaptureScript() {}

    static boolean supported() {
        return WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER);
    }

    static void install(WebView view, WebViewCompat.WebMessageListener listener) {
        if (!supported()) throw new IllegalStateException("현재 WebView에서 요청 캡처를 지원하지 않습니다.");
        WebViewCompat.addWebMessageListener(view, BRIDGE, ORIGINS, listener);
        WebViewCompat.addDocumentStartJavaScript(view, documentStartScript(), ORIGINS);
    }

    static String arm(String captureId, RequestProfileEngine.Mode mode) {
        return "window.__schedulerProfileCapture?.arm(" + JSONObject.quote(captureId) + ","
                + JSONObject.quote(mode.name().toLowerCase(Locale.ROOT)) + ")===true";
    }

    static String cancel() { return "window.__schedulerProfileCapture?.cancel()"; }

    static String documentStartScript() {
        return """
                (()=>{
                  if(window.top!==window||!['https://chatgpt.com','https://www.chatgpt.com'].includes(location.origin))return;
                  if(window.__schedulerProfileCapture?.version==='scheduler-profile-capture-v1')return;
                  const CONTROL=['model','thinking_effort','conversation_origin','service_tier'];
                  const MAX_BODY=1048576;
                  const own=(o,k)=>Object.prototype.hasOwnProperty.call(o,k);
                  let capture=null;
                  const live=c=>!!c&&capture===c&&c.armed;
                  const emit=(c,value)=>{
                    if(!live(c))return;
                    c.armed=false;
                    try{window.SchedulerProfileCapture?.postMessage(JSON.stringify({captureId:c.id,mode:c.mode,...value}));}catch(_){}
                  };
                  const error=(c,code)=>emit(c,{error:code});
                  const inspect=(c,text)=>{
                    if(!live(c))return;
                    if(typeof text!=='string'){error(c,'body_not_text');return;}
                    if(text.length>MAX_BODY){error(c,'body_too_large');return;}
                    let body;try{body=JSON.parse(text);}catch(_){error(c,'body_not_json');return;}
                    if(!body||typeof body!=='object'||Array.isArray(body)||!Array.isArray(body.messages))return;
                    const operations=[];
                    for(const path of CONTROL){
                      if(!own(body,path)){operations.push({op:'REMOVE',path});continue;}
                      const value=body[path];
                      if(typeof value!=='string'||!value.length||value.length>128){error(c,'control_value_unsupported');return;}
                      operations.push({op:'SET',path,value});
                    }
                    if(operations[0].op!=='SET'){error(c,'model_missing');return;}
                    emit(c,{operations});
                  };
                  const route=(url,method)=>{
                    try{
                      const u=new URL(url,location.href);
                      if(String(method||'GET').toUpperCase()!=='POST'||u.origin!==location.origin)return false;
                      let p=u.pathname;if(p.endsWith('/'))p=p.slice(0,-1);
                      return p==='/backend-api/conversation'||p==='/backend-api/f/conversation';
                    }catch(_){return false;}
                  };
                  const inspectRequest=async(c,request)=>{
                    let reader;
                    try{
                      reader=request.body?.getReader();
                      if(!reader){inspect(c,'');return;}
                      const decoder=new TextDecoder();let text='',size=0;
                      while(live(c)){
                        const part=await reader.read();
                        if(!live(c)){reader.cancel().catch(()=>{});return;}
                        if(part.done){text+=decoder.decode();inspect(c,text);return;}
                        size+=part.value.byteLength;
                        if(size>MAX_BODY){reader.cancel().catch(()=>{});error(c,'body_too_large');return;}
                        text+=decoder.decode(part.value,{stream:true});
                      }
                      reader.cancel().catch(()=>{});
                    }catch(_){error(c,'body_unreadable');}
                    finally{try{reader?.releaseLock();}catch(_){}}
                  };
                  const nativeFetch=window.fetch;
                  window.fetch=function(input,init){
                    const c=capture;
                    if(live(c)){
                      try{
                        const isRequest=typeof Request!=='undefined'&&input instanceof Request;
                        const url=isRequest?input.url:String(input);
                        const method=init&&init.method!==undefined?init.method:(isRequest?input.method:'GET');
                        if(route(url,method)){
                          if(init&&own(init,'body')&&init.body!=null)inspect(c,init.body);
                          else if(isRequest)inspectRequest(c,input.clone());
                          else inspect(c,'');
                        }
                      }catch(_){error(c,'body_unreadable');}
                    }
                    // Observe without replacing, serializing, retrying or cancelling the native request.
                    return Reflect.apply(nativeFetch,this,arguments);
                  };
                  const nativeOpen=XMLHttpRequest.prototype.open;
                  const nativeSend=XMLHttpRequest.prototype.send;
                  const requests=new WeakMap();
                  XMLHttpRequest.prototype.open=function(method,url){
                    const result=Reflect.apply(nativeOpen,this,arguments);
                    requests.set(this,{method,url});return result;
                  };
                  XMLHttpRequest.prototype.send=function(body){
                    const c=capture,request=requests.get(this);
                    if(live(c)&&request&&route(request.url,request.method)){
                      try{inspect(c,body);}catch(_){error(c,'body_unreadable');}
                    }
                    return Reflect.apply(nativeSend,this,arguments);
                  };
                  window.__schedulerProfileCapture={
                    version:'scheduler-profile-capture-v1',
                    arm(id,mode){
                      if(typeof id!=='string'||!id.length||id.length>128||!['chat','work'].includes(mode))return false;
                      if(capture?.id===id&&capture.mode===mode)return true;
                      if(capture)capture.armed=false;
                      capture={id,mode,armed:true};return true;
                    },
                    cancel(){if(capture)capture.armed=false;capture=null;return true;}
                  };
                })();
                """;
    }
}
