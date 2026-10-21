# ChatGPT Prompt Scheduler Android v0.5.5

정식 v0.5.4의 프로젝트·전송 기능을 그대로 유지하면서, ChatGPT 리치 텍스트 편집기의 공백 처리 차이 때문에 발생하는 COMPOSER_EDITOR_STATE_REVERTED 오판정을 수정한 정식 유지보수 릴리스입니다.

## 주요 개선 사항

- **정상 입력 유지:** DOM에서 읽은 텍스트의 공백·줄바꿈 표현이 원본과 달라도 단어와 실제 문자가 동일하면 입력 성공으로 인정합니다. 임의의 글자 변경, 문장 누락·중복, 단어 병합은 통과시키지 않습니다.
- **편집기 안정화:** 입력 직후 편집기 상태가 갱신되는 동안 즉시 내용을 삭제하지 않고 일정 시간 기다린 다음 재확인합니다.
- **기존 기능 보존:** v0.5.4의 DOM 입력·제출, 프로젝트 ID/URL 탐색, 실제 서버 대화 URL 성공 판정, 설정·예약 및 로그인 데이터 저장 구조는 변경하지 않습니다.

## 정식 업데이트 계보

- 패키지: com.shaterguy.chatgptpromptscheduler
- 표시명: ChatGPT Prompt Scheduler
- 버전: 0.5.5 / versionCode 2100000010
- 이전 정식판: v0.5.4 / versionCode 2100000009
- 공개판 인증서 SHA-256: 3cfe95acd09077a89cd8de85434cbd5d8bb3e2021d8e9eacb804a8da9ccce52a

## 검증

동일 수정 소스에 대한 Node JavaScript 실제 비교 회귀 테스트와 로컬 Android 단위 테스트 140개를 통과했습니다. 정식 앱 identity로 별도 로컬 빌드 및 테스트를 수행하고, 정식 서명·릴리스 게시 후 게시 APK와 v0.5.4 인증서·버전 계보를 검증합니다. 사용자의 Galaxy S25에서 웹 ChatGPT 실사용 동작은 사후 검증 대상입니다.
