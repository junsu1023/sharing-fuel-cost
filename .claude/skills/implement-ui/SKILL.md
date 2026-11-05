---
name: implement-ui
description: 기름값 정산 앱의 화면, UI 상태, ViewModel, 테마를 Compose로 구현하거나 수정한다. 입력 화면, 결과 화면, 거리 조회 상태, 복사와 공유 UI 작업에 사용한다.
---

# 화면 구현

1. `docs/ui-ux.md`와 `docs/roadmap.md`를 읽는다.
2. `docs/ui-ux.md`의 디자인 방향이 미정이면 Open Design으로 시안을 만들어 사용자에게 선택받고, 색상 토큰과 분위기를 기록한 뒤 진행한다. Open Design을 쓸 수 없으면 임의로 색상을 정하지 말고 사용자에게 알린다.
3. `instructions/presentation.md`와 `instructions/architecture.md`를 따른다.
4. `presentation`에 UiState, Intent, Effect를 먼저 정의하고, Screen, ViewModel 순서로 구현한다.
5. 로직이 아직 없으면 `domain`의 인터페이스에 맞춘 가짜 구현이나 Preview 데이터를 쓰고, 실제로 연결되지 않은 부분을 코드와 보고에 명확히 표시한다.
6. `docs/ui-ux.md`의 입력 항목, 거리 조회 상태, 결과 항목이 모두 구현되었는지 대조한다.
7. ViewModel 테스트를 작성한다.
8. `verify-and-report` 스킬로 마무리한다. 가능하면 `/run`으로 에뮬레이터에서 화면을 확인하고, 확인하지 못했으면 그대로 보고한다.
