# 0.6.2-dev11 — 모델·추론 설정 응답 진단 보완

NATIVE_PREFERENCE_FAILED에서 safePatch의 반환값이 success: true가 아니면 실제 응답 종류·필드명·상태코드를 실행 기록에 남깁니다. 반환값과 예외 응답은 길이 제한 없이 8,192자 단위로 전송하여 개별 실행의 응답 파일에 보관하며, 내보낸 로그에는 비밀값을 제거한 응답 전문을 포함합니다. HTTP 200이지만 의미상 실패한 응답도 기록합니다.

Chat·Work 설정 성공 판정과 프롬프트 전송·중복 방지·재시도 계약은 유지했습니다. 정상·실패 응답, 중첩된 성공값 오판 방지, 100KB 초과 진단문과 브릿지 부재를 테스트합니다.

DEV applicationId: com.shaterguy.chatgptpromptscheduler.dev
versionCode: 6002011
versionName: 0.6.2-dev11
