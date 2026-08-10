from pathlib import Path

repo = Path('.')
source = repo / 'app/src/main/java/com/shaterguy/chatgptpromptscheduler/AutomationScript.java'
text = source.read_text(encoding='utf-8')
start = text.index('    static String preferenceScript(Schedule schedule, String run) {')
end = text.index('    private static String modelScript(String requestedModel) {', start)
replacement = r'''    static String preferenceScript(Schedule schedule, String run) {
        if ("existing".equals(schedule.targetType)) {
            return "const modeDiagnostics={requested:'inherit',ready:true,action:'',skipped:true};" +
                    "const modelDiagnostics={requested:'inherit',ready:true,action:'',skipped:true};" +
                    "const reasoningDiagnostics={requested:'inherit',ready:true,action:'',skipped:true};";
        }
        String requestedMode = "work".equals(schedule.experience) ? "work" : "chat";
        String requestedModel = Schedule.normalizedWorkModel(schedule.experience, schedule.workModel);
        String requestedEffort = Schedule.normalizedReasoningEffort(schedule.experience, schedule.reasoningEffort);
        String modeSelection;
        if ("work".equals(schedule.experience)) {
            modeSelection =
                    "const mode=modeCandidate(['work','작업']);" +
                    "let modeClicks=0;try{modeClicks=Math.max(0,Number(sessionStorage.getItem(modeKey)||0));}catch(_){}" +
                    "const modeSelected=modeIsSelected(mode);" +
                    "const modeDiagnostics={requested:'work',candidateFound:!!mode,candidateLabel:mode?clip(exactText((mode.innerText||'')+' '+(mode.getAttribute('aria-label')||'')),120):'',selected:modeSelected,clicked:false,clickCount:modeClicks};" +
                    "if(mode&&!modeSelected){if(modeClicks>=3)return result('MODE_SELECTION_FAILED','Work 모드 실제 적용을 확인하지 못했습니다.',{...routeDiagnostics,mode:modeDiagnostics});modeClicks++;try{sessionStorage.setItem(modeKey,String(modeClicks));}catch(_){}mode.click();modeDiagnostics.clicked=true;modeDiagnostics.clickCount=modeClicks;}" +
                    "if(modeDiagnostics.clicked)return result('RETRY','모드 전환 반영 대기',{...routeDiagnostics,mode:modeDiagnostics});" +
                    "if(!modeSelected)return result('RETRY','Work 모드 실제 적용 상태 대기',{...routeDiagnostics,mode:modeDiagnostics});";
        } else {
            modeSelection =
                    "const mode=modeCandidate(['chat','채팅']);" +
                    "const workMode=modeCandidate(['work','작업']);" +
                    "let modeClicks=0;try{modeClicks=Math.max(0,Number(sessionStorage.getItem(modeKey)||0));}catch(_){}" +
                    "const modeSelected=modeIsSelected(mode),workSelected=modeIsSelected(workMode);" +
                    "const modeDiagnostics={requested:'chat',candidateFound:!!mode,candidateLabel:mode?clip(exactText((mode.innerText||'')+' '+(mode.getAttribute('aria-label')||'')),120):'',selected:modeSelected,workSelected,assumedActive:!modeSelected&&!workSelected,clicked:false,clickCount:modeClicks};" +
                    "if(workSelected){if(!mode)return result('MODE_SELECTION_FAILED','Chat 모드 선택 항목을 찾지 못했습니다.',{...routeDiagnostics,mode:modeDiagnostics});if(modeClicks>=3)return result('MODE_SELECTION_FAILED','Chat 모드 전환을 확인하지 못했습니다.',{...routeDiagnostics,mode:modeDiagnostics});modeClicks++;try{sessionStorage.setItem(modeKey,String(modeClicks));}catch(_){}mode.click();modeDiagnostics.clicked=true;modeDiagnostics.clickCount=modeClicks;return result('RETRY','Chat 모드 전환 반영 대기',{...routeDiagnostics,mode:modeDiagnostics});}";
        }
        return "const modeKey='chatgpt-prompt-scheduler:mode:' + " + run + ";" +
                "const exactText=s=>String(s??'').replace(/\\s+/g,' ').trim().toLowerCase();" +
                "const forbiddenMode=/new chat|새 채팅|새 대화|new conversation/i;" +
                "const modeCandidates=[...document.querySelectorAll('button,[role=\"button\"],[role=\"menuitemradio\"],[role=\"radio\"],[role=\"tab\"]')];" +
                "const modeCandidate=labels=>modeCandidates.find(e=>{const inner=exactText(e.innerText||'');const aria=exactText(e.getAttribute('aria-label')||'');const combined=exactText(inner+' '+aria);if(forbiddenMode.test(combined))return false;const role=e.getAttribute('role')||'';const testId=exactText(e.dataset?.testid||'');const strong=e.hasAttribute('aria-pressed')||e.hasAttribute('aria-checked')||['menuitemradio','radio','tab'].includes(role)||e.getAttribute('aria-haspopup')==='menu'||/mode|experience/.test(testId);return strong&&(labels.includes(inner)||labels.includes(aria));});" +
                "const modeIsSelected=e=>!!e&&(e.getAttribute('aria-pressed')==='true'||e.getAttribute('aria-checked')==='true'||/active|selected|checked/.test(exactText(e.dataset?.state||'')));" +
                modeSelection +
                "const elementLabel=e=>exactText(e?.innerText||'')||exactText(e?.getAttribute?.('aria-label')||'');" +
                "const visible=e=>!!e&&e.isConnected&&e.offsetParent!==null;" +
                "const composerInput=document.querySelector('#prompt-textarea')||[...document.querySelectorAll('textarea,[contenteditable=\"true\"]')].filter(visible).sort((a,b)=>b.getBoundingClientRect().bottom-a.getBoundingClientRect().bottom)[0]||null;" +
                "const composerForm=composerInput?.closest?.('form')||null;" +
                "const inComposer=e=>{if(!e||!composerInput)return false;if(composerForm)return composerForm.contains(e);const a=e.getBoundingClientRect(),b=composerInput.getBoundingClientRect();return a.bottom>=b.top-240&&a.top<=b.bottom+240&&a.right>=b.left-320&&a.left<=b.right+320;};" +
                "const selectedState=e=>!!e&&(e.getAttribute('aria-checked')==='true'||e.getAttribute('aria-pressed')==='true'||e.getAttribute('aria-selected')==='true'||/^(checked|selected|active|on)$/.test(exactText(e.dataset?.state||'')));" +
                "const openMenu=e=>{if(!e)return;e.focus?.();const init={bubbles:true,cancelable:true,composed:true,button:0,buttons:1,pointerId:1,pointerType:'mouse',isPrimary:true};if(typeof PointerEvent==='function')e.dispatchEvent(new PointerEvent('pointerdown',init));else e.dispatchEvent(new MouseEvent('mousedown',init));};" +
                modelScript(requestedModel) +
                reasoningScript(requestedEffort);
    }

'''
source.write_text(text[:start] + replacement + text[end:], encoding='utf-8')

