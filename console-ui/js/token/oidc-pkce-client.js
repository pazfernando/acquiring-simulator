/*
 * OIDC authorization-code + PKCE client and token seam for the Operation A console.
 *
 * Binding design: design-rev4.md section 2.2 (demo login and token lifecycle) and R-7.
 *
 * WHAT THIS MODULE IS RESPONSIBLE FOR
 *  - It is a PUBLIC OIDC client. No client secret exists, none is embedded here, and none
 *    may be added: bundled scripts are served from the console origin.
 *  - The access token lives in this module's closure memory only. It is never written to
 *    localStorage, sessionStorage or a cookie, and it is never placed in the address bar.
 *  - Every request the page makes carries an explicit Authorization header. There is no
 *    ambient credential and no server-side session.
 *  - Configuration (issuer, client id, audience, scope, endpoints) comes from runtime
 *    configuration served to the page. Nothing about the deployment is baked into this file.
 *
 * TWO MODES, AND WHY THE REFUSAL EXISTS
 *  - "oidc": real authorization-code + PKCE against a configured issuer. This module REFUSES
 *    TO START in this mode unless the runtime configuration explicitly enables PKCE
 *    (pkce: true). A public client that skipped PKCE would be sending an authorization code
 *    that an attacker could intercept; the check therefore runs before any redirect, any
 *    token request and any network call.
 *  - "dev-stub": the local development stand-in. IT IS NOT AUTHENTICATION. It asks the local
 *    development harness for a demo token, and nothing on either side verifies a signature,
 *    an issuer or a key. It exists so a person can exercise the form locally. It must never
 *    be enabled in a deployed build; the harness that serves it is a local entry point only.
 *
 * WHAT IS NOT IMPLEMENTED OR PROVEN HERE
 *  - The "oidc" branch below is written but NOT executed: no issuer exists in this slice, so
 *    it has no test coverage and is not claimed to work. Only the dev-stub branch is
 *    exercised locally.
 *  - No token revocation, no refresh-token rotation and no absolute session cap are
 *    implemented. design-rev4.md section 2.2 leaves refresh-vs-re-login as an implementation
 *    choice; this slice takes the simplest option for the stub (re-obtain on expiry) and
 *    marks the real branch incomplete rather than silently claiming it.
 *  - Browser behaviour of this module is not covered by the repository's focused tests. The
 *    tests cover the harness HTTP surface, not this JavaScript.
 */

import { acquireDevStubToken } from "./dev-stub-provider.js";

export const OIDC_MODE = "oidc";
export const DEV_STUB_MODE = "dev-stub";

/** Load and shape runtime configuration. A missing or unreadable config is a hard failure. */
export async function loadRuntimeConfig(url = "/config.json") {
  const response = await fetch(url, { credentials: "omit", cache: "no-store" });
  if (!response.ok) {
    throw new Error("runtime configuration is unavailable (HTTP " + response.status + ")");
  }
  const config = await response.json();
  assertStartable(config);
  return config;
}

/**
 * Refuse to start when the configuration cannot be honoured safely.
 *
 * The refusal that matters is explicit: a real issuer ("oidc" mode) without PKCE is rejected
 * here, before a single request is made.
 */
export function assertStartable(config) {
  if (!config || typeof config !== "object") {
    throw new Error("runtime configuration is not an object");
  }
  if (config.mode !== OIDC_MODE && config.mode !== DEV_STUB_MODE) {
    throw new Error('runtime configuration mode must be "' + OIDC_MODE + '" or "' + DEV_STUB_MODE + '"');
  }
  for (const name of ["issuer", "clientId", "audience", "scope"]) {
    if (typeof config[name] !== "string" || config[name].trim() === "") {
      throw new Error("runtime configuration is missing '" + name + "'");
    }
  }
  if (config.mode === OIDC_MODE) {
    if (!config.issuer.startsWith("https://")) {
      throw new Error("refusing to start: a real issuer must be served over https");
    }
    if (config.pkce !== true) {
      throw new Error(
        "refusing to start: mode \"oidc\" against a real issuer requires PKCE (config.pkce must be true)"
      );
    }
  }
  if (config.mode === DEV_STUB_MODE) {
    // The stub is only reachable when the harness says so in the configuration it served.
    // A production configuration that merely flipped mode to "dev-stub" is refused here.
    if (config.stub !== true) {
      throw new Error("refusing to start: dev-stub mode requires the harness marker stub: true");
    }
    if (typeof config.tokenEndpoint !== "string" || !config.tokenEndpoint.startsWith("/")) {
      throw new Error("refusing to start: dev-stub mode requires a same-origin tokenEndpoint");
    }
  }
}

