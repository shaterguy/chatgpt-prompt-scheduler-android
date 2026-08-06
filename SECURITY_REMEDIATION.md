# Signing Security Remediation

## 적용 내용

- 저장소에서 `.android-signing/chatgpt-prompt-scheduler-upload.jks.b64` 제거
- `.android-signing/`, `*.jks`, `*.keystore`, `signing-secrets*` 커밋 차단
- 키스토어 비밀번호 교체 및 동일 인증서 유지
- 키스토어를 `SIGNING_KEYSTORE_BASE64` repository secret으로 이전
- PR에서는 시크릿 없이 테스트와 unsigned APK 빌드만 수행
- release job의 권한을 `contents: write`로 한정하고 나머지는 `contents: read`로 제한

## 병합 전 필수 조건

1. repository secret `SIGNING_KEYSTORE_BASE64` 설정
2. repository secret `SIGNING_STORE_PASSWORD` 설정
3. repository secret `SIGNING_KEY_PASSWORD` 설정
4. 세 시크릿 설정 후 수동 workflow 실행으로 서명 인증서 SHA-256과 APK 업데이트 설치 검증
5. 검증 전에는 이 브랜치를 main에 병합하지 않음

## Git 이력 정리

현재 연결 도구는 GitHub Actions secrets 설정과 `git-filter-repo` mirror force-push를 지원하지 않는다. 시크릿 설정 후 별도 인증된 Git 환경에서 다음 범위를 정리해야 한다.

- `.android-signing/chatgpt-prompt-scheduler-upload.jks.b64`의 전체 이력 제거
- 과거 workflow 평문 비밀번호 문자열의 전체 이력 치환
- 모든 브랜치와 태그 강제 갱신
- 영향받는 PR ref와 GitHub 캐시 제거를 위한 GitHub Support 요청
- 기존 로컬 clone 폐기 후 재clone

이력 재작성 후에는 기존 커밋 SHA와 태그 SHA가 변경되므로 Drive 운영문서와 GitHub Release를 새 SHA 기준으로 다시 동기화한다.
