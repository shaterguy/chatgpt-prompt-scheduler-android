from pathlib import Path

path = Path('app/src/test/java/com/shaterguy/chatgptpromptscheduler/ProjectDuplicatePreventionTest.java')
text = path.read_text(encoding='utf-8')
old = '''        assertTrue(script.contains("desiredModeLabels=['chat','채팅']"));\n'''
new = '''        assertTrue(script.contains("const mode=modeCandidate(['chat','채팅'])"));\n        assertTrue(script.contains("const workMode=modeCandidate(['work','작업'])"));\n        assertTrue(script.contains("workSelected=modeIsSelected(workMode)"));\n        assertTrue(script.contains("assumedActive:!modeSelected&&!workSelected"));\n'''
if old not in text:
    raise SystemExit('expected legacy mode assertion not found')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
print('ProjectDuplicatePreventionTest aligned with RC2 project Chat semantics')
