# ChatGPT Prompt Scheduler Android v0.5.3

`v0.5.2` 정식 APK의 Android 앱 표시명에 개발판 문구가 남아 있던 패키징 오류를 바로잡는 정식 수정 릴리스입니다. 제품 기능은 `v0.5.2`와 동일하며 기능 코드·권한·네트워크 동작·데이터 계약을 변경하지 않습니다.

## 수정 내용

- 앱 표시명: `ChatGPT Prompt Scheduler DEV` → `ChatGPT Prompt Scheduler`
- Application ID: `com.shaterguy.chatgptpromptscheduler` 유지
- versionName: `0.5.3`
- versionCode: `2100000008`
- 공개 서명 인증서 SHA-256: `3cfe95acd09077a89cd8de85434cbd5d8bb3e2021d8e9eacb804a8da9ccce52a`
- 업데이트 기준선: 최신 정식 `v0.5.2` (`versionCode 2100000007`)

## 기능 기준선

`v0.5.2`의 기능 상태를 그대로 사용합니다. `v0.5.2`는 검증된 `v0.5.2-dev4` 기능 계보에서 승격되었으며, 이번 릴리스에서는 정식 표시명과 그에 직접 결합된 버전·릴리스 identity만 변경합니다.

정식 릴리스 workflow는 `v0.5.2` APK의 Application ID와 공개 서명 인증서를 확인하고 `2100000007 < 2100000008`을 검증한 뒤 정식 APK를 서명·게시합니다.
