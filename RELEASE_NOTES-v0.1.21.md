# v0.1.21

- `새 작업` 화면은 프로젝트 주소만 기본값으로 유지합니다. 이전 Job의 요구사항, Work 모델·추론값, 현재 동작, 신호, 오류 및 로그를 새 작업 입력 화면에 불러오지 않습니다.
- 새 작업의 Work 모델과 추론 정도는 `inherit`에서 시작하며, 사용자가 현재 화면에서 고른 값은 화면 회전 동안에만 유지됩니다.
- 오토런 작업 화면의 `처리 완료` 버튼을 제거하고 사용자 조치 완료 기능을 `재개`에 통합했습니다.
- `WAITING_USER`에서 `재개`를 누르면 앱이 `[AUTOMATION_USER_RESOLVED JOB ACTION_ID]`를 일반 Chat에 보내 재검증을 요청합니다. 그 외 상태의 `재개`는 기존처럼 두 대화방을 확인해 중계 상태를 재구성합니다.
- 실행 중 Job의 상태와 작업별 실행·디버그 로그는 작업 목록과 해당 Job 화면에 그대로 보존됩니다.
- 기존 예약 실행 우선권, AlarmEngine, QueueStore, ExecutionService, 예약 데이터와 알림 경로는 변경하지 않았습니다.

패키지명은 `com.shaterguy.chatgptpromptscheduler`, versionCode는 22, versionName은 0.1.21입니다. 공개 서명 인증서는 기존 릴리즈와 동일하게 유지됩니다.
