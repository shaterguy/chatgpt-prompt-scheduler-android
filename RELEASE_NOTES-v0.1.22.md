# ChatGPT Prompt Scheduler Android v0.1.22

## 주요 수정

- 일반 예약의 Work 모드 선택을 v0.1.15 정상 기준으로 복구했습니다. 선택 클릭 뒤 실제 UI 상태를 다시 읽으며 최대 3회까지 제한 재시도하고, 끝내 확인되지 않으면 `WORK_MODE_SELECT_FAILED`로 구분합니다.
- 오토런 재개는 Chat/Work 양쪽의 실제 제어 신호를 기준으로 Step 숫자 → Round 숫자 순서만 사용합니다. 같은 Step/Round에서 Chat의 `AR_USER_ACTION_REQUIRED`가 있으면 Chat을, 그 외에는 Work를 우선합니다. 한쪽에만 유효한 신호가 있어도 복구 후보로 사용합니다.
- 사용자가 `재개`를 누른 경우 최신 상태가 `AR_USER_ACTION_REQUIRED`이면 해당 조치 완료 의도를 보존하고 `AUTOMATION_USER_RESOLVED` 재검증을 한 번 수행하도록 수정했습니다.
- 같은 conversation ID가 `/g/<project>/c/<id>`와 `/c/<id>` 사이에서 정규화되어도 같은 대화로 인정합니다.
- 프로젝트 루트, ChatGPT 홈, `about:blank` 등 conversation ID가 잠시 보이지 않는 전환 경로는 즉시 `TARGET_CHANGED`로 실패시키지 않고 원래 대화 URL을 다시 엽니다.
- main-frame HTTP 429는 일시적 rate-limit 상태로 취급하여 adaptive retry 후 원래 대화를 복구합니다. 실제 다른 `/c/<conversation-id>`가 확인된 경우만 진짜 대상 변경으로 중단합니다.
- `TARGET_CHANGED` 발생 시 전체 URL 대신 expected/actual project ID와 conversation ID만 redacted 진단 정보로 기록하도록 보강했습니다.

## 검증

- 단위 테스트 전체 통과
- release APK 빌드 통과
- 일반 예약·오토런 재개·대화 식별·중복·terminal·잘못된 신호·Work 모드 재시도 회귀 테스트 포함
- 기존 예약 실행 우선순위 및 Automation Runtime Gate 유지

## 업데이트 호환성

- versionName: `0.1.22`
- versionCode: `23`
- Application ID: `com.shaterguy.chatgptpromptscheduler`
- 기존 공개판 서명 인증서를 유지하여 v0.1.21 등 기존 설치본 위에 업데이트 설치할 수 있도록 릴리즈 워크플로에서 인증서·versionCode 상승을 검증합니다.
