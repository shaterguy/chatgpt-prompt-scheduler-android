# Changelog

## 0.4.0 — 2026-08-30

- 검증된 `v0.4.0-dev1` 기능 상태를 기존 정식 앱 계보의 `0.4.0`으로 승격
- SelfRun 일반 Chat·Work 모델/추론 설정 JSON을 별도로 가져와 신규 프로필을 병합하고 동일 signal 프로필은 최신 값으로 갱신
- Work 예약은 등록된 model/reasoning 조합만 노출하고, 가져온 `SET`·`REMOVE` operation을 손실 없이 보존해 요청 프로필 엔진에 반영
- 내부 `inherit`·`keep` 상태를 사용자 화면에서 현재 설정 유지 의미로 표시하고 실행 시 native-inherit 계약을 유지
- 프로젝트 목록 전체 초기화는 ProjectCatalog 저장소만 비워 기존 예약·로그인 세션·실행 기록을 보존
- 기존 Schedule JSON `schemaVersion 1` 호환성을 유지하고 새 권한·의존성·인증·네트워크 전송 경계를 추가하지 않음
- Android API 36 WebView 6/6, 단위 테스트, release build와 trusted signer 검증을 통과한 `771aad44d6b12425fe2d457edc2b05fbeac56ee0` 기능 기준선을 사용
- 정식 Application ID와 공개 서명 계보를 유지하고 `versionCode 2100000004`로 최신 정식 `v0.3.2`에서의 업데이트 경로를 검증하도록 릴리스 구성

## 0.3.2 — 2026-08-29

- 검증된 `v0.3.2-dev1` 기능 상태를 정식 `0.3.2`로 승격
- 로그인된 ChatGPT 화면의 부수적인 `Log in`·`Sign up` 문구 때문에 `AUTH_REQUIRED`로 오판하던 문제 수정
- visible composer가 있으면 인증 완료 상태를 우선하고, composer 부재와 명시적 로그인 경로·컨트롤이 함께 확인될 때만 `AUTH_REQUIRED`로 판정
- composer와 인증 신호가 모두 없는 화면 준비 상태는 `AUTH_REQUIRED`가 아니라 `RETRY`로 처리
- Android API 36 WebView 회귀 테스트로 부수적 로그인 문구, 명시적 로그인 컨트롤, 로딩 상태를 검증
- 새 권한·의존성·외부 전송·쿠키 처리 변경 없이 기존 대상 검증과 composer 전송 게이트 유지
- 정식 Application ID와 공개 서명 계보를 유지하고 `versionCode 2100000003`으로 최신 정식 `v0.3.1`에서의 업데이트 경로를 검증

## 0.3.1 — 2026-08-29

- 검증된 `v0.3.1-dev1` 기능 상태를 정식 `0.3.1`로 승격
- Chat·Work 선택을 DOM 메뉴 클릭 대신 AndroidX WebKit document-start 요청 프로필 방식으로 전환
- 예약이 새 대화 요청을 보낼 때 `model`, `thinking_effort`, `conversation_origin`, `service_tier` 네 제어 필드만 변경하고 나머지 요청 데이터가 달라지면 전송을 차단
- 기존 대화(`existing`)는 요청 프로필 인터셉터를 설치하지 않고 네이티브 프로필을 그대로 상속
- 캡처되지 않았거나 불완전한 Chat·Work 프로필은 임의 추정하지 않고 전송 전에 fail-closed 처리
- Android System WebView 회귀 테스트와 요청 프로필 계약 테스트로 document-start 적용, 경로 allowlist, 비제어 데이터 보존을 검증
- 정식 Application ID와 공개 서명 계보를 유지하고 `versionCode 2100000002`로 최신 정식 `v0.3.0`에서의 업데이트 경로를 검증

## 0.3.0 — 2026-08-24

- 검증된 `v0.3.0-dev8` 기능 상태를 정식 `0.3.0`으로 승격
- 일반 Chat 예약에 현재 설정 유지·Instant·Medium·High·Extra High·Pro 추론 수준 선택 추가 및 기존 예약 하위 호환 유지
- 로그인/세션 WebView에서 실제 방문한 ChatGPT 프로젝트를 등록하고 프로젝트 예약 대상으로 선택하는 흐름 추가
- Chat 모드 전환을 제한된 재시도와 정확한 선택 상태 readback으로 검증한 뒤 추론 설정과 전송을 진행하도록 강화
- Android System WebView 기반 모드 전환 회귀 테스트 추가
- Protocol 3.x AutoRun 대화 중계 기능과 관련 화면·서비스·상태·알림·부팅 복구 제거, 예약 실행 엔진은 유지
- 정식 Application ID와 공개 서명 계보를 유지하고 `versionCode 2100000001`로 최신 정식 `v0.2.0`에서의 업데이트 경로를 검증