build = repo / 'app/build.gradle'
build_text = build.read_text(encoding='utf-8')
build_text = build_text.replace('versionCode 23', 'versionCode 24')
build_text = build_text.replace("versionName '0.1.22'", "versionName '0.1.22-rc2'")
build_text = build_text.replace("versionName '0.1.22-rc1'", "versionName '0.1.22-rc2'")
build.write_text(build_text, encoding='utf-8')

test = repo / 'app/src/test/java/com/shaterguy/chatgptpromptscheduler/ProjectChatModeRegressionTest.java'
test.write_text(r'''package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProjectChatModeRegressionTest {
    @Test
    public void projectChatTreatsAbsenceOfActiveWorkAsChatEvenWithoutSelectedMarker() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/project-1/project";
        schedule.experience = "chat";

        String script = AutomationScript.preferenceScript(schedule, "'run-project-chat'");

        assertTrue(script.contains("const workMode=modeCandidate(['work','작업'])"));
        assertTrue(script.contains("workSelected=modeIsSelected(workMode)"));
        assertTrue(script.contains("assumedActive:!modeSelected&&!workSelected"));
        assertTrue(script.contains("if(workSelected){"));
        assertFalse(script.contains("if(mode&&!modeSelected)"));
    }

    @Test
    public void workStillUsesBoundedSelectedStateReadback() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/project-1/project";
        schedule.experience = "work";

        String script = AutomationScript.preferenceScript(schedule, "'run-project-work'");

        assertTrue(script.contains("if(mode&&!modeSelected)"));
        assertTrue(script.contains("modeClicks>=3"));
        assertTrue(script.contains("Work 모드 실제 적용을 확인하지 못했습니다."));
        assertFalse(script.contains("assumedActive:!modeSelected&&!workSelected"));
    }
}
''', encoding='utf-8')

print('RC2 project Chat regression patch applied')
