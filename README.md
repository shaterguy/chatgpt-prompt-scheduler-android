# ChatGPT Prompt Scheduler for Android

ChatGPT 웹 세션에 예약 프롬프트를 자동 전송하는 독립 Android 앱입니다. Chrome 확장프로그램이나 외부 브라우저에 의존하지 않습니다.

현재 정식 버전은 `0.4.0`입니다. 일반 Chat, 프로젝트 새 대화, 기존 대화, Chat·Work 모드, 일회성·매일·매주·분 간격 반복, 기존 입력 교체, 설정 JSON 내보내기·가져오기와 실행 기록을 지원합니다.

## 0.4.0 SelfRun 요청 프로필 설정 연동

일반 Chat과 Work의 SelfRun 모델·추론 설정 JSON을 각각 가져올 수 있습니다. 가져온 프로필은 기존 등록값과 병합되며, 같은 signal의 프로필은 최신 값으로 갱신됩니다. Work 예약 편집에서는 실제로 등록된 model/reasoning 조합만 선택지로 노출합니다.

설정파일의 `SET`·`REMOVE` operation은 임의 단순화 없이 요청 프로필 엔진에 보존합니다. 내부 `inherit`·`keep` 상태는 화면에서 현재 설정 유지 의미로 표시하고 실행 시 native-inherit 계약을 유지하며, 기존 대화 대상도 기존 대화의 네이티브 프로필을 계속 상속합니다.

프로젝트 목록 전체 초기화 기능은 등록된 ProjectCatalog만 비우며 기존 예약, 로그인 세션과 실행 기록은 보존합니다. 기존 Schedule JSON `schemaVersion 1` 호환성도 유지합니다.

## 0.3.2 인증 상태 판정 보정

로그인된 ChatGPT 화면에 부수적인 `Log in`·`Sign up` 문구가 존재해도 실제 작성 가능한 composer가 보이면 인증 만료로 오판하지 않습니다. composer가 없고 명시적인 로그인 경로 또는 로그인 컨트롤이 확인될 때만 `AUTH_REQUIRED`로 판정하며, 인증 신호 없이 화면 준비만 덜 된 경우에는 `RETRY`로 대기합니다.

이 보정은 기존 대상 URL 검증과 visible composer 확인을 우회하지 않으며, 새 권한·외부 전송·쿠키 추출·비밀정보 저장을 추가하지 않습니다.

## 0.3.1 요청 프로필 적용

새 대화 예약은 AndroidX WebKit document-start 스크립트로 ChatGPT의 대화 요청이 만들어지기 전에 예약의 Chat·Work 프로필을 적용합니다. 변경 대상은 `model`, `thinking_effort`, `conversation_origin`, `service_tier` 네 제어 필드로 제한하며 메시지 등 데이터 평면이 달라지면 전송하지 않습니다.

일반 Chat은 캡처가 완료된 Instant, Medium, High, Extra High 절대 프로필을 사용합니다. Work는 Sol, Terra, Luna 모델과 지원되는 추론 강도를 절대 프로필로 적용합니다. 캡처되지 않았거나 불완전한 조합은 네이티브 값을 추정해 보내지 않고 전송 전에 실패 처리합니다.

`existing` 대상은 기존 대화의 네이티브 프로필을 그대로 상속하며 요청 프로필 인터셉터를 설치하지 않습니다.

## 프로젝트 대상 등록

프로젝트 예약은 프로젝트 주소를 직접 입력하지 않습니다.

1. 앱 홈에서 `로그인/세션`을 엽니다.
2. 로그인된 ChatGPT WebView에서 예약에 사용할 프로젝트를 직접 한 번 엽니다.
3. 앱은 현재 방문한 `https://chatgpt.com/g/g-p-...` 프로젝트만 확인하여 canonical 프로젝트 주소와 화면의 프로젝트 이름을 로컬에 등록합니다.
4. 예약 편집에서 대상 유형을 `project`로 선택한 뒤 등록된 프로젝트를 선택합니다.

앱은 프로젝트 목록을 자동 클릭하거나 세션 쿠키·토큰을 별도 추출하지 않습니다. 새로 관찰한 프로젝트 URL은 HTTPS, 정확한 `chatgpt.com` 호스트와 프로젝트 경로를 검증한 뒤 저장합니다. 기존에 저장된 프로젝트 예약은 등록 목록에 아직 없어도 편집 화면에서 현재 저장 프로젝트로 유지됩니다.

`existing` 대상의 기존 대화 URL 입력 방식은 그대로 유지합니다.

## AutoRun 중계 폐기

0.3.0부터 Protocol 3.x AutoRun 대화 중계 기능은 제거되었습니다. 중계 화면·서비스·부팅 복구·전용 알림·중계 상태 저장과 예약 실행 선점 게이트는 더 이상 사용하지 않습니다. 업그레이드 후 남아 있는 legacy 중계 SharedPreferences와 알림 채널도 앱 실행 시 제거합니다.

이 변경은 예약 프롬프트 실행 엔진인 `AutomationScript`와 `ExecutionService`를 폐기하는 것이 아닙니다. 예약된 프롬프트의 작성·전송·검증 기능은 계속 유지됩니다.
