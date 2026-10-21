package com.shaterguy.chatgptpromptscheduler;

/** DOM-only editor transport with separate clear, input, and persistent readback stages. */
final class DomComposerScript {
    private DomComposerScript() {}

    static String statePrelude() {
        return """
                const domStates=window.__schedulerComposerStates||(window.__schedulerComposerStates={});
                const domKey=markerKey+':dom';
                const domState=domStates[domKey]||(domStates[domKey]={phase:'clearing',reverts:0,verifiedAt:0});
                const domTextStable=()=>{const now=Date.now();if(domState.phase!=='verified'){domState.phase='verified';domState.verifiedAt=now;return false;}return now-domState.verifiedAt>=550;};
                """.replace("\n", "");
    }

    static String inputStep() {
        return """
                const editorDoc=composer.ownerDocument||document;
                const editorWin=editorDoc.defaultView||window;
                const empty=()=>canonical(raw())==='';
                const inputEvent=(type,kind,data)=>{try{return composer.dispatchEvent(new InputEvent(type,{bubbles:true,cancelable:type==='beforeinput',inputType:kind,data}));}catch(_){return composer.dispatchEvent(new Event(type,{bubbles:true,cancelable:type==='beforeinput'}));}};
                const emitChange=(kind,data)=>{inputEvent('input',kind,data);try{composer.dispatchEvent(new Event('change',{bubbles:true}));}catch(_){}};
                const fieldSetter=()=>{const own=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(composer),'value');const base=typeof HTMLTextAreaElement==='undefined'?null:Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value');return own?.set||base?.set;};
                const setValue=value=>{const setter=fieldSetter();if(setter)setter.call(composer,value);else composer.value=value;};
                const selectContents=()=>{try{composer.focus();const sel=editorWin.getSelection();const range=editorDoc.createRange();range.selectNodeContents(composer);sel.removeAllRanges();sel.addRange(range);return sel.rangeCount===1;}catch(_){return false;}};
                const putCaretAtEnd=()=>{try{composer.focus();const sel=editorWin.getSelection();const range=editorDoc.createRange();const p=composer.querySelector('p')||composer;range.selectNodeContents(p);range.collapse(false);sel.removeAllRanges();sel.addRange(range);return true;}catch(_){return false;}};
                const clearDom=()=>{
                    if(empty())return 'already-empty';
                    composer.focus();
                    if('value'in composer){inputEvent('beforeinput','deleteContentBackward',null);setValue('');emitChange('deleteContentBackward',null);return 'native-clear';}
                    const selected=selectContents();
                    inputEvent('beforeinput','deleteContentBackward',null);
                    if(selected){try{editorDoc.execCommand('delete',false,null);}catch(_){}}
                    if(!empty()){
                        let p=composer.querySelector('p');
                        if(!p){p=editorDoc.createElement('p');composer.replaceChildren(p);}
                        p.replaceChildren(editorDoc.createElement('br'));
                        if(composer.childNodes.length!==1||composer.firstChild!==p)composer.replaceChildren(p);
                        emitChange('deleteContentBackward',null);
                        return 'paragraph-clear';
                    }
                    emitChange('deleteContentBackward',null);
                    return 'native-delete';
                };
                const insertDom=()=>{
                    composer.focus();
                    if('value'in composer){inputEvent('beforeinput','insertText',expected);setValue(expected);emitChange('insertText',expected);return 'native-value';}
                    putCaretAtEnd();
                    inputEvent('beforeinput','insertText',expected);
                    if(same())return 'beforeinput';
                    let nativeDone=false;
                    try{nativeDone=!!editorDoc.execCommand('insertText',false,expected);}catch(_){}
                    if(same())return nativeDone?'native-insertText':'native-input';
                    let p=composer.querySelector('p');
                    if(!p){p=editorDoc.createElement('p');composer.replaceChildren(p);}
                    p.textContent=expected;
                    if(composer.childNodes.length!==1||composer.firstChild!==p)composer.replaceChildren(p);
                    emitChange('insertText',expected);
                    return 'paragraph-input';
                };
                if(domState.phase==='verified'||domState.phase==='inputting'){
                    domState.reverts++;
                    if(domState.reverts>=3)return result('COMPOSER_EDITOR_STATE_REVERTED','DOM 편집기 내용이 반복 초기화됩니다.',{...diag('input-failed','dom-readback'),reverts:domState.reverts,observedLength:raw().length,expectedLength:expected.length});
                    domState.phase='clearing';
                    domState.verifiedAt=0;
                }
                if(domState.phase==='clearing'){
                    if(!empty()){const strategy=clearDom();return result('RETRY','DOM 편집기 초기화 중',{...diag('clear',strategy),observedLength:raw().length});}
                    domState.phase='inserting';
                }
                if(domState.phase==='inserting'){
                    if(!empty()){domState.phase='clearing';const strategy=clearDom();return result('RETRY','DOM 편집기 초기화 중',{...diag('clear',strategy),observedLength:raw().length});}
                    const strategy=insertDom();
                    domState.phase='inputting';
                    return result('RETRY','DOM 입력 확정 대기',{...diag('input',strategy),observedLength:raw().length,expectedLength:expected.length,reverts:domState.reverts});
                }
                domState.phase='clearing';
                return result('RETRY','DOM 편집기 초기화 대기',diag('clear','reset'));
                """.replace("\n", "");
    }
}
