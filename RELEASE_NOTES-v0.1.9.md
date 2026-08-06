# ChatGPT Prompt Scheduler Android v0.1.9

## JavaScript 실행 오류 수정

- v0.1.8에서 생성 JavaScript의 정규식 이스케이프가 Java 문자열 처리 과정에서 제어문자로 변환되어 `Invalid regular expression: missing /`가 발생한 문제를 수정했습니다.
- compose·verify 스크립트의 `\r`, `\n`, `\t`, Unicode 이스케이프를 이중 이스케이프해 WebView에 유효한 JavaScript가 전달되도록 했습니다.
- 생성된 JavaScript에 실제 CR/LF 제어문자가 포함되지 않는 회귀 테스트를 추가했습니다.
- v0.1.8의 실행 ID 기반 중복 전송 차단과 풀로그 내려받기 기능은 유지합니다.
