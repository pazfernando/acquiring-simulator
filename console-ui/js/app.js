/*
 * Local development console entry point.
 *
 * Composition only: this module wires the C1 form, the dev-stub token seam and the truthful
 * renderer to the verified console route mounted by the loopback harness. It never holds a
 * credential outside the token client's closure, never logs or renders a token, never persists
 * anything, and never retries or resends a payment.
 */

import { loadRuntimeConfig, createTokenClient, DEV_STUB_MODE } from "./token/oidc-pkce-client.js";
import { applyDemoContext, fillDefaultReferences, readSubmission } from "./form.js";
import { classifyResponse, prettyJson, shortOutcome } from "./render.js";
import { createSessionHistory, localTime } from "./session-history.js";

const SUBMISSION_PATH = "/console/v1/authorizations";

const history = createSessionHistory();

const ui = {
  badge: document.getElementById("credential-badge"),
  detail: document.getElementById("credential-detail"),
  connect: document.getElementById("connect-button"),
  forget: document.getElementById("forget-button"),
  form: document.getElementById("authorization-form"),
  formError: document.getElementById("form-error"),
  submit: document.getElementById("submit-button"),
  globalNotice: document.getElementById("global-notice"),
  resultEmpty: document.getElementById("result-empty"),
  resultPanel: document.getElementById("result-panel"),
  resultBadge: document.getElementById("result-badge"),
  resultSummary: document.getElementById("result-summary"),
  resultNotice: document.getElementById("result-notice"),
  resultJson: document.getElementById("result-json"),
  resultMeta: document.getElementById("result-meta"),
  historyEmpty: document.getElementById("history-empty"),
  historyWrap: document.getElementById("history-wrap"),
  historyBody: document.getElementById("history-body"),
  clearHistory: document.getElementById("clear-history-button")
};

let tokenClient = null;

start().catch((error) => {
  showGlobal("error", "The console could not start: " + messageOf(error));
});

async function start() {
  const config = await loadRuntimeConfig();
  tokenClient = createTokenClient(config);

  applyDemoContext(ui.form, config.demoContext);
  fillDefaultReferences(ui.form);

  ui.connect.addEventListener("click", () => acquireCredential());
  ui.forget.addEventListener("click", () => {
    tokenClient.clear();
    updateCredentialState();
    showGlobal("info", "The in-memory credential was discarded.");
  });
  ui.form.addEventListener("submit", (event) => {
    event.preventDefault();
    submitAuthorization();
  });
  ui.clearHistory.addEventListener("click", () => {
    history.clear();
    renderHistory();
  });

  updateCredentialState();
  renderHistory();
  window.setInterval(updateCredentialState, 5000);

  if (config.mode !== DEV_STUB_MODE) {
    showGlobal(
      "info",
      "Runtime configuration selected mode \"" + config.mode + "\". This local screen is built "
        + "for the dev-stub mode and does not claim that the real OIDC branch works."
    );
  }
}

async function acquireCredential() {
  ui.connect.disabled = true;
  hideFormError();
  try {
    await tokenClient.start();
    updateCredentialState();
    showGlobal("ok", "Dev stub credential acquired. It is unsigned and proves nothing.");
  } catch (error) {
    updateCredentialState();
    showGlobal("error", "The dev stub token endpoint refused: " + messageOf(error));
  } finally {
    ui.connect.disabled = false;
  }
}

async function submitAuthorization() {
  hideFormError();

  if (!ui.form.checkValidity()) {
    ui.form.reportValidity();
    return;
  }

  const token = tokenClient ? tokenClient.token() : null;
  if (!token) {
    showFormError("Acquire the dev stub credential first; without it the route refuses with 401.");
    return;
  }

  let submission;
  try {
    submission = readSubmission(ui.form);
  } catch (error) {
    showFormError(messageOf(error));
    return;
  }

  ui.submit.disabled = true;
  let status = 0;
  let body = null;
  let transportError = null;

  try {
    const response = await fetch(SUBMISSION_PATH, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        Authorization: "Bearer " + token
      },
      body: JSON.stringify(submission),
      credentials: "omit",
      cache: "no-store"
    });
    status = response.status;
    const text = await response.text();
    if (text !== "") {
      try {
        body = JSON.parse(text);
      } catch (parseFailure) {
        body = text;
      }
    }
  } catch (error) {
    transportError = messageOf(error);
    showGlobal("error", "Network failure: " + transportError + ". Nothing can be concluded.");
  } finally {
    ui.submit.disabled = false;
  }

  if (!transportError) {
    renderResult(status, body);
    history.add({
      paymentRef: submission.paymentRef,
      status,
      outcome: shortOutcome(status, body),
      reference: body && typeof body === "object" ? body.authorizationReference || "" : ""
    });
    renderHistory();
  }

  if (status === 401 || status === 403) {
    tokenClient.clear();
    updateCredentialState();
    showGlobal(
      "error",
      "The route refused the credential (HTTP " + status + "). The in-memory token was discarded. "
        + "Acquire a new dev stub credential to continue."
    );
  }
}

