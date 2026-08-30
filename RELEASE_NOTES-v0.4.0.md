# ChatGPT Prompt Scheduler Android v0.4.0

검증 완료된 `v0.4.0-dev1` 기능 상태를 기존 정식 앱 설치·서명 계보로 승격한 릴리스입니다. DEV 앱 자체를 정식 앱으로 전환하지 않고 동일한 검증 기능 소스를 production identity로 다시 패키징합니다.

## 주요 변경

- SelfRun 일반 Chat·Work 모델/추론 설정 JSON을 각각 가져와 등록 프로필에 병합하고 동일 signal 프로필은 최신 값으로 갱신합니다.
- Work 예약은 실제 등록된 model/reasoning 조합만 노출하며, 설정파일에서 가져온 `SET`·`REMOVE` operation을 손실 없이 보존합니다.
- 내부 `inherit`·`keep` 상태는 사용자 화면에서 현재 설정 유지 의미로 표시하고 실행 시 native-inherit 계약을 유지합니다.
- 프로젝트 목록 전체 초기화는 ProjectCatalog 저장소만 비우며 기존 예약, 로그인 세션과 실행 기록은 보존합니다.
- 기존 Schedule JSON `schemaVersion 1` 호환성을 유지합니다.
- 새 권한·의존성·인증·네트워크 대상·외부 전송·비밀정보 처리 변경은 없습니다.

## 정식 설치 계보

- Application ID: `com.shaterguy.chatgptpromptscheduler`
- versionName: `0.4.0`
- versionCode: `2100000004`
- 공개 서명 인증서 SHA-256: `3cfe95acd09077a89cd8de85434cbd5d8bb3e2021d8e9eacb804a8da9ccce52a`
- 업데이트 기준선: 최신 정식 `v0.3.2` (`versionCode 2100000003`)

정식 릴리스 workflow는 `v0.3.2` APK의 Application ID와 공개 서명 인증서를 확인하고 `2100000003 < 2100000004`를 검증한 뒤 정식 APK를 서명·게시합니다.

## 검증 기준선

승격 기능 기준선은 `v0.4.0-dev1` 커밋 `771aad44d6b12425fe2d457edc2b05fbeac56ee0`입니다. DEV source run `33265506764`에서 Android API 36 WebView instrumentation 6/6, 단위 테스트와 release build가 통과했고, trusted signer run `33265704173`에서 source provenance, artifact digest, manifest/permission allowlist, 고정 인증서, APK v2/v3 서명, package/version/label과 zipalign 검증이 통과했습니다.

정식 승격 과정에서는 기능 소스나 검증 로직을 새로 변경하지 않고 정식 package identity, 최신 정식 업데이트 기준선, 릴리스 자산명·문서와 이에 직접 결합된 identity 계약만 정합화합니다.
