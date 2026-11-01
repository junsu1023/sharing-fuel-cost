---
name: implement-settlement
description: 연료 사용량, 전체 기름값, 균등 분할, 이동 거리 비례 분할, 반올림과 잔여 금액 처리 로직을 domain 모듈에 구현하거나 수정한다.
---

# 정산 로직 구현

1. `docs/settlement-rules.md`와 `instructions/domain.md`를 읽는다.
2. `docs/decisions.md`의 D-1, D-2, D-3이 미결정이면 제안을 보여 주고 사용자에게 확정을 받는다. 임의로 정하지 않는다.
3. 확정된 규칙과 예시를 `docs/settlement-rules.md`에 기록하고, `docs/decisions.md`를 갱신한다.
4. 필수 테스트를 먼저 작성한다. 예상값은 코드 실행 결과가 아니라 손으로 계산한 값을 쓴다.
5. `domain`에 구현한다.
6. `./gradlew :domain:test`를 실행해 통과를 확인한다.
7. `verify-and-report` 스킬로 마무리한다.
