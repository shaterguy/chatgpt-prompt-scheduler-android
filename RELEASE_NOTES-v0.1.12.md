# ChatGPT Prompt Scheduler Android v0.1.12

## Work 모델 선택

- Work 모드 예약에서 `inherit`, `Sol`, `Terra`, `Luna`를 선택할 수 있습니다.
- 실행 시 모델을 먼저 선택·검증한 뒤 추론 강도를 선택·검증합니다.
- 모델과 추론 강도가 모두 목표값과 일치한 경우에만 프롬프트를 한 번 전송합니다.
- 기존 예약은 `inherit`으로 자동 호환됩니다.

## 서명 보안 강화

- 저장소에 포함되어 있던 base64 키스토어를 제거했습니다.
- 키스토어 비밀번호를 교체하고 키스토어와 비밀번호를 GitHub Actions repository secrets로 이전했습니다.
- PR에서는 비밀정보를 사용하지 않고 단위 테스트와 unsigned Release 빌드만 수행합니다.
- main push의 release job만 서명 시크릿과 `contents: write` 권한을 사용합니다.

## 업데이트 호환성

- Application ID는 변경하지 않았습니다.
- 서명 인증서 SHA-256은 기존과 동일하게 유지합니다.
- versionCode는 13, versionName은 0.1.12입니다.
- v0.1.2 이후 설치본에서 덮어쓰기 업데이트가 가능합니다.

## 별도 조치

- 과거 Git 이력의 키스토어와 평문 비밀번호 제거는 인증된 `git-filter-repo` 환경에서 mirror force-push와 GitHub Support 캐시 제거 절차를 수행해야 합니다.