function base64url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/**
 * Create the token client. The returned functions are the only way the page can obtain,
 * read or discard a token; the token itself is never returned to the DOM and never logged.
 */
export function createTokenClient(config) {
  assertStartable(config);

  let accessToken = null;      // memory only
  let expiresAtMillis = null;  // memory only
  let credentialKind = "none";
  let pendingState = null;     // memory only (PKCE verifier + state)
  let pendingVerifier = null;

  function clearCredential() {
    accessToken = null;
    expiresAtMillis = null;
    credentialKind = "none";
    pendingState = null;
    pendingVerifier = null;
  }

  function token() {
    if (accessToken === null) return null;
    if (expiresAtMillis !== null && Date.now() >= expiresAtMillis) {
      clearCredential();
      return null;
    }
    return accessToken;
  }

  async function startDevStub() {
    clearCredential();
    const issued = await acquireDevStubToken(config.tokenEndpoint);
    accessToken = issued.accessToken;
    expiresAtMillis = Date.now() + issued.expiresIn * 1000;
    credentialKind = DEV_STUB_MODE;
    return credentialKind;
  }

  async function pkcePair() {
    const verifier = base64url(crypto.getRandomValues(new Uint8Array(32)));
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
    return { verifier, challenge: base64url(new Uint8Array(digest)) };
  }

  function tokenEndpointUrl() {
    return config.tokenEndpoint && config.tokenEndpoint.startsWith("https://")
      ? config.tokenEndpoint
      : config.issuer + "/protocol/openid-connect/token";
  }

  async function startOidc() {
    const pair = await pkcePair();
    pendingVerifier = pair.verifier;
    pendingState = base64url(crypto.getRandomValues(new Uint8Array(16)));
    const query = new URLSearchParams({
      response_type: "code",
      client_id: config.clientId,
      redirect_uri: config.redirectUri,
      scope: "openid " + config.scope,
      audience: config.audience,
      state: pendingState,
      code_challenge: pair.challenge,
      code_challenge_method: "S256"
    });
    window.location.assign(config.issuer + "/protocol/openid-connect/auth?" + query.toString());
    return OIDC_MODE; // unreachable in practice: the page navigates away
  }

  /** Handle the authorization redirect. Removes the code from the address bar immediately. */
  async function completeOidcCallback() {
    const params = new URLSearchParams(window.location.search);
    const code = params.get("code");
    const returnedState = params.get("state");
    if (!code) return false;
    if (pendingState === null || returnedState !== pendingState || pendingVerifier === null) {
      clearCredential();
      throw new Error("authorization callback state does not match this browser session");
    }
    const form = new URLSearchParams({
      grant_type: "authorization_code",
      code,
      redirect_uri: config.redirectUri,
      client_id: config.clientId,
      code_verifier: pendingVerifier
    });
    const response = await fetch(tokenEndpointUrl(), {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
      body: form.toString(),
      credentials: "omit",
      cache: "no-store"
    });
    // Strip the code from the address bar whether or not the exchange succeeds.
    window.history.replaceState(null, "", window.location.pathname);
    if (!response.ok) {
      clearCredential();
      throw new Error("token exchange failed (HTTP " + response.status + ")");
    }
    const body = await response.json();
    accessToken = body.access_token;
    expiresAtMillis = Date.now() + (Number(body.expires_in) || 0) * 1000;
    credentialKind = OIDC_MODE;
    pendingState = null;
    pendingVerifier = null;
    return true;
  }

  return {
    mode: config.mode,
    /** Begin sign-in. In oidc mode this navigates away and does not return here. */
    start: () => (config.mode === OIDC_MODE ? startOidc() : startDevStub()),
    completeCallback: completeOidcCallback,
    token,
    expiresAtMillis: () => expiresAtMillis,
    credentialKind: () => credentialKind,
    clear: clearCredential
  };
}

/**
 * The persistence APIs this module deliberately does not use. Exported as a reviewable
 * statement, not as runtime behaviour: a reviewer can confirm the token never leaves memory.
 */
export const FORBIDDEN_PERSISTENCE_APIS = Object.freeze([
  "localStorage",
  "sessionStorage",
  "document.cookie",
  "indexedDB"
]);
