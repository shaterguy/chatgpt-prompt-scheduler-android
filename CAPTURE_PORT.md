# 모델·추론 캡처 이식 — 0.5.0-dev1

## 기준
- 스케쥴러 최신 정식 v0.4.0: 88dc9f2f1c71656bc1b2e6eaa9603a5517f4ff12.
- 작업 부모 main: 0535c3040a1ab558898aa7b7401668a967071685. 정식 이후 차이는 관리 보고서 3개뿐이며 기능 기준선은 동일합니다.
- SelfRun drive-v2.3.2: 9a567558d6c81a1e9cd4c9f267b0e5171df3dec7의 ProfileRegistryActivity / RequestProfileScript에서 실제 outgoing request의 1회 캡처·신호명 등록·중복 조합 판별 방식을 이식했습니다.

## 사용
1. DEV 앱의 로그인/세션에서 ChatGPT에 로그인합니다. 기존 DEV 로그인은 공유됩니다. 정식 앱과 DEV의 데이터는 별개입니다.
2. 설정 → 모델·추론 프로필 → 일반 Chat 모델·추론 캡처 또는 Work 모델·추론 캡처를 엽니다.
3. 캡처 준비 표시 후 ChatGPT에서 해당 모드·원하는 모델·추론 정도를 선택하고 짧은 프롬프트를 실제로 한 번 전송합니다. 메뉴만 바꾸면 캡처되지 않습니다.
4. 표시된 실제 제어 값을 확인하고 예약에서 선택할 신호명을 등록합니다. Chat은 조합 신호명 하나, Work는 모델·추론 신호명을 각각 사용합니다.
5. 예약 편집에서 등록한 이름을 선택합니다. 실제 요청이 동일한 조합은 재등록하지 않으며 동일 신호명의 다른 조합은 갱신 확인을 받습니다.

## 변경 경계
- 전용 foreground Dialog·WebView만 사용하며 기존 예약 실행 엔진, 알람, 대기열, JSON 가져오기, 로그인 화면과 manifest는 변경하지 않습니다.
- model / thinking_effort / conversation_origin / service_tier만 저장합니다. 없는 필드는 REMOVE를 그대로 보존합니다. 실제 전송 요청을 재작성하거나 재전송하지 않습니다.
- 문서 시작 시 정확한 ChatGPT HTTPS origin에만 캡처 스크립트를 설치합니다. native bridge는 main frame·origin·현재 세션을 검사하고 사용자 등록 전에는 영구 저장하지 않습니다.
- 프롬프트·대화 식별자·헤더·쿠키는 native capture 결과와 프로필에 넣지 않습니다. 닫기·취소·background 전환은 캡처와 확인창을 종료합니다.
- DEV 설치 ID는 com.shaterguy.chatgptpromptscheduler.dev, versionCode 5000001입니다. 정식 릴리스 및 main 기능 소스는 그대로 둡니다.

## 검증 계획
- AC-01: 실제 fetch 문자열·Request 객체·XHR 제어값 캡처 / native payload 보존.
- AC-02: 기존 registry 검증 경로·영구 저장 readback·예약 선택·중복 판별.
- AC-03: 1회 캡처·취소·재시도·오래된 비동기 결과 무시·지원하지 않는 입력 처리.
- NR-01: 기존 unit 및 Android WebView 회귀 테스트. 실행 엔진 및 등록 schema는 그대로 유지합니다.
- SAFE-01: 정확한 HTTPS origin·main frame·세션·크기·필드 제한; prompt 수집 없음; 기존 Android 권한·의존성 유지.
- REL-01: 컴파일·단위 테스트·androidTest 빌드를 에뮬레이터 시작 전에 수행하고, API 36 WebView 테스트 통과 후 동일 unsigned APK를 기존 trusted signer로 전달합니다.
- 실제 사용자 계정의 ChatGPT 메뉴·모델 가용성 및 사용자 기기 UI 확인은 자동 fixture 검증과 구분하는 사후 확인 항목입니다.

검증 결과는 이 후보의 GitHub Actions 실행 및 전달 보고에서 확인합니다. 이 문서의 계획을 테스트 PASS로 간주하지 않습니다.
