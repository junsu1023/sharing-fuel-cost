---
paths:
  - "presentation/**"
---

# Presentation 규칙

화면 명세는 `docs/ui-ux.md`를 따른다.

## 상태 관리 (MVVM + MVI)

화면마다 다음을 둔다.

- `XxxUiState`: 화면이 그리는 불변 상태
- `XxxIntent`: 사용자 동작
- `XxxEffect`: 일회성 이벤트 (복사 완료 알림, 화면 이동, 공유 시트). 필요한 화면에만 둔다.
- `XxxViewModel`: `onIntent(intent)` 하나로 입력을 받고, `StateFlow<XxxUiState>`로 상태를 내보낸다.

## ViewModel

- 네트워크, 저장소 구현을 직접 호출하지 않는다. `domain`의 use case나 인터페이스만 사용한다.
- 계산, 정산 규칙을 ViewModel이나 Composable에 구현하지 않는다.
- 입력 문자열은 UI 상태로 유지하고, 숫자로 변환한 값을 domain에 넘긴다.

## Composable

- Route Composable이 ViewModel을 연결하고, Screen Composable은 상태와 콜백만 받는다.
- Screen Composable마다 `@Preview`를 둔다. 라이트, 다크, 오류 상태처럼 의미 있는 상태를 포함한다.
- 색상과 글꼴은 `MaterialTheme`에서 가져오고, 화면 문구는 `strings.xml`에 둔다.
- 도메인 모델을 표시 문자열로 바꾸는 포맷팅은 `presentation`에서 한다.
