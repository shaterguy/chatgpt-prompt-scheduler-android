# ChatGPT Prompt Scheduler for Android

ChatGPT 웹 세션에 예약 프롬프트를 자동 전송하는 독립 Android 앱입니다. Chrome 확장프로그램이나 외부 브라우저에 의존하지 않습니다.

- versionName: `0.1.14`
- versionCode: `15`
- Application ID: `com.shaterguy.chatgptpromptscheduler`
- Android 8.0 이상

일반·프로젝트·기존 대화, Chat·Work 모드, 일회성·매일·매주·분 간격 반복, 기존 입력 교체, 설정 JSON 내보내기·가져오기, 스마트폰·태블릿 UI를 지원합니다.

v0.1.14에서는 일반 Chat과 Work 대화가 Protocol 3.0 제어 신호로 한 턴씩 교대하는 선택형 오토런 중계를 추가했습니다. 중계 상태는 예약 설정·대기열과 분리해 저장하며, 예약 실행이 시작되면 중계 WebView를 즉시 닫고 예약 완료 후 저장 상태에서 이어갑니다. 기존 예약 생성·저장·알림·실행 경로와 JSON 형식은 변경하지 않았습니다.

## 오토런 중계 사용

1. Automation Run 계획 턴에서 한 번도 중계에 사용하지 않은 Job ID와 서로 다른 일반 Chat·Work 대화 URL을 준비합니다.
2. 앱 홈의 `오토런 중계 열기`에서 두 URL과 Job ID를 저장한 뒤 `새로 시작`을 누릅니다.
3. 앱은 `[AUTOMATION_START ...]`부터 시작해 각 응답의 마지막 Protocol 3.0 제어 신호에 따라 대화방을 한 턴씩 교대합니다.
4. 잘못된 Job·Step·Round, 중복 신호, 대상 URL 변경, 로그인 필요 또는 전송 결과 불명확 상태에서는 자동 재전송을 하지 않고 확인 알림을 표시합니다.

오토런 중계는 선택 기능입니다. 사용하지 않으면 기존 예약 기능의 동작과 데이터 형식에 영향을 주지 않습니다.

v0.1.2 이후 APK는 동일한 고정 인증서로 서명되어 덮어쓰기 업데이트를 지원합니다.
