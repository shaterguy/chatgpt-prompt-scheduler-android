# ChatGPT Prompt Scheduler Android v0.1.8

## 중복 전송 차단

- 프롬프트 전송 버튼을 누르기 직전에 실행 ID별 중복 방지 마커를 `localStorage`와 `sessionStorage`에 저장합니다.
- 전송 클릭 직후 ChatGPT가 새 대화 URL로 이동해 JavaScript 콜백이 무효화되더라도, 다음 자동화 단계에서 동일 실행 마커를 확인하고 다시 클릭하지 않습니다.
- 대상 대화 복구 또는 WebView 재시도가 발생해도 동일 실행 ID에서는 전송 버튼을 최대 한 번만 누릅니다.
- 중복 방지 마커를 저장하지 못하면 전송을 수행하지 않고 실패 처리합니다.
- 전송 여부가 불명확할 때는 중복 전송보다 미전송 실패를 우선하는 at-most-once 정책을 적용합니다.

## 기존 기능 유지

- 비공개 가상 디스플레이 기반 백그라운드 WebView 입력
- 다단계 ChatGPT 편집기 입력 전략
- 프로젝트·기존 대화 URL 검증과 대상 복구
- 실행별 풀로그 JSON 내려받기

## 업데이트

- versionCode: 9
- versionName: 0.1.8
- 기존 고정 서명 인증서를 유지합니다.