function renderResult(status, body) {
  const view = classifyResponse(status, body, null);

  ui.resultEmpty.hidden = true;
  ui.resultPanel.hidden = false;

  ui.resultBadge.textContent = view.badge;
  ui.resultBadge.className = "badge " + badgeClass(view.tone);

  ui.resultSummary.textContent = view.headline;
  ui.resultJson.textContent = prettyJson(body) || "(empty body)";

  if (view.provisional) {
    ui.resultNotice.hidden = false;
    ui.resultNotice.className = "notice notice-warn";
    ui.resultNotice.textContent =
      "This is the provisional acknowledgement {authorizationReference, correlationId, outcome}, "
      + "not the closed C3 envelope. The service does not yet expose the committed business "
      + "version, identifiers or rejection reason this screen would need to render C3.";
  } else {
    ui.resultNotice.hidden = true;
    ui.resultNotice.textContent = "";
  }

  const fields = [
    ["HTTP status", String(status)],
    ["Outcome", body && typeof body === "object" && body.outcome ? body.outcome : "—"],
    ["Authorization reference",
      body && typeof body === "object" && body.authorizationReference
        ? body.authorizationReference
        : "—"],
    ["Correlation ID",
      body && typeof body === "object" && body.correlationId ? body.correlationId : "—"]
  ];
  const nodes = fields.map(([term, value]) => {
    const wrapper = document.createElement("div");
    const dt = document.createElement("dt");
    dt.textContent = term;
    const dd = document.createElement("dd");
    dd.textContent = value;
    wrapper.append(dt, dd);
    return wrapper;
  });
  ui.resultMeta.replaceChildren(...nodes);
}

function renderHistory() {
  const entries = history.entries();
  const empty = entries.length === 0;

  ui.historyEmpty.hidden = !empty;
  ui.historyWrap.hidden = empty;
  ui.clearHistory.disabled = empty;

  const rows = entries.map((entry) => {
    const row = document.createElement("tr");

    const time = document.createElement("td");
    time.textContent = localTime(entry.at);

    const payment = document.createElement("td");
    payment.className = "mono";
    payment.textContent = entry.paymentRef;

    const status = document.createElement("td");
    status.textContent = String(entry.status);

    const outcome = document.createElement("td");
    outcome.textContent = entry.outcome;

    const reference = document.createElement("td");
    reference.className = "mono";
    reference.textContent = entry.reference || "—";

    row.append(time, payment, status, outcome, reference);
    return row;
  });
  ui.historyBody.replaceChildren(...rows);
}

function updateCredentialState() {
  if (!tokenClient) return;
  const token = tokenClient.token();

  if (token) {
    const expiresAt = tokenClient.expiresAtMillis();
    ui.badge.textContent = "dev stub credential · memory only";
    ui.badge.className = "badge badge-ok";
    ui.detail.textContent = expiresAt
      ? "Held in memory; expires at " + new Date(expiresAt).toLocaleTimeString() + "."
      : "Held in memory.";
    ui.forget.disabled = false;
  } else {
    ui.badge.textContent = "No credential";
    ui.badge.className = "badge badge-neutral";
    ui.detail.textContent = "Not acquired.";
    ui.forget.disabled = true;
  }
}

function showFormError(message) {
  ui.formError.textContent = message;
  ui.formError.hidden = false;
}

function hideFormError() {
  ui.formError.hidden = true;
  ui.formError.textContent = "";
}

function showGlobal(tone, message) {
  ui.globalNotice.hidden = false;
  ui.globalNotice.className = "notice " + noticeClass(tone);
  ui.globalNotice.textContent = message;
}

function noticeClass(tone) {
  switch (tone) {
    case "ok": return "notice-ok";
    case "error": return "notice-danger";
    case "warn": return "notice-warn";
    default: return "notice-info";
  }
}

function badgeClass(tone) {
  switch (tone) {
    case "ok": return "badge-ok";
    case "warn": return "badge-warn";
    case "danger": return "badge-danger";
    default: return "badge-neutral";
  }
}

function messageOf(error) {
  return error && error.message ? error.message : String(error);
}
