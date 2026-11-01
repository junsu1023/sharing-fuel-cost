---
paths:
  - "app/**/*.kt"
  - "presentation/**/*.kt"
  - "data/**/*.kt"
  - "domain/**/*.kt"
  - "**/*.gradle.kts"
  - "gradle/libs.versions.toml"
---

# 아키텍처 규칙

## 모듈 의존 방향

```
app          → presentation, data, domain
presentation → domain
data         → domain
domain       → 다른 모듈 참조 금지
```

- `presentation`은 `data`를 참조하지 않는다. `data`를 아는 쪽은 DI를 구성하는 `app`뿐이다.
- `core`, `feature:*` 모듈을 임의로 추가하지 않는다. 필요하면 먼저 사용자에게 제안한다.

## 패키지

| 모듈 | 패키지 |
|---|---|
| `app` | `com.jskim.sharingfuelcost` |
| `presentation` | `com.jskim.sharingfuelcost.presentation` |
| `data` | `com.jskim.sharingfuelcost.data` |
| `domain` | `com.jskim.sharingfuelcost.domain` |

## 빌드 설정

- SDK, Java 버전 등 공통 설정은 `build-logic`의 convention plugin에서 관리한다. 모듈의 빌드 파일에 반복하지 않는다.
- 의존성과 버전은 `gradle/libs.versions.toml`에 등록하고 catalog 별칭으로 참조한다.
- AGP 9의 내장 Kotlin을 쓰므로 Android 모듈에 `kotlin-android` 플러그인을 적용하지 않는다.
- 새 라이브러리는 공식 릴리스 정보로 버전을 확인한 뒤, 현재 AGP, Kotlin, compileSdk와 함께 빌드해 검증한다.
- Hilt를 쓰더라도 `domain`에는 `javax.inject`만 추가한다.

## 범위

개인 개발자가 유지보수할 규모를 유지한다. 구현이 하나뿐인 추상화나 쓰이지 않는 확장 지점을 만들지 않는다. 거리 조회 인터페이스는 교체 가능해야 한다는 요구사항이 있으므로 예외다.
