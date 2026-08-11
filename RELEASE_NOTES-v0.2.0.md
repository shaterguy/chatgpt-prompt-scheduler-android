# v0.2.0

- 애플리케이션 코드·리소스·테스트를 `v0.1.15` 기준으로 되돌려 정식 릴리스합니다.
- 사용자 표시 버전은 `0.2.0`, 내부 `versionCode`는 현재 공개 정식본과 동일한 `2100000000`입니다.
- Android 플랫폼의 APK 업데이트 조건에 따라 동일 Application ID·동일 서명 인증서·동일하거나 높은 versionCode를 사용하므로 `v2100000000` 설치본 위에 덮어쓰기 업데이트할 수 있도록 구성합니다.
- Application ID는 `com.shaterguy.chatgptpromptscheduler`로 유지합니다.
- 기존 공개판 고정 서명 인증서를 그대로 사용합니다.
- 앱 기능과 사용자 동작은 `v0.1.15` 기준이며, `v0.1.16` 이후에 추가된 앱 코드 변경은 포함하지 않습니다.
- 릴리스 워크플로는 `v2100000000` APK와 새 APK의 패키지명·서명·versionCode 동일성을 검증합니다.

이 저장소의 배포 경로는 GitHub Release APK이며, Google Play의 새 업로드용 versionCode 재사용 제한은 적용 대상이 아닙니다.
