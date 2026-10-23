# 0.6.1-dev4

- 자동 갱신 프로필 문서와 기존 동기화 경로를 유지하고 canonical operation의 모델·추론 원본값을 사용합니다.
- 명시한 새 대화 프로필은 ChatGPT의 서버 마지막 선택 설정, native atom 및 Work cookie에 먼저 저장하고 영구 저장 완료를 확인합니다.
- `model` URL 인자 없이 일반 첫 화면 또는 canonical 프로젝트 주소로 다시 진입한 뒤 내부 선택값을 읽어 확인합니다. 일치할 때만 입력·전송을 진행합니다.
- Instant·Pro의 `thinking_effort: REMOVE`는 서버 설정 query에서 생략하고 이전 Thinking 선택값은 `null`로 지웁니다.
- Work의 Ultra는 top-level 강도에 저장하고, Ultra를 허용하지 않는 `lastStartedModelConfig`에는 넣지 않습니다.
- 예약 실행의 fetch/XHR 전송 인터셉터 설치를 제거합니다. 기존 대화·현재 설정 유지, 자동 프로필 갱신, 전송 marker와 대화 주소 검증은 유지합니다.
- DEV package `com.shaterguy.chatgptpromptscheduler.dev`, versionCode `6001004`, versionName `0.6.1-dev4`입니다.

브라우저 테스트 페이지의 내부 설정 방식은 사용자가 Instant·Pro와 Chat·Work 추론 조합에서 작동을 확인했습니다. Android 앱의 실제 로그인 세션 예약 실행은 새 APK 설치 후 확인할 항목입니다. 내부 프런트엔드 모듈이 바뀌면 준비 단계에서 실패 처리하고 프롬프트를 전송하지 않습니다.
