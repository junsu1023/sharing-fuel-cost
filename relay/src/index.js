// 앱의 외부 API 호출을 중계한다. 인증키는 Worker 비밀값에만 두고, 앱이 쓰는 경로만 허용한다.
import { verifyAppCheckToken } from "./appcheck.js";

/**
 * 앱 경로 접두사 → 원래 서버와 키를 붙이는 방법.
 * 경로는 앱(data 모듈)의 Retrofit 정의와 같다.
 */
const ROUTES = [
  {
    prefix: "/kakao-navi/",
    origin: "https://apis-navi.kakaomobility.com/",
    paths: ["v1/directions"],
    authorize: (url, headers, env) => headers.set("Authorization", `KakaoAK ${env.KAKAO_REST_API_KEY}`),
  },
  {
    prefix: "/kakao-local/",
    origin: "https://dapi.kakao.com/",
    paths: ["v2/local/search/address.json", "v2/local/search/keyword.json", "v2/local/geo/coord2address.json"],
    authorize: (url, headers, env) => headers.set("Authorization", `KakaoAK ${env.KAKAO_REST_API_KEY}`),
  },
  {
    prefix: "/opinet/",
    origin: "https://www.opinet.co.kr/",
    paths: ["api/avgAllPrice.do"],
    // 공식 문서의 certkey가 아니라 code만 동작한다. (2026-11-01 호출로 확인)
    authorize: (url, headers, env) => url.searchParams.set("code", env.OPINET_API_KEY),
  },
  {
    prefix: "/data-go-kr/",
    origin: "https://apis.data.go.kr/",
    paths: ["B553530/CAREFF/CAREFF_LIST"],
    // 비밀값에는 디코딩된 키를 넣는다. searchParams가 한 번만 인코딩한다.
    authorize: (url, headers, env) => url.searchParams.set("serviceKey", env.DATA_GO_KR_API_KEY),
  },
  {
    // 한국에너지공단 자동차 표시연비 정보(2026-04-24). 플러그인 하이브리드의 전비만 여기서 가져온다.
    prefix: "/odcloud/",
    origin: "https://api.odcloud.kr/",
    paths: ["api/15083023/v1/uddi:c5d3f252-f55b-469c-8573-e53160dd9e69"],
    // 공공데이터포털 같은 계정의 키라 DATA_GO_KR_API_KEY를 함께 쓴다. (2026-11-12 호출로 확인)
    authorize: (url, headers, env) => url.searchParams.set("serviceKey", env.DATA_GO_KR_API_KEY),
  },
];

/** 앱이 키를 보내더라도 쓰지 않도록 지운다. */
const CLIENT_KEY_PARAMS = ["code", "certkey", "serviceKey", "ServiceKey"];

function json(status, error) {
  return new Response(JSON.stringify({ error }), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

function appIds(env) {
  return (env.FIREBASE_APP_IDS ?? "").split(",").map((id) => id.trim()).filter(Boolean);
}

export async function handleRequest(request, env, { fetchUpstream = fetch, fetchJwks = fetch } = {}) {
  if (request.method !== "GET") return json(405, "method_not_allowed");

  const incoming = new URL(request.url);
  const route = ROUTES.find((r) => incoming.pathname.startsWith(r.prefix));
  const path = route && incoming.pathname.slice(route.prefix.length);
  if (!route || !route.paths.includes(path)) return json(404, "not_found");

  if (env.APP_CHECK_ENFORCED !== "false") {
    const token = request.headers.get("X-Firebase-AppCheck");
    const verified = await verifyAppCheckToken(token, {
      projectNumber: env.FIREBASE_PROJECT_NUMBER,
      appIds: appIds(env),
      fetchJwks,
    });
    if (!verified) return json(401, "app_check_failed");
  }

  const upstream = new URL(path, route.origin);
  incoming.searchParams.forEach((value, name) => {
    if (!CLIENT_KEY_PARAMS.includes(name)) upstream.searchParams.append(name, value);
  });
  const headers = new Headers({ accept: request.headers.get("accept") ?? "application/json" });
  route.authorize(upstream, headers, env);

  const response = await fetchUpstream(upstream.toString(), { method: "GET", headers });
  // 원래 서버의 상태 코드와 본문을 그대로 돌려줘 앱의 오류 구분(인증, 형식, 네트워크)을 유지한다.
  return new Response(response.body, {
    status: response.status,
    headers: { "content-type": response.headers.get("content-type") ?? "application/json" },
  });
}

export default {
  fetch: (request, env) => handleRequest(request, env),
};