## 0.2.0 — 2026-08-12

- 애플리케이션 코드·리소스·테스트를 v0.1.15 기준으로 되돌려 새 정식 릴리스로 구성
- 사용자 표시 버전을 `0.2.0`, 내부 `versionCode`를 현재 공개 정식본과 동일한 `2100000000`으로 설정
- 동일 Application ID·동일 공개 서명·동일 versionCode를 이용한 Android APK 덮어쓰기 업데이트 호환성 검증
- v0.1.16 이후의 앱 기능 변경은 의도적으로 포함하지 않음

## 0.1.18 — 2026-08-09

- `새로 시작` 시 일반 Chat 입력창의 기존 초안을 지우고 정확한 `[AUTOMATION_START <JOB_ID>]`를 항상 새 사용자 턴으로 전송
- 최초 시작 중 같은 conversation ID를 유지하는 ChatGPT 정규화·SPA 주소 변경 허용
- 시작 사용자 턴 확인 뒤 기존 초안 보호, 중복 방지, 대상 검사 및 예약 실행 우선 로직 유지
- 첫 일반 Chat 제어 신호로 기존 Drive Step/Round를 한 번만 재설정하는 durable bootstrap 추가
- 최초 대상 SPA 경로 복구를 무제한 유지하되 적응형 백오프로 중복 로드 억제

## 0.1.14 — 2026-08-08

- 일반 Chat과 Work 대화를 엄격한 Protocol 3.0 신호로 한 턴씩 연결하는 선택형 오토런 중계 추가
- Job·Step·Round 검증, 중복·과거 신호 거부, 응답 안정화 확인 및 재시작 상태 복구
- 예약 실행 시 중계 WebView를 즉시 양보하고 예약 완료 뒤 재개하는 우선순위 게이트 추가
- 오토런 상태를 별도 SharedPreferences에 저장해 기존 예약·대기열 스키마 유지
- 기존 공개 서명 인증서와 Application ID를 유지한 v0.1.14 릴리스 자동화

## 0.1.12 — 2026-07-26

- Work 모드 예약별 모델 선택(`inherit`, `sol`, `terra`, `luna`) 추가
- 모델 선택 완료 후 추론 강도를 적용하는 DOM 상태 기반 순차 실행 및 검증

- 저장소에서 base64 키스토어 제거
- 키스토어 비밀번호 교체 및 GitHub Actions repository secrets 이전
- PR 테스트와 main 릴리스 작업 분리, 최소 권한 적용
- 기존 설치본 업데이트 호환성을 위해 서명 인증서 유지
- 서명 보안 복구 및 Git 이력 정리 절차 문서화

## 0.1.11 — 2026-07-26

- Work 모드 예약에 추론 강도(`inherit`, `light`, `medium`, `high`, `xhigh`, `max`) 추가
- 데스크톱 라디오와 모바일 모드 메뉴를 모두 처리하는 상태 기반 Chat·Work 전환
- 추론 수준 메뉴의 한국어·영어 레이블과 모바일·데스크톱 레이아웃 지원
- 메인 화면 활성화 전환 및 편집 화면 복귀 시 스크롤 위치 유지

## 0.1.1

- 실제 ChatGPT composer만 탐지하도록 선택자와 초안 판정 강화
- Android 15∼16 상태바·내비게이션바·키보드 인셋 처리
- 스마트폰 2열·태블릿 4열 반응형 작업 버튼 배치
- 모든 편집 화면의 키보드 스크롤 및 하단 버튼 접근성 개선

## 0.1.0 — 2026-07-24

- Android MVP 최초 구현
- 정확한 알람과 Foreground Service 기반 무화면 예약 실행
- ChatGPT 로그인 세션 화면 및 오프스크린 WebView 자동화
- 예약 CRUD, 복수 시각, 실행 로그, JSON 내보내기·가져오기
- 재부팅 복구, 실행 대기열, KST 타임스탬프, 초안 보호와 전송 검증
