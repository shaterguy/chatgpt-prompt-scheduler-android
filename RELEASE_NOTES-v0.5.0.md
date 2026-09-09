# ChatGPT Prompt Scheduler Android v0.5.0

검증 완료된 `v0.5.0-dev2` 기능 상태를 기존 정식 앱 설치·서명 계보로 승격한 릴리스입니다. DEV 앱 자체를 정식 앱으로 전환하지 않고 동일한 검증 기능 소스를 production identity로 다시 패키징합니다.

## 주요 변경

- 설정의 모델·추론 프로필 화면에서 일반 Chat 또는 Work를 열고 실제 ChatGPT 요청을 한 번 전송해 현재 모델·추론 제어값을 캡처할 수 있습니다.
- 캡처 결과는 `model`, `thinking_effort`, `conversation_origin`, `service_tier` 제어 필드만 다루며, 없는 필드는 `REMOVE`로 보존합니다. 프롬프트·대화 식별자·헤더·쿠키는 프로필에 저장하지 않습니다.
- 캡처한 조합은 신호명으로 등록해 예약에서 재사용하고, 동일 조합의 중복 등록과 동일 신호명의 다른 조합을 구분합니다.
- 프로젝트 예약 실행은 ChatGPT의 `/projects` 목록에서 저장된 프로젝트 표시명에 맞는 실제 선택 행을 찾아 클릭한 뒤 기존 canonical 프로젝트 URL로 진입했는지 확인합니다. 저장된 경로를 임의로 직접 합성하지 않습니다.
- 프로젝트가 아직 목록에 없거나 화면이 준비되지 않은 경우 다른 프로젝트를 선택하지 않고 재시도하며, 동명이인 프로젝트 후보는 저장된 순서로 구분합니다.
- 기존 예약 실행 엔진, 알람, 대기열, Schedule JSON 스키마, Android 권한·의존성·외부 네트워크 대상은 변경하지 않습니다.

## 정식 설치 계보

- Application ID: `com.shaterguy.chatgptpromptscheduler`
- versionName: `0.5.0`
- versionCode: `2100000005`
- 공개 서명 인증서 SHA-256: `3cfe95acd09077a89cd8de85434cbd5d8bb3e2021d8e9eacb804a8da9ccce52a`
- 업데이트 기준선: 최신 정식 `v0.4.0` (`versionCode 2100000004`)

정식 릴리스 workflow는 `v0.4.0` APK의 Application ID와 공개 서명 인증서를 확인하고 `2100000004 < 2100000005`를 검증한 뒤 정식 APK를 서명·게시합니다.

## 검증 기준선

승격 기능 기준선은 `v0.5.0-dev2` 커밋 `4c2a3179fe317a89f9a009e0968cd7e33b4e15a0`입니다. DEV source run `34300493672`에서 단위 테스트, release/debug/androidTest 빌드와 Android API 36 WebView 회귀 테스트가 통과했습니다.

정식 승격 과정에서는 기능 소스나 검증 로직을 새로 변경하지 않고 정식 package identity, 표시명, 최신 정식 업데이트 기준선, 릴리스 자산명·문서와 이에 직접 결합된 identity 계약만 정합화합니다.
