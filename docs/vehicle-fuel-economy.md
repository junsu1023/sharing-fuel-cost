# 차종별 표시연비

**현재 상태: 구현 완료, 실제 호출 미확인.** 공공데이터포털 인증키가 등록되면 실제 호출로 아래 미확인 항목을 확인한다.

## 공식 문서 확인 내용 (2026-11-01)

[한국에너지공단_자동차 표시연비 목록 조회 서비스](https://www.data.go.kr/data/15139827/openapi.do)

- 요청: `GET https://apis.data.go.kr/B553530/CAREFF/CAREFF_LIST`
- 필수: `serviceKey`, `pageNo`, `numOfRows`. 선택: `apiType`, `q1`(업체명), `q2`(모델명), `q3`(등급), `q4`(출시년도)
- 응답 `body.items.item`: `DISPLAY_EFF`(표시연비), `URBAN_EFF`(도심), `HIGHWAY_EFF`(고속도로), `COMP_NM`, `MODEL_NM`, `YEAR`, `FUEL_NM`, `GRADE`, `CAR_KIND`, `ENGINE_DISPLACEMENT`, `GEAR_TYPE`, `RANGE_PER_CHARGE` 등
- 결과 코드: `00` 정상, `03` 데이터 없음, `10`, `11`, `01`, `02`, `05`, `99` 오류
- 이용허락범위 제한 없음, 수정일 2025-06-02

## 확인하지 못한 내용

- `DISPLAY_EFF`의 단위. 문서에 없어 내연기관은 km/L로 가정했다.
- JSON 응답 구조. 문서에 JSON 예시가 없어 `response` 감싸기, `item` 배열과 단일 객체를 모두 받는다.
- `q2`의 부분 일치 여부와 `apiType=json` 값.
- `FUEL_NM`의 실제 값 목록. 휘발유, 가솔린, 경유, 디젤, LPG가 들어간 값만 사용한다.
- 호출 제한.

## 구현 기준

- 키: `local.properties`의 `data_go_kr_api_key`
- 전기, 수소와 알 수 없는 연료는 결과에서 제외한다. 원/L로 계산할 수 없기 때문이다.
- 차종을 고르면 연비 칸과 연료 종류를 채우고, 유가를 직접 고치지 않았다면 평균 유가도 그 연료로 바꾼다.
- 연비를 직접 고치면 선택한 차종을 해제한다.
- 고른 차종은 저장하지 않는다.
- 표시연비는 공인 값이라 실제 연비와 다를 수 있다.
