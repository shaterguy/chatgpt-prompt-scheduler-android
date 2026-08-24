# ChatGPT Prompt Scheduler Android v0.3.0

`v0.3.0-dev8`의 검증된 기능 상태를 기존 정식 앱 계보로 승격한 릴리스입니다.

## 주요 변경

- 일반 Chat 예약에 현재 설정 유지, Instant, Medium, High, Extra High, Pro 추론 수준 선택을 추가했습니다.
- 기존 예약 JSON에 Chat 추론 값이 없으면 현재 설정 유지로 처리합니다.
- 로그인/세션 WebView에서 사용자가 실제 방문한 ChatGPT 프로젝트를 등록하고 프로젝트 예약 대상으로 선택할 수 있습니다.
- Chat 모드 전환을 제한된 재시도와 선택 상태 readback으로 검증한 뒤 추론 설정과 프롬프트 전송을 진행합니다.
- Android System WebView 기반 회귀 테스트로 Chat/Work 모드 전환과 모드 확인 후 추론 선택 경로를 검증합니다.
- Protocol 3.x AutoRun 대화 중계 기능과 관련 UI·서비스·상태·알림·부팅 복구를 제거했습니다. 예약 실행 엔진은 유지됩니다.

## 정식 설치 계보

- Application ID: `com.shaterguy.chatgptpromptscheduler`
- versionName: `0.3.0`
- versionCode: `2100000001`
- 공개 서명 인증서 SHA-256: `3cfe95acd09077a89cd8de85434cbd5d8bb3e2021d8e9eacb804a8da9ccce52a`
- 업데이트 기준선: 최신 정식 `v0.2.0` (`versionCode 2100000000`)

정식 릴리스 workflow는 기존 `v0.2.0` APK의 Application ID와 공개 서명 인증서를 확인하고, 기존 versionCode가 새 versionCode보다 낮은지 검증한 뒤 서명·게시합니다.

## 검증 기준선

승격 기능 기준선은 `v0.3.0-dev8` 커밋 `eff93ed678ba7c233fd71f09a70644dfbd584965`입니다. 해당 커밋의 DEV CI에서 Android WebView 회귀 테스트, 단위 테스트, release APK 빌드 및 패키지 메타데이터 검증이 통과했고, main-controlled trusted signer의 독립 서명·manifest·권한·인증서 검증도 통과했습니다.
