# ChatGPT Prompt Scheduler Android v0.3.2

`v0.3.2-dev1`의 검증된 기능 상태를 기존 정식 앱 계보로 승격한 릴리스입니다.

## 주요 변경

- 로그인된 ChatGPT 화면에 부수적인 `Log in`·`Sign up` 문구가 있어도 visible composer가 있으면 인증 만료로 오판하지 않습니다.
- composer가 없고 `/auth/login`, `signup`, `signin` 계열 경로 또는 명시적인 visible auth control이 확인될 때만 `AUTH_REQUIRED`를 반환합니다.
- composer와 명시적 인증 신호가 모두 없는 준비 중 화면은 `RETRY`로 처리합니다.
- 기존 대상 URL 검증과 visible composer 전송 게이트를 유지하며 인증 우회 경로를 추가하지 않습니다.
- 새 권한·의존성·외부 전송·비밀정보 저장·쿠키 처리 변경은 없습니다.
- Android API 36 WebView 회귀 테스트로 부수적 로그인 문구, 명시적 로그인 컨트롤, 로딩 상태를 검증했습니다.

## 정식 설치 계보

- Application ID: `com.shaterguy.chatgptpromptscheduler`
- versionName: `0.3.2`
- versionCode: `2100000003`
- 공개 서명 인증서 SHA-256: `3cfe95acd09077a89cd8de85434cbd5d8bb3e2021d8e9eacb804a8da9ccce52a`
- 업데이트 기준선: 최신 정식 `v0.3.1` (`versionCode 2100000002`)

정식 릴리스 workflow는 기존 `v0.3.1` APK의 Application ID와 공개 서명 인증서를 확인하고, `2100000002 < 2100000003`을 검증한 뒤 서명·게시합니다.

## 검증 기준선

승격 기능 기준선은 `v0.3.2-dev1` 커밋 `1b8a404005f94df648df1e54b8854fa2e7933bf1`입니다. 해당 커밋의 DEV CI `33249608926`에서 Android API 36 WebView 회귀 테스트, 단위 테스트와 release APK 빌드가 통과했고, main-controlled trusted signer 실행 `33249803603`에서 source provenance, manifest allowlist, 고정 DEV 인증서, APK v2/v3 서명, zipalign과 체크섬 검증이 통과했습니다.

정식 승격 과정에서는 기능 소스를 변경하지 않고 정식 패키지 identity, 최신 정식 업데이트 기준선, 릴리스 메타데이터와 이에 직접 결합된 identity 계약 테스트만 정합화합니다.
