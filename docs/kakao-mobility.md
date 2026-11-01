# 카카오모빌리티 거리 조회 검증

**현재 상태: 거리 조회와 주소 검색 연동 완료, 지도와 현재 위치 미구현.** 검증 항목은 2026-11-01에 공식 문서로 확인했고, 실제 호출 결과는 연동 상태에 적었다.
절차는 `integrate-kakao-route` 스킬을 따른다.

## 결론

- 거리 조회는 **길찾기 REST API의 자동차 길찾기**를 쓴다. Kakao SDK의 내비 모듈은 카카오내비 앱을 실행할 뿐이다.
- 경로 API는 **좌표만 받는다.** 주소와 지도 선택 결과를 좌표로 저장해 넘겨야 한다. (A-4 해당)
- 탑승자별 이동 거리는 응답의 구간 거리 `sections[].distance`로 계산한다.

## 검증 항목

확인일은 모두 2026-11-01이다.

| 질문 | 결과 | 출처 |
|---|---|---|
| Kakao SDK 내비 모듈만으로 주행 거리를 조회할 수 있는가? | 없음. 길 안내(`navigateIntent`)와 목적지 공유(`shareDestinationIntent`)로 앱을 실행하며, 거리 반환은 문서에 없다 | [Android](https://developers.kakao.com/docs/ko/kakaonavi/android) |
| 내장형 길찾기 SDK로 조회할 수 있는가? | 시작 문서와 제품 소개에서 주행 없이 거리를 얻는 기능을 찾지 못했다. API 레퍼런스 전체는 확인하지 않았다. 길 안내 제품이라 쓰지 않는다 | [시작하기](https://developers.kakaomobility.com/guide/android/start.html), [제품](https://developers.kakaomobility.com/product/navisdk.html) |
| 별도의 Directions REST API가 필요한가? | 필요하다. `GET https://apis-navi.kakaomobility.com/v1/directions` | [자동차 길찾기](https://developers.kakaomobility.com/guide/navi-api/directions.html) |
| 인증 방식은? | 길찾기, 로컬 REST API: `Authorization: KakaoAK ${REST_API_KEY}`. 지도 SDK: 네이티브 앱 키와 키 해시 | [자동차 길찾기](https://developers.kakaomobility.com/guide/navi-api/directions.html), [로컬](https://developers.kakao.com/docs/ko/local/dev-guide), [지도 시작](https://apis.map.kakao.com/android_v2/docs/getting-started/quickstart/) |
| 제휴 승인이 필요한가? | 길찾기 API 문서에는 앱 등록과 REST API 키 발급만 있다. 제휴는 쿼터 상향과 대량 이용에서 언급된다. 오류 코드 `-5`(권한 없음, 검수 필요)가 있어 실제 호출로 확인해야 한다 | [시작하기](https://developers.kakaomobility.com/guide/navi-api/start.html), [이해하기](https://developers.kakao.com/docs/ko/kakaonavi/common), [오류](https://developers.kakaomobility.com/guide/navi-api/solution.html) |
| 상업적 이용 조건은? | 확인 불가. 요금 페이지와 약관에 명시 조항이 없고, 약관은 세부 사항을 운영정책에 위임한다. 운영정책은 읽지 않았다. 공개 출시 전 확인이 필요하다 | [요금](https://developers.kakaomobility.com/price/), [약관](https://developers.kakao.com/terms/ko/site-terms) |
| 주행 거리 필드와 단위는? | 전체: `routes[].summary.distance`(Int, 미터). 구간: `routes[].sections[].distance`(Int, 미터). 성공은 `result_code` 0 | [자동차 길찾기](https://developers.kakaomobility.com/guide/navi-api/directions.html), [레퍼런스](https://developers.kakaomobility.com/guide/navi-api/reference.html) |
| 경유지 지원과 제한은? | 자동차 길찾기 최대 5개, `\|`로 구분. 다중 경유지 API(`POST /v1/waypoints/directions`)는 최대 30개. 둘 다 총 거리 1,500km 미만 | [자동차 길찾기](https://developers.kakaomobility.com/guide/navi-api/directions.html), [다중 경유지](https://developers.kakaomobility.com/guide/navi-api/waypoints.html) |
| 구간별 거리를 제공하는가? | 제공한다. 다중 경유지 문서에 구간 수는 경유지 수 + 1로 명시되어 있다. 자동차 길찾기는 같은 필드를 제공하지만 구간 수 명시는 읽지 못해 실제 호출로 확인한다. `summary=true`이면 요약만 반환하므로 쓰지 않는다 | [다중 경유지](https://developers.kakaomobility.com/guide/navi-api/waypoints.html) |
| 요청에 좌표가 필요한가? | 필요하다. `origin`, `destination`은 `X,Y` 좌표 형식이고 주소 문자열은 문서에 없다 | [자동차 길찾기](https://developers.kakaomobility.com/guide/navi-api/directions.html) |
| 주소 검색과 좌표 변환 API는? | 로컬 API: `/v2/local/search/address`, `/v2/local/search/keyword`, `/v2/local/geo/coord2address`. x는 경도, y는 위도 | [로컬](https://developers.kakao.com/docs/ko/local/dev-guide) |
| 지도 SDK와 호환되는가? | 카카오맵 Android SDK v2 2.15.2, API 23 이상이라 minSdk 28과 맞는다. ABI는 `armeabi-v7a`, `arm64-v8a`. compileSdk 37, Compose 호환은 문서에 없어 빌드로 확인한다. Compose 전용 API는 문서에서 찾지 못해 `AndroidView`로 감싸야 한다. 지도 중심 좌표와 탭 좌표 API는 시작 문서에 없어 구현 전에 추가 확인한다 | [시작](https://apis.map.kakao.com/android_v2/docs/getting-started/), [빠른 시작](https://apis.map.kakao.com/android_v2/docs/getting-started/quickstart/) |
| 인증키를 앱에 포함해도 되는가? | 금지하는 공식 안내를 찾지 못했지만, 안전하다는 근거도 없다. 약관 제11조 3항은 비밀 키 등을 승낙 없이 제3자에게 제공하지 못하게 한다. 호출 허용 IP는 모바일 앱에 쓸 수 없다. REST API 키는 APK에서 추출될 수 있다. 전용 키 문서는 찾지 못했다 | [약관](https://developers.kakao.com/terms/ko/site-terms), [시작하기](https://developers.kakaomobility.com/guide/navi-api/start.html) |
| 무료 사용량은? | 일간: 자동차 길찾기 10,000건, 다중 경유지 5,000건, 주소 변환과 키워드 검색 각 100,000건, 지도 SDK 300,000건. 카카오맵 무료 쿼터는 계정에서 처음 활성화한 앱에만 제공 | [쿼터](https://developers.kakao.com/docs/ko/getting-started/quota), [요금](https://developers.kakaomobility.com/price/), [카카오맵](https://developers.kakao.com/docs/ko/kakaomap/common) |
| 초과 시 동작은? | HTTP 429. 초과 요금은 자동차 길찾기 건당 8원. 지도, 로컬은 비즈월렛과 유료 설정이 있어야 초과 호출할 수 있다. 내비 API의 초과 과금 전환 절차는 문서에서 확인하지 못했다 | [오류](https://developers.kakaomobility.com/guide/navi-api/solution.html), [요금](https://developers.kakaomobility.com/price/), [카카오맵](https://developers.kakao.com/docs/ko/kakaomap/common) |

## 구현 기준

- 호출: `GET /v1/directions?origin=x,y&destination=x,y&waypoints=x,y|x,y`. 탑승자는 최대 5명으로 제한한다.
- 전체 주행 거리: `summary.distance`
- 탑승자 이동 거리: 그 탑승자의 경유지 다음 구간부터 마지막 구간까지 `sections[].distance`의 합. `sections` 수가 경유지 수 + 1이 아니면 실패로 처리해 직접 입력으로 넘긴다.
- 실패 구분: `result_code`가 0이 아님(경로 없음, 예: 104는 출발지와 도착지 5m 이내), HTTP 401/403(인증, 권한), 429(쿼터), 네트워크 오류. 모두 직접 입력으로 대체할 수 있어야 한다.
- 현재 위치는 카카오가 아니라 Android 위치 API로 얻고, `coord2address`로 주소를 표시한다.

## 연동 상태

| 항목 | 상태 |
|---|---|
| 수동 입력 구현 | 완료 (PR #4) |
| 거리 조회 인터페이스 | 완료 |
| 실제 거리 조회 API 구현 | 완료. 2026-11-01 에뮬레이터 앱에서 실제 호출 성공 |
| 주소 검색 구현 | 완료. 2026-11-01 에뮬레이터 앱에서 실제 호출 성공 |
| 지도 위치 선택 구현 | 미구현. 네이티브 앱 키와 키 해시 설정 필요 |
| 현재 위치 조회 구현 | 미구현 |

2026-11-01 실제 호출 결과: 경유지 2개에 `sections` 3개, 구간 합과 `summary.distance` 일치(256,734m), 별도 제휴 승인 없이 HTTP 200. 자동차 길찾기의 구간 수는 이 호출로 확인했다.

인증키는 `local.properties`의 `kakao_rest_api_key`에서 읽어 `BuildConfig`로 주입한다.

## 사용자가 콘솔에서 설정할 항목

[카카오디벨로퍼스](https://developers.kakao.com/)에서 설정한다. 키 값은 이 문서나 대화에 적지 않고 `local.properties`에만 넣는다.

1. 앱을 생성한다. 무료 쿼터를 받으려면 계정에서 처음으로 카카오맵을 활성화하는 앱이어야 한다.
2. 앱의 [제품 설정] > [카카오맵]에서 사용 설정을 켠다.
3. REST API 키를 발급한다. (길찾기, 로컬)
4. Android 플랫폼에 패키지명 `com.jskim.sharingfuelcost`와 키 해시를 등록하고 네이티브 앱 키를 확인한다. (지도)
5. 카카오내비 또는 길찾기 API에 별도 활성화 항목이 보이면 켠다. 문서로 확인하지 못해 실제 호출의 `-3`, `-5` 오류로 확인한다.
