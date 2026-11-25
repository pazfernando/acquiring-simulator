/*
 * Truthful presentation of one console response.
 *
 * Every function here is pure and has no DOM dependency, so a Node test can exercise it without a
 * browser. It never invents a value the server did not return, never upgrades a provisional
 * acknowledgement into the C3 envelope, and never describes a synthetic approval as money moving.
 */

/** Pretty JSON with a bounded rendered size; the raw body is passed in separately. */
export function prettyJson(value) {
  if (value === null || value === undefined) return "";
  if (typeof value === "string") return value;
  try {
    return JSON.stringify(value, null, 2);
  } catch (error) {
    return String(value);
  }
}

/** The provisional acknowledgement keys the route documents for a reached-service outcome. */
const PROVISIONAL_KEYS = ["authorizationReference", "correlationId", "outcome"];

/**
 * True only when the body has exactly the provisional acknowledgement shape and nothing else.
 * A C3 envelope carries contractVersion and many more keys, so it is never classified here.
 */
export function isProvisionalAcknowledgement(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) return false;
  const keys = Object.keys(body).sort();
  return keys.length === PROVISIONAL_KEYS.length
    && keys.every((key, index) => key === [...PROVISIONAL_KEYS].sort()[index]);
}

function errorCodeOf(body) {
  if (body && typeof body === "object" && typeof body.error === "string") return body.error;
  return null;
}

function outcomeOf(body) {
  if (body && typeof body === "object" && typeof body.outcome === "string") return body.outcome;
  return null;
}

/**
 * Classify one response for display.
 *
 * @returns {{
 *   tone: "ok"|"warn"|"danger"|"neutral",
 *   badge: string,
 *   headline: string,
 *   detail: string,
 *   provisional: boolean
 * }}
 */
export function classifyResponse(status, body, transportError) {
  if (transportError) {
    return {
      tone: "danger",
      badge: "No response",
      headline: "The request did not reach the harness.",
      detail: "Nothing can be concluded about a payment. " + transportError,
      provisional: false
    };
  }

  const error = errorCodeOf(body);
  const outcome = outcomeOf(body);
  const provisional = isProvisionalAcknowledgement(body);
  const empty = body === null || body === undefined || body === "";

  if (status === 200 && outcome === "APPROVED") {
    return {
      tone: "ok",
      badge: "200 · APPROVED",
      headline: "HTTP 200 · synthetic APPROVED",
      detail:
        "The authorization service returned an approval for this submission. This is a synthetic "
        + "simulator decision, not a real card authorization and not evidence that funds moved. "
        + (provisional
          ? "The body is the provisional acknowledgement, not the closed C3 envelope."
          : ""),
      provisional
    };
  }
  if (status === 200 && outcome === "DECLINED") {
    return {
      tone: "danger",
      badge: "200 · DECLINED",
      headline: "HTTP 200 · synthetic DECLINED",
      detail:
        "The deterministic simulator declined this submission. Synthetic result only; no real "
        + "issuer was contacted.",
      provisional
    };
  }
  if (status === 200 && outcome === "NOT_CONFIRMED") {
    return {
      tone: "warn",
      badge: "200 · NOT CONFIRMED",
      headline: "HTTP 200 · NOT_CONFIRMED",
      detail:
        "The outcome is not confirmed. This is never a finality claim and must not be read as "
        + "approved or declined.",
      provisional
    };
  }
  if (status === 202) {
    return {
      tone: "warn",
      badge: "202 · in flight",
      headline: "HTTP 202 · in flight, not final",
      detail:
        "The route accepted the submission but did not return a final outcome. A 202 is never a "
        + "finality claim.",
      provisional
    };
  }
  if (status === 401) {
    return {
      tone: "danger",
      badge: "401 · refused",
      headline: "HTTP 401 · refused before any business action",
      detail:
        "The credential seam refused the request" + (error ? " (" + error + ")" : "")
        + ". No claim, event, send or rejection audit should exist for it.",
      provisional: false
    };
  }
  if (status === 403) {
    return {
      tone: "danger",
      badge: "403 · refused",
      headline: "HTTP 403 · refused before any business action",
      detail:
        "The request was refused" + (error ? " (" + error + ")" : "")
        + ", for example because the trusted context did not match. Nothing was written or sent.",
      provisional: false
    };
  }
  if (status === 400) {
    return {
      tone: "warn",
      badge: "400 · refused",
      headline: "HTTP 400 · rejected before any business action",
      detail:
        "The closed C1 body or the C3 envelope refused the request"
        + (error ? " (" + error + ")" : "") + ". Nothing was written or sent.",
      provisional: false
    };
  }
  if (status === 413) {
    return {
      tone: "warn",
      badge: "413 · too large",
      headline: "HTTP 413 · body too large",
      detail: "The body exceeded the approved C1 size bound. Nothing was written or sent.",
      provisional: false
    };
  }
  if (status === 415) {
    return {
      tone: "warn",
      badge: "415 · media type",
      headline: "HTTP 415 · unsupported media type",
      detail: "Only application/json is accepted. Nothing was written or sent.",
      provisional: false
    };
  }
  if (status === 429) {
    return {
      tone: "warn",
      badge: "429 · refused",
      headline: "HTTP 429 · rate-limit seam refused",
      detail:
        "The local rate-limit seam refused the request. No numeric limit is configured in this "
        + "slice; the default seam is permissive, so a 429 indicates a substituted seam.",
      provisional: false
    };
  }
  if (status >= 500) {
    return {
      tone: "danger",
      badge: status + " · server error",
      headline: "HTTP " + status + " · the harness or route failed",
      detail:
        "This is not a payment outcome. Inspect the harness terminal output; the response body "
        + "may not be a C3 envelope.",
      provisional: false
    };
  }
  if (empty) {
    return {
      tone: "neutral",
      badge: status + " · empty body",
      headline: "HTTP " + status + " · empty body",
      detail: "The route returned no body for this status; do not invent one.",
      provisional: false
    };
  }
  return {
    tone: "neutral",
    badge: String(status),
    headline: "HTTP " + status + (error ? " · " + error : ""),
    detail: "The body was not recognised as an outcome this console knows how to describe.",
    provisional
  };
}

/** Human text for the status only; used by the history table. */
export function shortOutcome(status, body) {
  const outcome = outcomeOf(body);
  if (outcome) return outcome;
  const error = errorCodeOf(body);
  if (error) return error;
  return status === 429 ? "RATE_LIMITED" : "—";
}
