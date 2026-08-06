# ChatGPT Prompt Scheduler Android v0.1.13

## Ultra 추론 강도

- Work 모드에서 울트라 및 ultra 표기를 인식해 가장 높은 추론 강도로 선택할 수 있습니다.
- 기존 inherit, Sol, Terra, Luna 모델 선택과 예약 실행 경로는 유지됩니다.
- 설정 저장·불러오기, 대기열, 실행 서비스, 생성 스크립트까지 ultra 값이 보존됩니다.
- 기존 예약은 변경 없이 계속 사용할 수 있습니다.

## 검증 범위

- 한국어·영어 Ultra 메뉴 표기와 기존 xhigh 이하 옵션과의 우선순위를 단위 테스트로 검증했습니다.
- PR 워크플로에서 단위 테스트와 unsigned Release APK 빌드를 수행합니다.
- PR 검증에서는 서명 시크릿을 사용하지 않습니다.

## 업데이트 호환성

- Application ID는 변경하지 않았습니다.
- 서명 인증서 SHA-256은 기존과 동일하게 유지합니다.
- versionCode는 14, versionName은 0.1.13입니다.
- Android 8.0 이상 지원과 기존 예약 JSON 호환성을 유지합니다.
