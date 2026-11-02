# ChatGPT Prompt Scheduler Android v0.6.1

검증된 v0.6.1-dev10 (d3b655193d2d48aefa2972727cac15e7d47c66b5)의 기능 소스를 정식 채택했습니다.

- 예약·실행 기록·설정 탐색과 화면 구성을 개선했습니다.
- 모델·추론 사전 설정 및 쿠키 저장 교정, 여러 줄 프롬프트 입력 개선을 포함합니다.
- 정식 패키지: com.shaterguy.chatgptpromptscheduler
- 버전: 0.6.1 / versionCode 2100000013
- 기존 정식 v0.6.0과 동일한 공개판 서명을 사용하며 DEV 앱과 별도로 설치됩니다.
- 기존 dev10 단위 테스트 171개 통과 증거를 재사용합니다.
- 기능 소스와 테스트는 dev10 그대로 유지합니다. release-init.gradle로 정식 패키지·버전을 지정하고 release 리소스로 정식 표시명만 덮어씁니다.
- 정식 서명·APK 무결성·최신 정식판과의 package/certificate/versionCode 호환성을 확인합니다.
- 실제 기기의 설치 업데이트·예약 전송과 화면 렌더링은 자동 연결 환경이 없는 경우 미검증 항목으로 구분합니다.
