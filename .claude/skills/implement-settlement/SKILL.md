---
name: implement-settlement
description: 연료 사용량, 전체 기름값, 균등 분할, 이동 거리 비례 분할, 반올림과 잔여 금액 처리 로직을 domain 모듈에 구현하거나 수정한다.
---

# 정산 로직 구현

1. `docs/settlement-rules.md`와 `instructions/domain.md`를 읽는다.
2. `docs/decisions.md`의 D-1~D-3과 A-1을 확인한다. 규칙을 바꾸려면 사용자에게 확정을 받고 두 문서를 함께 갱신한다.
3. 필수 테스트를 먼저 작성한다. 예상값은 코드 실행 결과가 아니라 손으로 계산한 값을 쓴다.
4. `domain`에 구현한다.
5. `./gradlew :domain:test`를 실행해 통과를 확인한다.
6. `verify-and-report` 스킬로 마무리한다.
