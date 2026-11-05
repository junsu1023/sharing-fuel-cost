import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { resetJwksCache, verifyAppCheckToken } from "../src/appcheck.js";
import { handleRequest } from "../src/index.js";

const PROJECT = "123456789";
const APP_ID = "1:123456789:android:abc";
const NOW = 1_800_000_000;

const keyPair = await crypto.subtle.generateKey(
  { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
  true,
  ["sign", "verify"],
);
const otherPair = await crypto.subtle.generateKey(
  { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
  true,
  ["sign", "verify"],
);
const publicJwk = { ...(await crypto.subtle.exportKey("jwk", keyPair.publicKey)), kid: "k1", alg: "RS256", use: "sig" };

let jwksCalls = 0;
const fetchJwks = async () => {
  jwksCalls++;
  return new Response(JSON.stringify({ keys: [publicJwk] }));
};

const b64url = (bytes) => Buffer.from(bytes).toString("base64url");
const part = (obj) => b64url(new TextEncoder().encode(JSON.stringify(obj)));

async function token({ header = {}, payload = {}, signer = keyPair.privateKey } = {}) {
  const h = part({ alg: "RS256", typ: "JWT", kid: "k1", ...header });
  const p = part({
    iss: `https://firebaseappcheck.googleapis.com/${PROJECT}`,
    aud: [`projects/${PROJECT}`],
    sub: APP_ID,
    exp: NOW + 3600,
    ...payload,
  });
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", signer, new TextEncoder().encode(`${h}.${p}`));
  return `${h}.${p}.${b64url(new Uint8Array(signature))}`;
}

const verify = (t, options = {}) =>
  verifyAppCheckToken(t, { projectNumber: PROJECT, appIds: [APP_ID], fetchJwks, nowSeconds: NOW, ...options });

beforeEach(() => {
  resetJwksCache();
  jwksCalls = 0;
});

test("valid token passes and returns payload", async () => {
  const payload = await verify(await token());
  assert.equal(payload.sub, APP_ID);
});

test("token signed with another key fails", async () => {
  assert.equal(await verify(await token({ signer: otherPair.privateKey })), null);
});

test("wrong issuer, audience, app id, or expired token fails", async () => {
  assert.equal(await verify(await token({ payload: { iss: "https://firebaseappcheck.googleapis.com/999" } })), null);
  assert.equal(await verify(await token({ payload: { aud: ["projects/999"] } })), null);
  assert.equal(await verify(await token({ payload: { sub: "1:999:android:zzz" } })), null);
  assert.equal(await verify(await token({ payload: { exp: NOW - 1 } })), null);
});

test("wrong algorithm, type, unknown key id, or malformed token fails", async () => {
  assert.equal(await verify(await token({ header: { alg: "none" } })), null);
  assert.equal(await verify(await token({ header: { typ: "at+jwt" } })), null);
  assert.equal(await verify(await token({ header: { kid: "unknown" } })), null);
  assert.equal(await verify("not.a-token"), null);
  assert.equal(await verify(null), null);
});

test("app id check is skipped when no app ids are configured", async () => {
  assert.ok(await verify(await token({ payload: { sub: "other" } }), { appIds: [] }));
});

test("public keys are cached between verifications", async () => {
  await verify(await token());
  await verify(await token());
  assert.equal(jwksCalls, 1);
});

// --- 중계 ---

const env = {
  KAKAO_REST_API_KEY: "kakao-secret",
  OPINET_API_KEY: "opinet-secret",
  DATA_GO_KR_API_KEY: "a+b/c==",
  FIREBASE_PROJECT_NUMBER: PROJECT,
  FIREBASE_APP_IDS: APP_ID,
};

function recorder(status = 200, body = '{"ok":true}') {
  const calls = [];
  const fetchUpstream = async (url, init) => {
    calls.push({ url: new URL(url), headers: init.headers });
    return new Response(body, { status, headers: { "content-type": "application/json" } });
  };
  return { calls, fetchUpstream };
}

async function relay(path, { appCheck, method = "GET", envOverride = {}, upstream = recorder() } = {}) {
  const headers = appCheck === undefined ? { "X-Firebase-AppCheck": await token({ payload: { exp: 4_000_000_000 } }) } : appCheck;
  const response = await handleRequest(
    new Request(`https://relay.example${path}`, { method, headers }),
    { ...env, ...envOverride },
    { fetchUpstream: upstream.fetchUpstream, fetchJwks },
  );
  return { response, calls: upstream.calls };
}

test("kakao directions is relayed with authorization header and query kept", async () => {
  const { response, calls } = await relay("/kakao-navi/v1/directions?origin=127,37&destination=128,38");
  assert.equal(response.status, 200);
  assert.equal(calls[0].url.origin + calls[0].url.pathname, "https://apis-navi.kakaomobility.com/v1/directions");
  assert.equal(calls[0].url.searchParams.get("origin"), "127,37");
  assert.equal(calls[0].headers.get("Authorization"), "KakaoAK kakao-secret");
});

test("kakao local search is relayed to dapi", async () => {
  const { calls } = await relay("/kakao-local/v2/local/search/keyword.json?query=%EA%B0%95%EB%82%A8%EC%97%AD");
  assert.equal(calls[0].url.origin + calls[0].url.pathname, "https://dapi.kakao.com/v2/local/search/keyword.json");
  assert.equal(calls[0].url.searchParams.get("query"), "강남역");
});

test("opinet gets key as code and client supplied key is ignored", async () => {
  const { calls } = await relay("/opinet/api/avgAllPrice.do?out=json&code=attacker&certkey=x");
  assert.equal(calls[0].url.searchParams.get("code"), "opinet-secret");
  assert.equal(calls[0].url.searchParams.getAll("code").length, 1);
  assert.equal(calls[0].url.searchParams.get("certkey"), null);
  assert.equal(calls[0].url.searchParams.get("out"), "json");
});

test("data.go.kr gets decoded service key encoded exactly once", async () => {
  const { calls } = await relay("/data-go-kr/B553530/CAREFF/CAREFF_LIST?q2=K5&serviceKey=attacker");
  assert.equal(calls[0].url.searchParams.get("serviceKey"), "a+b/c==");
  assert.ok(calls[0].url.search.includes("serviceKey=a%2Bb%2Fc%3D%3D"));
});

test("unknown path, other host path, and non GET method are rejected without calling upstream", async () => {
  for (const path of ["/kakao-local/v2/user/me", "/kakao-navi/v1/waypoints/directions", "/other", "/"]) {
    const { response, calls } = await relay(path);
    assert.equal(response.status, 404, path);
    assert.equal(calls.length, 0);
  }
  const { response } = await relay("/kakao-navi/v1/directions", { method: "POST" });
  assert.equal(response.status, 405);
});

test("missing or invalid app check token is rejected with 401", async () => {
  const missing = await relay("/opinet/api/avgAllPrice.do", { appCheck: {} });
  assert.equal(missing.response.status, 401);
  assert.equal(missing.calls.length, 0);
  const forged = await relay("/opinet/api/avgAllPrice.do", {
    appCheck: { "X-Firebase-AppCheck": await token({ signer: otherPair.privateKey }) },
  });
  assert.equal(forged.response.status, 401);
});

test("app check can be turned off only by explicit false", async () => {
  const { response } = await relay("/opinet/api/avgAllPrice.do", { appCheck: {}, envOverride: { APP_CHECK_ENFORCED: "false" } });
  assert.equal(response.status, 200);
});

test("upstream status and body are passed through", async () => {
  const { response } = await relay("/kakao-navi/v1/directions", { upstream: recorder(401, '{"code":-401}') });
  assert.equal(response.status, 401);
  assert.equal(await response.text(), '{"code":-401}');
});
