from pathlib import Path

repo = Path('.')
source = repo / 'app/src/main/java/com/shaterguy/chatgptpromptscheduler/AutomationScript.java'
text = source.read_text(encoding='utf-8')

strict_token = "modeClicks>=3)return result('MODE_SELECTION_FAILED','Work 모드 실제 적용을 확인하지 못했습니다.'"
baseline_token = "if(mode&&!modeSelected&&!modePrior)"
if strict_token in text:
    start_marker = '        if ("work".equals(schedule.experience)) {\n            modeSelection =\n'
    else_marker = '        } else {\n            modeSelection =\n'
    start = text.index(start_marker)
    end = text.index(else_marker, start)
    replacement = r'''        if ("work".equals(schedule.experience)) {
            // v0.1.15 baseline: click Work once, remember that action for this run,
            // then continue on the next evaluation. The current ChatGPT project UI does
            // not expose a reliable aria/data selected marker after the click.
            modeSelection =
                    "const mode=modeCandidate(['work','작업']);" +
                    "let modePrior='';try{modePrior=sessionStorage.getItem(modeKey)||'';}catch(_){}" +
                    "const modeSelected=modeIsSelected(mode);" +
                    "const modeDiagnostics={requested:'work',candidateFound:!!mode,candidateLabel:mode?clip(exactText((mode.innerText||'')+' '+(mode.getAttribute('aria-label')||'')),120):'',selected:modeSelected,clicked:false,priorClick:!!modePrior};" +
                    "if(mode&&!modeSelected&&!modePrior){const value=JSON.stringify({at:Date.now(),label:modeDiagnostics.candidateLabel});try{sessionStorage.setItem(modeKey,value);}catch(_){}window[modeKey]=value;mode.click();modeDiagnostics.clicked=true;}" +
                    "if(modeDiagnostics.clicked)return result('RETRY','모드 전환 반영 대기',{...routeDiagnostics,mode:modeDiagnostics});";
'''
    text = text[:start] + replacement + text[end:]
    source.write_text(text, encoding='utf-8')
elif baseline_token not in text:
    raise SystemExit('neither RC2 strict Work logic nor RC3 baseline Work logic was found')

build = repo / 'app/build.gradle'
build_text = build.read_text(encoding='utf-8')
if 'versionCode 24' in build_text:
    build_text = build_text.replace('versionCode 24', 'versionCode 25', 1)
elif 'versionCode 25' not in build_text:
    raise SystemExit('unexpected versionCode while preparing RC3')
if "versionName '0.1.22-rc2'" in build_text:
    build_text = build_text.replace("versionName '0.1.22-rc2'", "versionName '0.1.22-rc3'", 1)
elif "versionName '0.1.22-rc3'" not in build_text:
    raise SystemExit('unexpected versionName while preparing RC3')
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
        assertFalse(script.contains("if(mode&&!modeSelected&&!modePrior)"));
    }

    @Test
    public void workRestoresVersion015OneShotPriorClickLatch() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/project-1/project";
        schedule.experience = "work";

        String script = AutomationScript.preferenceScript(schedule, "'run-project-work'");

        assertTrue(script.contains("const mode=modeCandidate(['work','작업'])"));
        assertTrue(script.contains("let modePrior=''"));
        assertTrue(script.contains("priorClick:!!modePrior"));
        assertTrue(script.contains("if(mode&&!modeSelected&&!modePrior)"));
        assertTrue(script.contains("mode.click()"));
        assertFalse(script.contains("modeClicks>=3"));
        assertFalse(script.contains("Work 모드 실제 적용 상태 대기"));
        assertFalse(script.contains("Work 모드 실제 적용을 확인하지 못했습니다."));
        assertFalse(script.contains("assumedActive:!modeSelected&&!workSelected"));
    }
}
''', encoding='utf-8')

print('RC3 restores v0.1.15 one-shot Work latch while preserving RC2 Chat handling')
