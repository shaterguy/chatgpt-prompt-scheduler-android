# ChatGPT Prompt Scheduler Android v0.1.3

## 예약 편집 화면 정리

- `general` 선택 시 고정 대상인 일반 새 대화 URL 입력란을 숨깁니다.
- `project` 선택 시 프로젝트 URL과 Chat·Work 모드만 표시합니다.
- `existing` 선택 시 기존 대화 URL만 표시하고 Chat·Work 모드는 숨깁니다.
- 기존 대화는 저장·가져오기 시 실행 모드를 `inherit`로 정규화하여 해당 대화의 현재 모드를 유지합니다.
- `once`와 `daily`에서는 요일 입력란을 숨깁니다.
- `weekly`에서만 요일 입력란을 표시하고 하나 이상의 요일을 요구합니다.
- 프로젝트 새 대화에 기존 대화 URL을 입력하는 대상 유형 불일치를 차단합니다.
- 대상 유형에 따라 URL 라벨과 예시를 프로젝트 URL 또는 기존 대화 URL로 바꿉니다.

## 배포

- versionCode: 4
- versionName: 0.1.3
- v0.1.2와 동일한 고정 서명 인증서를 사용하여 업데이트 설치를 지원합니다.
