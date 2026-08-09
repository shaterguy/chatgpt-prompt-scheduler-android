# v0.1.19

오토런 시작 계약을 Protocol 3.3으로 개편했습니다. 새 Job은 확정 요구사항과 기본 프로젝트/Work 설정만으로 시작하며 Job ID와 두 대화 주소는 앱이 생성·확인·저장합니다.

- 프로젝트의 새 일반 Chat을 자동 생성하고 bootstrap metadata를 첫 요청에 부가합니다.
- 첫 `AR_SEND_WORK` 시점에 Work를 지연 생성하고 선택 모델/추론 정도를 실제 UI에서 확인합니다.
- 제출 직전 상태를 영속화하고, 결과가 불명확하면 자동 재전송하지 않는 fail-closed 복구를 적용합니다.
- 시작 시 프로젝트·요구사항·Work 모델·추론값을 Job 스냅샷으로 고정합니다.
- 기존 수동 URL/Job ID 입력은 신규 UI에서 제거했으며 기존 실행 데이터는 레거시 경로로 보존합니다.
- 예약 실행의 WebView 우선권과 기존 예약 저장/큐/알람 경로는 유지합니다.

패키지명은 `com.shaterguy.chatgptpromptscheduler`, versionCode는 20, versionName은 0.1.19입니다.
