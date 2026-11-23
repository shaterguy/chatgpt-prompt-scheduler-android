# 0.6.2-dev12 — 마지막 모델 저장 거부와 대화별 모델 적용 분리

지난 dev11 실행 로그에서 ChatGPT가 명시적으로 Failed to meet eligibility criteria for updating user's last used model config.를 반환한 것을 확인했습니다.

Chat 모드에서 위 정확한 반환값을 받은 경우에는 PREPARED_LOCAL 상태로 분기하여 ChatGPT 네이티브 선택 상태를 저장합니다. 대상 대화에 진입하기 전에 기존 요청별 프로필 적용 기능을 해당 실행에만 활성화하고, 전송 요청의 model·thinking_effort 값과 제거 필드를 선택한 프로필에 맞춰 적용합니다. 프롬프트·메시지·대화 ID 등 나머지 필드는 보존합니다.

전송 검증에서는 모델·추론값의 요청 적용 영수증이 확인되지 않으면 MODEL_ENFORCEMENT_UNVERIFIED 실패로 분류합니다. 정상 서버 저장 성공, 다른 서버 오류, Work 모드, 이전 대화 또는 모델 설정 상속 예약은 이전 동작을 유지합니다.

지원하지 않는 웹 요청 구조에는 다른 모델로 성공했다고 표시하지 않고 기존 fail-closed 동작을 적용합니다.

- applicationId: com.shaterguy.chatgptpromptscheduler.dev
- versionCode: 6002012
- versionName: 0.6.2-dev12
