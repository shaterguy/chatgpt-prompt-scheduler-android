# ChatGPT Prompt Scheduler Android v0.1.14

## Protocol 3.0 오토런 중계

- 일반 Chat과 Work 대화 URL 및 Job ID를 지정하면 두 대화가 한 턴씩 교대합니다.
- 앱은 `[AR_SEND_WORK ...]`, `[AR_SEND_CHAT ...]`과 종료 신호만 엄격히 수락합니다.
- Job ID가 다르거나 중복·과거 Step/Round인 신호는 자동 전송하지 않고 안전하게 일시정지합니다.
- 미전송 프롬프트와 응답 대기 상태를 별도 저장소에 보존해 앱 프로세스나 WebView 렌더러 종료 뒤에도 이어갈 수 있습니다.
- Job ID 재사용과 같은 대화 URL의 양측 지정을 차단합니다.
- 전송을 준비와 커밋으로 분리하고 커밋 결과가 불명확하면 자동 재전송하지 않습니다.

## 예약 기능 보존

- 기존 Schedule 및 QueueStore 스키마는 변경하지 않았습니다.
- 예약 실행이 시작되면 오토런 WebView를 즉시 닫고 예약 실행을 우선합니다.
- 예약 실행이 끝나면 오토런이 저장된 상태에서 재개됩니다.
- 기존 예약 CRUD, 정확한 알람, 알림, 로그인 세션, 실행 로그, 설정 JSON 형식은 그대로 유지됩니다.

## 업데이트 호환성

- Application ID는 `com.shaterguy.chatgptpromptscheduler`로 유지합니다.
- 공개 서명 인증서 SHA-256은 `3cfe95acd09077a89cd8de85434cbd5d8bb3e2021d8e9eacb804a8da9ccce52a`로 유지합니다.
- versionCode는 15, versionName은 0.1.14입니다.
- Android 8.0 이상을 지원합니다.
