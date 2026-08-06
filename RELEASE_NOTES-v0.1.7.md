# ChatGPT Prompt Scheduler Android v0.1.7

## 입력 안정화

- 서비스 컨텍스트의 분리된 WebView 대신, 물리 화면에 표시되지 않는 비공개 가상 디스플레이와 Presentation 창에 WebView를 연결합니다.
- WebView가 실제 창에 연결된 상태에서 포커스를 확보하도록 해 ChatGPT contenteditable 편집기의 입력 반영 실패를 줄입니다.
- textarea 네이티브 setter, `execCommand`, `beforeinput`, paste 이벤트, DOM+input의 다단계 입력 전략을 순환 적용합니다.
- 입력값 비교 시 줄바꿈·공백·제로폭 문자 차이를 정규화하고, 현재 ChatGPT 전송 버튼 식별자를 추가 지원합니다.
- WebView 렌더러 종료, 메인 프레임 HTTP/SSL 오류, JavaScript 경고·오류와 입력기 진단을 기록합니다.

## 실패 풀로그

- 각 실행에 WebView 생성·창 연결 상태, 페이지 이동, 자동화 시도, 입력기 상태, 전송 검증, 오류와 기기·WebView 버전을 구조화 JSON으로 저장합니다.
- 실행 기록의 실패 카드에서 해당 실행의 풀로그 JSON을 내려받을 수 있습니다.
- 전체 실행 기록의 풀로그 JSON도 한 번에 내려받을 수 있습니다.
- 실패 알림에 `풀로그 내려받기` 작업을 추가했습니다.

## 업데이트

- versionCode: 8
- versionName: 0.1.7
- v0.1.2 이후와 동일한 고정 서명 인증서를 사용하므로 기존 앱 위에 업데이트 설치할 수 있습니다.
