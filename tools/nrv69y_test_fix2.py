from pathlib import Path
p=Path('app/src/test/java/com/shaterguy/chatgptpromptscheduler/OrchestrationSignalTest.java')
s=p.read_text(encoding='utf-8')
repls={
'assertTrue(service.contains("classifyConversationTarget"));':'assertTrue(service.contains("isTransientExpectedTarget"));',
'assertTrue(service.contains("TARGET_PROMPT_ALREADY_PRESENT"));':'assertTrue(service.contains("RESUME_TARGET_SCAN_RESULT"));'
}
for old,new in repls.items():
    if old not in s: raise SystemExit('missing '+old)
    s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')
print('test fix2 ok')
