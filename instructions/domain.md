---
paths:
  - "domain/**"
---

# Domain 규칙

계산과 정산 규칙은 `docs/settlement-rules.md`를 따른다.

- 순수 Kotlin(JVM) 모듈이다. Android, Retrofit, OkHttp, Compose 타입을 쓰지 않는다.
- 외부 의존성은 `javax.inject`와 `kotlinx-coroutines-core`만 허용한다. 다른 의존성이 필요하면 사용자에게 먼저 묻는다.
- 거리 조회는 이 모듈의 인터페이스로 정의하고, 구현은 `data`에 둔다. 실패는 호출자가 수동 입력으로 전환할 수 있도록 결과 타입으로 표현한다.
- 모델은 불변으로 만들고, 거리와 금액의 단위가 이름이나 타입에서 드러나게 한다. (예: `distanceKm`, `amountWon`)
- 규칙을 추가하거나 바꾸면 같은 작업에서 단위 테스트도 추가하거나 고친다.
