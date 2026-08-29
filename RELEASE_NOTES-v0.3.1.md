# ChatGPT Prompt Scheduler Android v0.3.1

`v0.3.1-dev1`의 검증된 기능 상태를 기존 정식 앱 계보로 승격한 릴리스입니다.

## 주요 변경

- 새 대화의 Chat·Work 모델·추론 선택을 DOM 메뉴 조작 대신 AndroidX WebKit document-start 요청 프로필 방식으로 적용합니다.
- 대화 요청에서는 `model`, `thinking_effort`, `conversation_origin`, `service_tier` 네 제어 필드만 변경하며 그 밖의 요청 데이터가 달라지면 전송을 차단합니다.
- 기존 대화(`existing`)는 네이티브 프로필 상속을 유지하고 요청 프로필 인터셉터를 설치하지 않습니다.
- 캡처되지 않았거나 불완전한 프로필은 임의 추정하지 않고 전송 전에 fail-closed 처리합니다.
- Android System WebView 회귀 테스트와 단위 계약 테스트로 document-start 적용, 대상 요청 경로 allowlist, 비제어 데이터 보존을 검증했습니다.

## 정식 설치 계보

- Application ID: `com.shaterguy.chatgptpromptscheduler`
- versionName: `0.3.1`
- versionCode: `2100000002`
- 공개 서명 인증서 SHA-256: `3cfe95acd09077a89cd8de85434cbd5d8bb3e2021d8e9eacb804a8da9ccce52a`
- 업데이트 기준선: 최신 정식 `v0.3.0` (`versionCode 2100000001`)

정식 릴리스 workflow는 기존 `v0.3.0` APK의 Application ID와 공개 서명 인증서를 확인하고, 기존 versionCode가 새 versionCode보다 낮은지 검증한 뒤 서명·게시합니다.

## 검증 기준선

승격 기능 기준선은 `v0.3.1-dev1` 커밋 `7fce2442c3c414eb62b0e2183a900074d90a7cd4`입니다. 해당 커밋의 DEV CI `33229677252`에서 Android API 36 WebView 회귀 테스트, 단위 테스트와 release APK 빌드가 통과했고, main-controlled trusted signer 실행 `33229876308`에서 manifest allowlist, 고정 공개 인증서, APK v2/v3 서명, zipalign과 체크섬 검증이 통과했습니다.

정식 승격 과정에서는 기능 소스를 변경하지 않고 정식 패키지 identity, 최신 정식 업데이트 기준선, 릴리스 메타데이터와 이에 직접 결합된 identity 계약 테스트만 정합화합니다.
