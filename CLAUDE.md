# SharingFuelCost

친구들과 자동차로 이동할 때 드는 기름값을 계산하고 탑승자별 부담금을 정산하는 Android 앱이다.
Kotlin, Jetpack Compose, 멀티모듈 클린 아키텍처로 구현한다.

## 작업 시작 전 확인

1. `docs/roadmap.md`에서 현재 단계와 다음 작업을 확인한다.
2. 아래 표에서 이번 작업에 해당하는 문서를 읽고, 그 내용에 맞춰 구현한다.
3. `docs/decisions.md`의 결정과 `확인 필요` 해석을 따른다. 기록되지 않은 요구사항 결정은 임의로 정하지 말고 사용자에게 묻는다.

| 작업 | 읽을 문서 | 스킬 |
|---|---|---|
| 기능 범위, 요구사항 확인 | `docs/brief.md` | |
| 화면, UI 상태 구현 | `docs/ui-ux.md` | `implement-ui` |
| 기름값 계산, 정산 로직 | `docs/settlement-rules.md` | `implement-settlement` |
| 카카오모빌리티 검증, 거리 조회 연동 | `docs/kakao-mobility.md` | `integrate-kakao-route` |
| 평균 유가, 오피넷 연동 | `docs/opinet.md` | |
| 작업 마무리, 빌드와 테스트, 보고 | `docs/roadmap.md` | `verify-and-report` |

`instructions/`의 규칙은 `.claude/rules/instructions` 링크를 통해 해당 경로의 파일을 다룰 때 자동으로 적용된다.

| 규칙 파일 | 적용 경로 |
|---|---|
| `instructions/architecture.md` | 모든 모듈의 Kotlin 소스, Gradle 빌드 파일 |
| `instructions/presentation.md` | `presentation/` |
| `instructions/domain.md` | `domain/` |
| `instructions/data.md` | `data/` |
| `instructions/testing.md` | 테스트 소스 |

## 절대 규칙

- 프로젝트는 이미 생성되어 있다. 다시 생성하거나, 사용자 동의 없이 기존 파일을 삭제하거나 덮어쓰지 않는다.
- 외부 API 인증키를 소스 코드에 하드코딩하거나 커밋하지 않는다.
- 실제로 호출해 성공을 확인하지 않은 외부 API는 연동 완료라고 보고하지 않는다.
- 공식 문서에서 확인되지 않은 SDK 기능이나 메서드를 추측해 구현하지 않는다.
- 라이브러리 버전을 최신이라고 가정하지 않는다. 공식 릴리스 정보로 확인하고, 기존 버전과의 호환성을 빌드로 검증한다.
- MVP 범위 밖의 기능을 임의로 추가하지 않는다. 자체 내비게이션과 실시간 GPS 추적은 만들지 않는다. 현재 위치는 출발지를 정할 때 한 번만 가져온다.

## 명령어

```bash
./gradlew assembleDebug   # 디버그 빌드
./gradlew test            # 전체 단위 테스트
./gradlew :domain:test    # domain 모듈 단위 테스트
```

`local.properties`가 없는 worktree에서는 `ANDROID_HOME=$HOME/Library/Android/sdk`를 앞에 붙여 실행한다.

## Git

- 작업마다 최신 `origin/develop`에서 기능 브랜치를 만들고, `develop` 대상 PR까지만 진행한다.
- merge는 사용자가 직접 한다. `main` 대상 PR, `main` 직접 푸시, force-push는 하지 않는다.
- 커밋 메시지는 `feat: 설명`처럼 타입을 앞에 붙이고 한국어로 쓴다.

## 문서 관리

- 단계가 끝나면 `docs/roadmap.md`의 상태를 갱신한다.
- 사용자가 결정한 내용은 `docs/decisions.md`에 기록하고, 관련 문서에도 반영한다.
- `docs/brief.md`는 사용자의 원문이므로 수정하지 않는다. 변경된 내용은 `docs/decisions.md`에 기록한다.
