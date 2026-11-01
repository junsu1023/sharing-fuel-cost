# 로드맵

`docs/brief.md`의 작업 순서를 현재 상태에 맞게 옮긴 표다. 사용자 요청에 따라 UI/UX를 먼저 진행한다.
단계가 끝나면 상태를 갱신한다.

| # | 단계 | 상태 | 비고 |
|---|---|---|---|
| 1 | 프로젝트 생성 | 완료 | 기존 프로젝트 사용 |
| 2 | 멀티모듈 구조, `build-logic` 구성 | 완료 | PR #1 |
| 3 | 기술 스택 추가 (Hilt, Retrofit, OkHttp, Coroutines) | 대기 | 필요한 단계에서 추가, 버전은 공식 릴리스로 확인 |
| 4 | 핵심 화면과 UI 상태 구현 | **다음 작업** | `docs/ui-ux.md` |
| 5 | 기름값 계산, 정산 도메인 로직 | 대기 | `docs/settlement-rules.md`, D-1~D-3 결정 필요 |
| 6 | 카카오모빌리티 SDK/API 적합성 검증 | 대기 | `docs/kakao-mobility.md` |
| 7 | 거리 조회와 수동 입력 대체 구현 | 대기 | 6번 결과에 따라 범위 결정 |
| 8 | 정산 결과와 거리 조회 연결 | 대기 | |
| 9 | 단위 테스트 작성, 실행 | 대기 | 각 단계에서 함께 작성하고 여기서 전체 점검 |
| 10 | 빌드, 오류 수정 | 대기 | |
| 11 | 최종 보고 | 대기 | `verify-and-report` 스킬의 항목 사용 |

## 현재 코드 상태

- 모듈: `app`, `presentation`, `data`, `domain`, `build-logic`
- `data`, `domain`에는 아직 소스가 없다.
- `MainActivity`는 템플릿 `Greeting` 화면을 표시한다.
- Hilt, Retrofit, OkHttp는 아직 추가되지 않았다.
