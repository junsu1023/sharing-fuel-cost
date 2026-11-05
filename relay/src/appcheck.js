// Firebase App Check 토큰을 공개 키(JWKS)로 검증한다.
// 절차: https://firebase.google.com/docs/app-check/custom-resource-backend
// 외부 라이브러리 없이 Workers의 WebCrypto(RS256)만 쓴다.

export const JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks";

/** 공식 문서가 최대 6시간 캐시를 권장한다. */
const JWKS_CACHE_SECONDS = 6 * 60 * 60;

let cachedKeys = null;
let cachedAt = 0;

export function resetJwksCache() {
  cachedKeys = null;
  cachedAt = 0;
}

async function loadKeys(fetchJwks, nowSeconds) {
  if (cachedKeys && nowSeconds - cachedAt < JWKS_CACHE_SECONDS) return cachedKeys;
  const response = await fetchJwks(JWKS_URL);
  if (!response.ok) throw new Error(`jwks ${response.status}`);
  const { keys } = await response.json();
  cachedKeys = keys;
  cachedAt = nowSeconds;
  return keys;
}

function base64UrlToBytes(value) {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
  return Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
}

function decodeJson(part) {
  return JSON.parse(new TextDecoder().decode(base64UrlToBytes(part)));
}

/**
 * 토큰이 유효하면 payload를, 아니면 null을 돌려준다.
 * 문서의 확인 항목: 서명, alg RS256, typ JWT, iss, exp, aud. sub(앱 ID)는 appId가 있을 때 확인한다.
 */
export async function verifyAppCheckToken(token, { projectNumber, appIds = [], fetchJwks = fetch, nowSeconds }) {
  if (!token || !projectNumber) return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const header = decodeJson(parts[0]);
    const payload = decodeJson(parts[1]);
    if (header.alg !== "RS256" || header.typ !== "JWT") return null;

    const now = nowSeconds ?? Math.floor(Date.now() / 1000);
    const keys = await loadKeys(fetchJwks, now);
    const jwk = keys.find((key) => key.kid === header.kid);
    if (!jwk) return null;

    const key = await crypto.subtle.importKey(
      "jwk",
      jwk,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"],
    );
    const signed = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
    const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, base64UrlToBytes(parts[2]), signed);
    if (!valid) return null;

    if (payload.iss !== `https://firebaseappcheck.googleapis.com/${projectNumber}`) return null;
    if (typeof payload.exp !== "number" || payload.exp <= now) return null;
    const audience = Array.isArray(payload.aud) ? payload.aud : [payload.aud];
    if (!audience.includes(`projects/${projectNumber}`)) return null;
    if (appIds.length > 0 && !appIds.includes(payload.sub)) return null;
    return payload;
  } catch {
    return null;
  }
}
