# 오피넷 평균 유가

**현재 상태: 구현 완료, 실제 호출 미확인.** 인증키가 등록되면 실제 호출로 응답 구조를 확인한다.

## 공식 문서 확인 내용 (2026-11-01)

[주유소 전국 평균가격 API](https://www.opinet.co.kr/user/custapi/openApiInfoDtl.do?apiId=4)

- 요청: `https://www.opinet.co.kr/api/avgAllPrice.do?out=json&certkey=[인증키]`
- 응답 필드: `TRADE_DT`(일자), `PRODCD`(제품 코드), `PRODNM`(제품명), `PRICE`(평균 가격), `DIFF`(전일 대비)
- 사용 코드: `B027` 휘발유, `D047` 자동차용경유, `K015` 자동차용부탄
- 인증키는 [오피넷 인증키 발급](https://www.opinet.co.kr/user/custapi/openApiNew.do)에서 받는다.
- [이용 안내](https://www.opinet.co.kr/user/custapi/openApiIntro.do): 출처가 석유공사임을 명시해야 한다. 수익 목적 이용은 석유공사의 사전 협의나 허가가 필요하다. 문의 price@knoc.co.kr

## 확인하지 못한 내용

- JSON 응답 구조. 문서에는 XML 예시만 있어 `RESULT.OIL[]`로 추정했다. 다르면 해석 실패로 처리되어 직접 입력으로 넘어간다.
- 가격 단위, 호출 제한, 갱신 주기. 문서에 없다. 단위는 원/L로 가정했다.

## 구현 기준

- 키: `local.properties`의 `opinet_api_key`
- 앱을 열 때 저장값의 날짜가 오늘이 아니면 새로 받는다. 받지 못하면 마지막 저장값을 쓴다. 백그라운드 갱신은 하지 않는다.
- 사용자가 유가를 직접 고치면 덮어쓰지 않는다. "평균가 적용"으로 되돌릴 수 있다.
- 입력칸에는 1원 단위로 반올림한 값을 넣는다.
