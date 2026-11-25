/*
 * DEV STUB TOKEN PROVIDER — THIS IS NOT AUTHENTICATION.
 *
 * It exists for one purpose: let a person exercise the console form against the local
 * development harness without Keycloak. The harness mints a demo token and returns it to
 * this module; no signature is verified, no issuer is contacted, no key exists, and anyone
 * who can reach the harness can obtain the same token. It proves nothing about who is
 * calling. Do not describe it as a login, a session or an identity check.
 *
 * The token is handed straight back to the caller, which keeps it in closure memory only.
 * This module never stores it, never logs it and never writes it to a URL.
 *
 * The token format and its claims are produced by the Java side of the seam
 * (com.acquiring.simulator.console.dev.DevStubTokenIssuer). Nothing here invents token
 * bytes: there is exactly one implementation of the format, so the browser and the route
 * cannot drift apart.
 */

export async function acquireDevStubToken(endpoint) {
  const response = await fetch(endpoint, {
    method: "POST",
    headers: { Accept: "application/json" },
    credentials: "omit",
    cache: "no-store"
  });
  if (!response.ok) {
    throw new Error("dev stub token endpoint refused (HTTP " + response.status + ")");
  }
  const body = await response.json();
  if (typeof body.accessToken !== "string" || body.accessToken === "") {
    throw new Error("dev stub token endpoint returned no access token");
  }
  if (body.stub !== true) {
    throw new Error("dev stub token endpoint did not identify itself as a development stub");
  }
  return {
    accessToken: body.accessToken,
    expiresIn: Number(body.expiresIn) || 0,
    stub: true
  };
}
