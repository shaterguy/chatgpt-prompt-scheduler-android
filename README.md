# ChatGPT Prompt Scheduler for Android

ChatGPT 웹 세션에 예약 프롬프트를 자동 전송하는 독립 Android 앱입니다. Chrome 확장프로그램이나 외부 브라우저에 의존하지 않습니다.

- versionName: `0.1.13`
- versionCode: `14`
- Application ID: `com.shaterguy.chatgptpromptscheduler`
- Android 8.0 이상

일반·프로젝트·기존 대화, Chat·Work 모드, 일회성·매일·매주·분 간격 반복, 기존 입력 교체, 설정 JSON 내보내기·가져오기, 스마트폰·태블릿 UI를 지원합니다.

v0.1.13에서는 Work 모드에서 `inherit`, `Sol`, `Terra`, `Luna` 모델을 예약별로 선택하고 `울트라` (`ultra`) 추론 강도를 사용할 수 있습니다. 실행 시 모델을 먼저 확인·선택한 뒤 추론 강도를 적용하며, 두 값이 모두 일치한 경우에만 프롬프트를 전송합니다. 또한 서명 키스토어를 저장소에서 제거하고 GitHub Actions repository secrets로 이전했습니다.

v0.1.2 이후 APK는 동일한 고정 인증서로 서명되어 덮어쓰기 업데이트를 지원합니다.
