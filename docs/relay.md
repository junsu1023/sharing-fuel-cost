# API 중계 서버

카카오 REST, 오피넷, 공공데이터 인증키를 앱에서 빼고 Cloudflare Workers 중계 서버(`relay/`)에만 둔다. 앱은 키 대신 Firebase App Check 토큰을 보내고, 중계 서버는 토큰이 유효할 때만 키를 붙여 원래 API로 전달한다. (결정: `docs/decisions.md` 2026-11-03, 2026-11-05)

```
앱 ── X-Firebase-AppCheck ──▶ relay (Cloudflare Workers) ── 키 ──▶ 카카오 / 오피넷 / 공공데이터
```

## 경로

앱이 쓰는 GET 경로만 허용하고 나머지는 404로 거절한다.

| 중계 경로 | 원래 주소 | 키를 붙이는 방법 |
|---|---|---|
| `/kakao-navi/v1/directions` | `apis-navi.kakaomobility.com` | `Authorization: KakaoAK` 헤더 |
| `/kakao-local/v2/local/search/address.json`, `keyword.json`, `geo/coord2address.json` | `dapi.kakao.com` | `Authorization: KakaoAK` 헤더 |
| `/opinet/api/avgAllPrice.do` | `www.opinet.co.kr` | `code` 쿼리 (`certkey`는 동작하지 않음) |
| `/data-go-kr/B553530/CAREFF/CAREFF_LIST` | `apis.data.go.kr` | `serviceKey` 쿼리 (디코딩된 키를 넣는다) |
| `/odcloud/api/15083023/v1/uddi:c5d3f252-…` | `api.odcloud.kr` | `serviceKey` 쿼리 (`DATA_GO_KR_API_KEY`를 함께 쓴다) |

- 앱이 `code`, `certkey`, `serviceKey`를 보내도 지우고 중계 서버의 키만 쓴다.
- 원래 서버의 상태 코드와 본문을 그대로 돌려줘, 앱의 오류 구분(인증, 쿼터, 형식)을 유지한다.
- App Check 토큰이 없거나 틀리면 401(`app_check_failed`)을 돌려준다. 앱은 다른 인증 실패처럼 처리하고, 직접 입력으로 진행할 수 있다.

## App Check 검증

