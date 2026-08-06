# ChatGPT Prompt Scheduler Android v0.1.5

## 변경 사항

- `DRAFT_PRESENT` 차단 로직을 제거했습니다.
- 예약 실행 시 ChatGPT 입력창에 기존 문장이 있으면 모두 지운 뒤 이번 예약 프롬프트로 교체합니다.
- 이전 실행에서 남은 문장, ChatGPT가 복원한 초안, 사용자가 입력한 문장 여부와 관계없이 예약 프롬프트를 우선합니다.
- textarea와 contenteditable 입력창 모두 네이티브 입력 이벤트를 발생시켜 교체합니다.
- 입력 교체 후 안정화 확인을 거쳐 전송 버튼을 클릭합니다.

## 업데이트

- versionCode: 6
- versionName: 0.1.5
- v0.1.2 이후와 동일한 고정 서명 인증서를 사용하므로 기존 앱 위에 업데이트 설치할 수 있습니다.
