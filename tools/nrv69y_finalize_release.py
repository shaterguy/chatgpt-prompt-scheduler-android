from pathlib import Path
p=Path('.github/workflows/android-build.yml')
s=p.read_text(encoding='utf-8')
repls=[
('chatgpt-prompt-scheduler-android-v0.1.21-test-reports','chatgpt-prompt-scheduler-android-v0.1.22-test-reports'),
('APK_NAME: chatgpt-prompt-scheduler-android-v0.1.21.apk','APK_NAME: chatgpt-prompt-scheduler-android-v0.1.22.apk'),
('SOURCE_NAME: chatgpt-prompt-scheduler-android-v0.1.21-source.zip','SOURCE_NAME: chatgpt-prompt-scheduler-android-v0.1.22-source.zip'),
("VERSION_CODE: '22'","VERSION_CODE: '23'"),
("VERSION_NAME: '0.1.21'","VERSION_NAME: '0.1.22'"),
("OLD_VERSION_CODE: '21'","OLD_VERSION_CODE: '22'"),
('gh release view v0.1.21','gh release view v0.1.22'),
('gh release download v0.1.20','gh release download v0.1.21'),
('chatgpt-prompt-scheduler-android-v0.1.21-public-signed','chatgpt-prompt-scheduler-android-v0.1.22-public-signed'),
('gh release create v0.1.21','gh release create v0.1.22'),
("--title 'ChatGPT Prompt Scheduler Android v0.1.21'","--title 'ChatGPT Prompt Scheduler Android v0.1.22'"),
('--notes-file RELEASE_NOTES-v0.1.21.md','--notes-file RELEASE_NOTES-v0.1.22.md'),
('gh release download v0.1.21 --repo "$GITHUB_REPOSITORY" --pattern "$APK_NAME"','gh release download v0.1.22 --repo "$GITHUB_REPOSITORY" --pattern "$APK_NAME"'),
('gh release download v0.1.21 --repo "$GITHUB_REPOSITORY" --pattern "$SOURCE_NAME"','gh release download v0.1.22 --repo "$GITHUB_REPOSITORY" --pattern "$SOURCE_NAME"'),
('gh release download v0.1.21 --repo "$GITHUB_REPOSITORY" --pattern SHA256SUMS.txt','gh release download v0.1.22 --repo "$GITHUB_REPOSITORY" --pattern SHA256SUMS.txt'),
]
for old,new in repls:
    if old not in s:
        raise SystemExit('missing release workflow token: '+old)
    s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')
print('release workflow updated')