[사용자 서버에서 토큰 검증](https://firebase.google.com/docs/app-check/custom-resource-backend)의 절차를 외부 라이브러리 없이 WebCrypto로 구현했다. (`relay/src/appcheck.js`)

- 공개 키: `https://firebaseappcheck.googleapis.com/v1/jwks`, 6시간 캐시
- 확인 항목: RS256 서명, `alg` RS256, `typ` JWT, `iss` `https://firebaseappcheck.googleapis.com/<프로젝트 번호>`, `exp`, `aud`에 `projects/<프로젝트 번호>`, `sub`가 등록한 앱 ID(설정했을 때)
- 재사용 방지(replay protection)는 베타이고 Admin SDK가 필요해 쓰지 않는다.

앱 쪽 (`app` 모듈)

- `google-services.json`이 `app/`에 있을 때만 google-services 플러그인과 Firebase를 켠다. 없으면 토큰 없이 요청해 중계 서버가 거절한다.
- debug 빌드: 디버그 provider (`firebase-appcheck-debug`). release 빌드: Play Integrity provider. 디버그 provider는 release에 들어가지 않는다.
- 토큰은 `X-Firebase-AppCheck` 헤더로 보낸다. 라이브러리가 캐시하고 필요할 때 갱신한다.

## 설정 값

| 이름 | 어디에 | 설명 |
|---|---|---|
| `KAKAO_REST_API_KEY` | Worker 비밀값 | 카카오 REST API 키 |
| `OPINET_API_KEY` | Worker 비밀값 | 오피넷 인증키 |
| `DATA_GO_KR_API_KEY` | Worker 비밀값 | 공공데이터포털 서비스 키 (디코딩 키) |
| `FIREBASE_PROJECT_NUMBER` | Worker 비밀값 | Firebase 프로젝트 번호 (ID가 아니라 숫자) |
| `FIREBASE_APP_IDS` | Worker 비밀값 (선택) | Firebase Android 앱 ID. 여러 개면 쉼표로 구분 |
| `APP_CHECK_ENFORCED` | `wrangler.jsonc`의 `vars` | `"true"`. `"false"`일 때만 검증을 끈다 |
| `relay_base_url` | 앱의 `local.properties` | 배포한 Worker 주소. 비어 있으면 앱은 미연동 상태로 동작 |
| `kakao_native_app_key` | 앱의 `local.properties` | 지도 SDK용이라 앱에 남는다. 키 해시로 묶여 있다 |

`kakao_rest_api_key`, `opinet_api_key`, `data_go_kr_api_key`는 이제 앱이 읽지 않는다.

## 배포 순서 (사용자)

### 1. Cloudflare

1. [Cloudflare](https://dash.cloudflare.com/sign-up)에 가입한다.
2. `cd relay && npm install` (wrangler 4.151.2가 설치된다)
3. `npx wrangler login`
4. 비밀값을 넣는다. 명령마다 값을 묻는다.
   ```
   npx wrangler secret put KAKAO_REST_API_KEY
   npx wrangler secret put OPINET_API_KEY
   npx wrangler secret put DATA_GO_KR_API_KEY
   npx wrangler secret put FIREBASE_PROJECT_NUMBER
   npx wrangler secret put FIREBASE_APP_IDS
   ```
5. `npx wrangler deploy` → 출력된 `https://sharing-fuel-cost-relay.<계정>.workers.dev` 주소를 앱의 `local.properties`에 `relay_base_url`로 넣는다.

### 2. Firebase

1. [Firebase 콘솔](https://console.firebase.google.com/)에서 프로젝트를 만들고 Android 앱(`com.jskim.sharingfuelcost`)을 등록한다.
2. `google-services.json`을 받아 `app/`에 둔다. git에는 넣지 않는다(이미 `.gitignore`에 있음).
3. 프로젝트 설정의 프로젝트 번호와 앱 ID를 위 비밀값으로 넣는다.
4. [보안] > [App Check] > [앱]에서 앱을 Play Integrity로 등록하고 서명 인증서 SHA-256을 넣는다. Play 앱 서명을 쓰면 Play Console [앱 무결성]의 앱 서명 키 SHA-256이다.
5. Play Console [출시] > [앱 무결성] > [Play Integrity API]에서 이 Firebase 프로젝트를 연결한다. (프로젝트 소유자여야 함) 연결하지 않으면 기기에서 토큰을 받지 못해 모든 요청이 401이 된다. 2026-11-10 비공개 테스트에서 실제로 겪었다.
6. 개발용: debug 빌드를 실행하면 logcat `DebugAppCheckProvider`에 "Enter this debug secret into the allow list" 토큰이 나온다고 문서에 있지만, 2026-11-05 에뮬레이터(API 34)에서는 찍히지 않았다. 그때는 `adb shell run-as com.jskim.sharingfuelcost cat shared_prefs/com.google.firebase.appcheck.debug.store.*.xml`의 `DEBUG_SECRET` 값을 쓴다. App Check [앱] 메뉴의 [디버그 토큰 관리]에 등록한다. 앱 데이터를 지우면 토큰이 새로 만들어진다.

Play Integrity 기본 설정은 Google Play가 인식한 앱(`PLAY_RECOGNIZED`)만 통과시킨다. 그래서 release 빌드의 실제 확인은 Play 내부 테스트 트랙으로 설치해서 한다.

## 로컬 개발

1. `relay/.dev.vars`에 비밀값을 적는다(git 제외). App Check 없이 확인하려면 `APP_CHECK_ENFORCED="false"`.
2. `cd relay && npx wrangler dev --port 8787 --ip 127.0.0.1`
3. 앱 `local.properties`에 `relay_base_url=http://10.0.2.2:8787/`을 넣고 debug 빌드를 에뮬레이터에 설치한다. HTTP는 debug 빌드에서 `10.0.2.2`, `localhost`에만 허용한다.
4. 테스트: `cd relay && npm test`

## 확인 상태

| 항목 | 상태 |
|---|---|
| 중계 단위 테스트 (토큰 검증, 허용 경로, 키 주입, 앱 키 제거) | 14건 통과 |
| 로컬 Worker(wrangler dev)로 실제 API 다섯 개 호출 | 2026-11-05 모두 200 |
| 에뮬레이터 debug 빌드가 로컬 중계로 지도 선택부터 결과, 기록, 차종 검색까지 | 2026-11-05 확인 |
| 검증을 켠 중계가 토큰 없는 요청을 401로 거절하고 앱이 직접 입력 안내로 전환 | 2026-11-05 확인 |
| APK에 REST 키 세 개가 없는지 | debug, release 모두 없음 |
| Cloudflare에 배포한 Worker에서 국내 API 호출 | 2026-11-05 확인. 카카오 길찾기, 로컬, 오피넷, 공공데이터 모두 응답(해외 IP 차단 없음) |
| 배포한 Worker가 토큰 없는 요청 401, 모르는 경로 404 | 2026-11-05 확인 |
| 실제 Firebase 디버그 토큰으로 검증 통과 | 2026-11-05 에뮬레이터 debug 빌드로 정산 전체 흐름과 차종 검색 확인 |
| Play Integrity 토큰으로 검증 통과 (release) | 2026-11-10 비공개 테스트 설치본으로 확인. `wrangler tail`에서 주소 검색, 거리 조회, 유가, 차종 검색 모두 200 |
