# v0.1.20

오토런 첫 화면을 작업 이력 대시보드로 바꿨습니다. 상단 `새 작업`에서 기존 설정 화면으로 이동하고, 각 Job 카드에서 요구사항·프로젝트·Chat/Work 연결·Step/Round·최근 수행 항목을 확인할 수 있습니다.

- Job별 실행 로그와 디버그 로그를 분리했습니다. 실행 로그는 주요 상태만 읽기 쉽게 표시하고, 디버그 로그는 원문·URL·쿠키·토큰을 제외한 redacted JSONL로 보관·내보냅니다.
- 각 Job의 디버그 로그를 별도 제한 파일에 보존하여 다른 작업의 로그와 섞이지 않게 했습니다.
- ChatGPT 대화 입력창 탐색을 명시적 prompt ID 또는 `main form` 내부로 제한해 다른 Lexical 편집기를 초안으로 오인하는 `DRAFT_PRESENT` 회귀를 수정했습니다.
- 실제 초안 충돌은 계속 fail-closed 하며, 원문 대신 길이와 fingerprint만 디버그 로그에 기록합니다.
- Protocol 3.3 자동 Job/Chat/Work provisioning, 모델·추론 readback, 앱 재시작 복구, 예약 실행 최우선 규칙을 유지합니다.

패키지명은 `com.shaterguy.chatgptpromptscheduler`, versionCode는 21, versionName은 0.1.20입니다.
