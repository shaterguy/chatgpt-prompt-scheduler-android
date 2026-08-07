# Android Signing Policy

## 고정 서명 기준

- Application ID: `com.shaterguy.chatgptpromptscheduler`
- Keystore source: GitHub Actions repository secret `SIGNING_KEYSTORE_BASE64`
- Key alias: `chatgpt-prompt-scheduler`
- Certificate SHA-256: `172dc1f21f1d1d4aaf8b22ff84a4084dbfd531f5ecfacae8fcf374b1beb4466e`
- Password sources: `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`

## 필수 규칙

1. v0.1.2 이후 설치본과의 업데이트 호환성을 위해 동일한 서명 인증서와 개인키를 유지한다.
2. 키스토어 원본과 비밀번호는 저장소 파일·Git 이력·릴리스 자산에 넣지 않는다.
3. 키스토어 컨테이너의 파일 SHA는 고정 신원으로 사용하지 않는다. 비밀번호 회전·재포장으로 파일 바이트가 바뀌어도 동일한 개인키와 인증서라면 허용한다.
4. 각 후속 릴리스는 직전보다 큰 `versionCode`를 사용한다.
5. 배포 전 `apksigner verify --verbose --print-certs`로 Certificate SHA-256이 위 기준값과 일치하는지 확인한다.
6. APK를 서명한 후에는 APK 내용을 변경하지 않는다.
7. PR에서는 비밀정보를 사용하지 않고 테스트와 unsigned Release 빌드만 수행한다.
8. main push 또는 명시적 수동 release 실행에서만 GitHub Actions repository secrets를 사용한다.

## 키 회전 판단

현재 배포 방식은 GitHub Release APK 직접 설치이며 minSdk 26이다. Android 8.1 이하에서는 인증서 변경을 직접 지원하지 않으므로, 기존 설치본의 덮어쓰기 업데이트를 유지하려면 인증서와 개인키를 유지해야 한다. 저장 위치나 키스토어 비밀번호는 필요할 때 재회전할 수 있으며, 이때에도 최종 APK의 Certificate SHA-256을 기준으로 동일성을 검증한다. 신규 인증서 전환은 Android 9 이상 proof-of-rotation과 Android 8용 이전 인증서 병행 서명을 함께 검증한 별도 마이그레이션 릴리스에서만 수행한다.

## 기준선 전환

v0.1.0과 v0.1.1은 GitHub Actions 임시 debug 키로 서명되어 고정 키 APK와 직접 업데이트 호환되지 않는다. v0.1.2 이후는 위 인증서를 유지한다. v0.1.12부터 키스토어 파일은 저장소에서 제거하고 GitHub Actions repository secrets에서만 복원한다.
