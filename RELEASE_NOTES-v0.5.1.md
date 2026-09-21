# ChatGPT Prompt Scheduler Android v0.5.1

검증 완료된 `v0.5.1-dev1` 기능 상태를 기존 정식 앱 설치·서명 계보로 승격한 릴리스입니다. DEV 앱 자체를 정식 앱으로 전환하지 않고, 검증된 기능 소스를 최신 `main`의 유지관리 이력 위에 통합한 뒤 production identity로 패키징합니다.

## 주요 변경

- 예약 프롬프트 전송 후 실제 ChatGPT 대화 주소가 생성되었는지 확인합니다.
- 일반 Chat, 프로젝트, 기존 대화별로 현재 주소가 기대한 대상 컨텍스트와 일치하는지 검증합니다.
- 실제 대화 안에 전송한 사용자 프롬프트가 확인되어야 완료로 판정합니다.
- 일반 Chat 홈이나 프로젝트 루트처럼 아직 `/c/...` 대화 주소가 생성되지 않은 상태는 조기 성공 처리하지 않고 계속 확인합니다.
- 검증이 완료된 실제 대화 주소를 실행 기록의 `conversationUrl`에 저장합니다.
- 기존 schema 2 실행 기록은 `conversationUrl`이 없어도 계속 읽을 수 있습니다.
- 기존 예약 실행 엔진, 알람, 대기열, 요청 프로필, Android 권한·의존성·외부 네트워크 대상은 변경하지 않습니다.

## 정식 설치 계보

- Application ID: `com.shaterguy.chatgptpromptscheduler`
- versionName: `0.5.1`
- versionCode: `2100000006`
- 공개 서명 인증서 SHA-256: `3cfe95acd09077a89cd8de85434cbd5d8bb3e2021d8e9eacb804a8da9ccce52a`
- 업데이트 기준선: 최신 정식 `v0.5.0` (`versionCode 2100000005`)

정식 릴리스 workflow는 `v0.5.0` APK의 Application ID와 공개 서명 인증서를 확인하고 `2100000005 < 2100000006`을 검증한 뒤 정식 APK를 서명·게시합니다.

## 검증 기준선

승격 기능 기준선은 `v0.5.1-dev1` 커밋 `5bccb643cce79f927b97270d370309e2758d2c72`입니다. 이 후보의 독립 검증에서 단위 테스트 68건과 Android/WebView 테스트 20건, 총 88건이 모두 통과했습니다.

정식 승격 과정에서는 기능 소스의 동작 의미를 새로 변경하지 않고 정식 package identity, 표시명, 최신 정식 업데이트 기준선, 릴리스 자산명·문서와 이에 직접 결합된 identity 계약만 정합화합니다.
