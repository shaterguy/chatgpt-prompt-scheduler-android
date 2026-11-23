# 0.6.2-dev13 — 서버 모델 설정 확인

모델·추론 설정은 ChatGPT 서버가 마지막 사용 모델 구성 저장 요청에 성공 응답을 반환한 경우에만 준비 완료로 판정합니다. 실패 응답에서는 기존 실행 차단 규칙을 적용하며, 오류 응답 전문·상태와 반환값 종류를 기록합니다.

예약 실행, 프롬프트 전송, 중복 실행 방지, Chat·Work 프로필, 실행 기록 보존 및 내보내기 기능의 기존 동작을 유지합니다. 서버 저장 거부 응답을 재현하는 회귀 테스트를 추가했습니다.

- applicationId: com.shaterguy.chatgptpromptscheduler.dev
- versionCode: 6002013
- versionName: 0.6.2-dev13
