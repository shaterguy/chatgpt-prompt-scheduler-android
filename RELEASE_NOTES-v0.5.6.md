# ChatGPT Prompt Scheduler Android v0.5.6

정식 v0.5.5 이후 확인된 DOM 편집기 입력 오판정에 대한 단순화 수정 릴리스입니다.

## 변경 사항

- 앱이 예약 프롬프트를 입력한 뒤 작성창에 글자가 하나라도 남아 있으면, 기존 프롬프트와 문자열·공백·길이 일치 여부를 추가로 확인하지 않고 전송 단계로 진행합니다.
- 내용이 있는 초안을 오판정하여 반복 초기화하거나 삭제하던 처리 경로를 제거했습니다.
- 초기 기존 초안 교체·입력 시도·전송 버튼 상태 확인·중복 전송 방지·대화 URL 검증은 유지합니다.
- DOM 입력·전송 방식은 그대로 사용합니다. DevTools 또는 Android IME를 이용한 새 전송 경로는 없습니다.

## 정식 업데이트 호환성

- 패키지: com.shaterguy.chatgptpromptscheduler
- 표시명: ChatGPT Prompt Scheduler
- 버전: 0.5.6 / versionCode 2100000011
- 기준: v0.5.5 / versionCode 2100000010
- 정식 서명 인증서 SHA-256: 3cfe95acd09077a89cd8de85434cbd5d8bb3e2021d8e9eacb804a8da9ccce52a
- 기존 앱 데이터를 유지하는 업데이트용 APK입니다.

## 검증

로컬 Android 단위 테스트 140개와 APK 빌드를 수행하고 정식 패키지·버전·서명·릴리스 자산을 검사합니다. 실기기 ChatGPT 웹 UI에서의 자동 제출은 사후 검증 대상입니다. GitHub Android 에뮬레이터는 실행하지 않습니다.
