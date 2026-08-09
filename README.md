# ChatGPT Prompt Scheduler for Android

ChatGPT 웹 세션에 예약 프롬프트를 자동 전송하는 독립 Android 앱입니다. Chrome 확장프로그램이나 외부 브라우저에 의존하지 않습니다.

- versionName: `0.1.19`
- versionCode: `20`
- Application ID: `com.shaterguy.chatgptpromptscheduler`
- Android 8.0 이상

일반·프로젝트·기존 대화, Chat·Work 모드, 일회성·매일·매주·분 간격 반복, 기존 입력 교체, 설정 JSON 내보내기·가져오기, 스마트폰·태블릿 UI를 지원합니다.

v0.1.19의 Protocol 3.3 오토런은 앱이 bootstrap 권한을 가집니다. Job ID를 자동 생성하고 지정 프로젝트에 실행 전용 일반 Chat을 만든 뒤, 첫 `AR_SEND_WORK` 때 같은 프로젝트에 Work를 지연 생성합니다. 두 대화의 실제 URL은 첫 사용자 턴과 프로젝트 경로를 함께 확인한 뒤 저장합니다. 기존 예약 생성·저장·알림·실행 경로와 JSON 형식은 변경하지 않았습니다.

## 오토런 중계 사용

1. 일반 Chat에서 작업을 충분히 논의하고 최종 `(오토런) ...` 요구사항을 준비합니다.
2. 앱에서 프로젝트 주소와 Work 모델/추론 기본값을 설정하고 요구사항을 붙여넣은 뒤 `오토런 시작`을 한 번 누릅니다.
3. 앱은 Job ID를 영속 생성하고 프로젝트의 새 일반 Chat에 요구사항과 `[AUTOMATION_BOOTSTRAP 3.3.0 ...]` metadata를 전송합니다.
4. Orchestrator의 최초 `AR_SEND_WORK`를 받으면 새 Work를 만들고 선택 모델/추론값의 실제 적용을 확인한 뒤 현재 Step을 전송합니다.
5. 이후 각 응답의 마지막 Protocol 3.3 제어 신호에 따라 두 대화를 중계합니다. 잘못된 프로젝트·대화, 로그인 필요, 선택값 미확인 또는 제출 결과 불명확 상태에서는 자동 재전송하지 않습니다.
6. `[AR_USER_ACTION_REQUIRED JOB S### R### ACTION_ID]`가 오면 사용자 조치 대기 알림을 표시합니다. 조치 후 `처리 완료`를 누르면 일반 Chat에 재검증을 요청합니다.

프로젝트·요구사항·Work 모델·추론값은 Job 시작 시 스냅샷으로 고정됩니다. 기본 설정 변경은 다음 Job부터 적용됩니다. 실행 중 Job ID와 자동 획득한 두 대화 URL은 상태 화면에서 확인할 수 있습니다.

오토런 중계는 선택 기능입니다. 사용하지 않으면 기존 예약 기능의 동작과 데이터 형식에 영향을 주지 않습니다.

v0.1.2 이후 APK는 동일한 고정 인증서로 서명되어 덮어쓰기 업데이트를 지원합니다.
