---
name: verify-and-report
description: 구현을 마무리할 때 빌드와 단위 테스트를 실행하고, 문서를 갱신한 뒤 PR을 열고 결과를 보고한다. 작업 완료, PR 생성, 단계 마무리, 최종 보고에 사용한다.
---

# 검증과 보고

## 검증

1. `./gradlew assembleDebug test`를 실행한다. 실패하면 원인을 고치고 다시 실행한다.
2. Gradle 파일이나 모듈 의존성을 바꿨으면 `./gradlew assembleRelease assembleDebugAndroidTest`도 실행한다.
3. 변경 내용에 하드코딩된 인증키가 없는지, `local.properties`가 커밋되지 않는지 확인한다.
4. `instructions/architecture.md`의 모듈 의존 방향을 지켰는지 확인한다.

## 문서 갱신

- `docs/roadmap.md`의 단계 상태와 현재 코드 상태
- `docs/decisions.md`의 결정 내용
- 해당되면 `docs/kakao-mobility.md`의 연동 상태

## PR

최신 `origin/develop`에서 분기한 기능 브랜치를 푸시하고 `gh pr create --base develop`로 PR을 연다. merge는 하지 않는다.

## 단계 보고

실행한 검증과 결과, 실행하지 못한 검증, PR 링크, 사용자가 해야 할 일을 적는다.

## 최종 보고

프로젝트 전체를 마칠 때는 다음 항목을 모두 보고한다.

- 프로젝트 구조
- 구현한 화면과 기능
- 선택한 아키텍처와 기술 스택
- 카카오모빌리티 SDK/API 검증 결과
- 실제 거리 조회 연동 성공 여부
- 빌드와 테스트 결과
- 개발자 콘솔에서 직접 설정해야 하는 인증 정보
- 미구현 기능과 후속 작업
