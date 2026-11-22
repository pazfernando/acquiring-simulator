/*
 * Presenter console client for the local-only acquiring simulator relay.
 *
 * Boundary rules implemented here (channel-contract C1/C3/C6/C7, observation-contract O5a/O6):
 *  - The browser never holds a JWT, access code, key or token in storage, URLs or logs. The relay
 *    session is an opaque HttpOnly cookie; this file only reads session metadata from JSON.
 *  - The purchase form sends exactly the closed C1 field set. Identity, decision, route,
 *    generation, actor and fault are not representable.
 *  - A payment POST happens only on an explicit user submit. Reconnect, catch-up, history,
 *    resync and heartbeats never POST.
 *  - Live frames are accepted only after a matching subscribe ACK; pre-ACK frames are buffered,
 *    never rendered. Duplicates are dropped, gaps trigger HTTP catch-up, and an expired or
 *    changed snapshot requires an explicit resync instead of a silent jump to the newest event.
 *  - Every server-supplied string is rendered as inert text (textContent), never as markup.
 */
(() => {
  "use strict";

  /* ------------------------------------------------------------------ */
  /* Constants                                                          */
  /* ------------------------------------------------------------------ */

  const API = Object.freeze({
    session: "/presenter/v1/session",
    submit: "/console/v1/authorizations",
    history: "/console/v1/transactions",
    detail: (id) => `/console/v1/transactions/${encodeURIComponent(id)}`,
    events: (id) => `/console/v1/transactions/${encodeURIComponent(id)}/events`,
    live: () => `${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/presenter/v1/live`,
  });

  const LIMITS = Object.freeze({
    requestTimeoutMs: 10000,
    ackTimeoutMs: 10000,
    heartbeatMs: 30000,
    syncWatchdogMs: 45000,
    reconnectDelaysMs: [1000, 2000, 5000, 10000, 30000],
    pageEvents: 100,
    passEvents: 1000,
    passBytes: 1000 * 1000,
    bufferEvents: 1000,
    bufferBytes: 1000 * 1000,
    maxPages: 10,
    maxTimeline: 1000,
  });

  const ENVELOPE_KEYS = Object.freeze([
    "contractVersion", "authorizationId", "transactionId", "attemptId", "correlationId",
    "originalCorrelationId", "outcome", "disposition", "businessVersion", "asOf",
    "observation", "serverFreshness", "error",
  ]);

  const OUTCOMES = Object.freeze(["APPROVED", "DECLINED", "PROCESSOR_REJECTED", "NOT_CONFIRMED"]);
  const DISPOSITIONS = Object.freeze(["COMPLETED", "PENDING", "REPLAY", "REJECTED", "NOT_DISPATCHED", "UNAVAILABLE"]);
  const OBSERVATIONS = Object.freeze(["COMPLETE", "PARTIAL", "UNKNOWN"]);
  const FRESHNESS = Object.freeze(["CURRENT", "STALE", "UNKNOWN"]);
  const ERRORS = Object.freeze([
    "UNAUTHENTICATED", "CONTEXT_DENIED", "INVALID_INPUT", "UNSUPPORTED_MEDIA", "BODY_TOO_LARGE",
    "PAYLOAD_CONFLICT", "INVALID_REFERENCE", "NOT_FOUND", "EVENT_HISTORY_UNAVAILABLE",
    "LIVE_NOTIFICATIONS_UNAVAILABLE", "LIVE_DELIVERY_LAG", "RESERVED_UNSENT",
    "BUSINESS_ADMISSION_UNAVAILABLE", "EVENT_CAPACITY", "BUSINESS_JOURNAL_UNAVAILABLE",
  ]);
  const EVENT_STATUSES = Object.freeze(["STARTED", "SUCCEEDED", "FAILED", "SKIPPED"]);
  const AUTHORITIES = Object.freeze(["JOURNAL_AND_EVENTS", "LAST_RECORDED"]);

  const REFERENCE_PATTERN = /^[A-Za-z0-9._:-]+$/;
  const AMOUNT_PATTERN = /^(0|[1-9][0-9]{0,3})\.[0-9]{2}$/;
  const ASCII_PATTERN = /^[\x20-\x7E]*$/;

  const REFERENCE_MAX = 128;
  const CORRELATION_MAX = 64;
  const CREDENTIAL_MAX = 64;
  const PRIOR_MAX = 64;

  /* ------------------------------------------------------------------ */
  /* State                                                              */
  /* ------------------------------------------------------------------ */

  const state = {
    session: null,
    sessionTimer: null,
    sessionPoll: null,
    environmentAvailable: true,
    submitInFlight: false,
    lastFingerprint: null,
    duplicateArmed: false,
    lastResult: null,
    lastResultReceivedAt: null,
    activeTransactionId: null,
    counters: {
      submissions: 0,
      catchups: 0,
      reconnects: 0,
      framesApplied: 0,
      framesDuplicate: 0,
      framesDiscarded: 0,
      history: 0,
    },
    history: {
      items: [],
      nextCursor: null,
      loading: false,
      selected: null,
      detail: null,
    },
  };

  const live = {
    ws: null,
    status: "UNKNOWN", // UNKNOWN | CONNECTING | CONNECTED | DISCONNECTED
    epoch: null,
    readyEnvironment: null,
    followTransactionId: null,
    pending: null,
    active: null,
    buffered: [],
    outOfOrder: new Map(),
    seen: new Map(), // `${producerId}:${eventId}` -> eventSha256
    sequenceHash: new Map(),
    events: [],
    covered: 0,
    snapshotHead: 0,
    expiryRevision: 0,
    firstRetained: 0,
    expiredRanges: [],
    gapRanges: [],
    completeThroughSnapshot: false,
    retentionTruncated: false,
    catchingUp: false,
    catchUpAgain: false,
    catchUpHandle: null,
    reconnectAttempt: 0,
    reconnectTimer: null,
    heartbeatTimer: null,
    tickTimer: null,
    lastSyncSentAt: null,
    lastSyncOkAt: null,
    resyncRequired: false,
    integrityGap: false,
    closing: false,
  };

  /* ------------------------------------------------------------------ */
  /* DOM helpers                                                        */
  /* ------------------------------------------------------------------ */

  const $ = (id) => document.getElementById(id);

  const nodes = {};

  function cacheNodes() {
    const ids = [
      "global-notice", "fixture-badge", "session-badge", "signout-button",
      "login-section", "login-form", "access-code", "login-error", "login-button",
      "app", "identity-mode-badge", "ctx-environment", "ctx-acquirer", "ctx-merchant",
      "ctx-terminal", "ctx-session-expiry",
      "purchase-form", "payment-ref", "correlation-id", "correlation-generate",
      "credential-ref", "amount", "entry-mode", "cvm", "risk", "prior-authorization",
      "cryptogram", "form-error", "form-warning", "submit-button", "new-operation-button",
      "result-status-badge", "result-empty", "result-body", "result-outcome",
      "result-disposition", "result-caveat", "res-authorization", "res-transaction",
      "res-attempt", "res-correlation", "res-original-correlation", "res-business-version",
      "res-observation", "res-freshness", "res-error", "res-asof", "res-http", "res-received",
      "history-refresh", "history-status", "history-rows", "history-more", "history-detail",
      "det-authority", "det-journal-outcome", "det-journal-version", "det-journal-asof",
      "det-last-recorded", "det-known-head", "det-covered", "det-truncated", "det-ranges",
      "det-authority-note", "detail-follow", "detail-close",
      "live-badge", "live-transaction", "live-generation", "live-snapshot-head", "live-covered",
      "live-first-retained", "live-expiry-revision", "live-note", "live-ranges", "live-alert",
      "resync-button", "unsubscribe-button", "timeline", "timeline-empty",
      "counter-submissions", "counter-catchups", "counter-reconnects", "counter-frames-applied",
      "counter-frames-duplicate", "counter-frames-discarded", "counter-history",
    ];
    for (const id of ids) {
      nodes[id] = $(id);
    }
  }

  function setText(node, value) {
    if (!node) return;
    node.textContent = value === null || value === undefined || value === "" ? "—" : String(value);
  }

  function clearText(node) {
    if (node) node.textContent = "";
  }

  function show(node, visible) {
    if (node) node.hidden = !visible;
  }

  function setBadge(node, text, tone) {
    if (!node) return;
    node.textContent = text;
    node.className = `badge badge-${tone || "neutral"}`;
  }

  function setNotice(node, text, tone) {
    if (!node) return;
    if (!text) {
      node.hidden = true;
      node.textContent = "";
      return;
    }
    node.textContent = text;
    node.className = `notice notice-${tone || "warn"}`;
    node.hidden = false;
  }

  function markInvalid(input, invalid) {
    if (!input) return;
    if (invalid) input.setAttribute("aria-invalid", "true");
    else input.removeAttribute("aria-invalid");
  }

  function clearInvalid(form) {
    if (!form) return;
    for (const input of form.querySelectorAll("[aria-invalid]")) {
      input.removeAttribute("aria-invalid");
    }
  }

  function formatTime(value) {
    if (typeof value !== "string" || value.length === 0) return "—";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    try {
      return new Intl.DateTimeFormat("es", { dateStyle: "medium", timeStyle: "medium" }).format(date);
    } catch {
      return date.toISOString();
    }
  }

  function formatAge(milliseconds) {
    const seconds = Math.max(0, Math.round(milliseconds / 1000));
    if (seconds < 60) return `hace ${seconds} s`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `hace ${minutes} min`;
    const hours = Math.floor(minutes / 60);
    return `hace ${hours} h`;
  }

  function formatRanges(ranges) {
    if (!Array.isArray(ranges) || ranges.length === 0) return "ninguna";
    const parts = [];
    for (const range of ranges) {
      if (Array.isArray(range) && range.length >= 2) {
        parts.push(`${range[0]}–${range[1]}`);
      } else if (isObject(range) && typeof range.from === "number" && typeof range.to === "number") {
        parts.push(`${range.from}–${range.to}`);
      }
    }
    return parts.length > 0 ? parts.join(", ") : "ninguna";
  }

  function isObject(value) {
    return value !== null && typeof value === "object" && !Array.isArray(value);
  }

  function asString(value, max = 512) {
    return typeof value === "string" && value.length <= max ? value : null;
  }

  function asCounter(value) {
    return typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? value : null;
  }

  function enumOrUnknown(value, allowed) {
    return typeof value === "string" && allowed.includes(value) ? value : "DESCONOCIDO";
  }

  function byteLength(text) {
    return new TextEncoder().encode(text).length;
  }

  function randomId(length) {
    const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    const output = [];
    if (globalThis.crypto && typeof globalThis.crypto.getRandomValues === "function") {
      const bytes = new Uint8Array(length);
      globalThis.crypto.getRandomValues(bytes);
      for (const byte of bytes) output.push(alphabet[byte % alphabet.length]);
      return output.join("");
    }
    for (let index = 0; index < length; index += 1) {
      output.push(alphabet[Math.floor(Math.random() * alphabet.length)]);
    }
    return output.join("");
  }

  /* ------------------------------------------------------------------ */
  /* HTTP transport                                                     */
  /* ------------------------------------------------------------------ */

  async function api(path, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? LIMITS.requestTimeoutMs);
    const headers = { Accept: "application/json" };
    const method = options.method ?? "GET";
    if (method !== "GET" && method !== "HEAD") headers["X-Presenter-Request"] = "1";
    if (options.body !== undefined) headers["Content-Type"] = "application/json";
    try {
      const response = await fetch(path, {
        method,
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        credentials: "same-origin",
        cache: "no-store",
        redirect: "error",
        signal: controller.signal,
      });
      const text = await response.text();
      let payload = null;
      if (text.length > 0) {
        try {
          payload = JSON.parse(text);
        } catch {
          payload = null;
        }
      }
      return { status: response.status, payload, text };
    } catch (error) {
      return { status: 0, payload: null, text: "", transportError: error };
    } finally {
      clearTimeout(timeout);
    }
  }

  /* ------------------------------------------------------------------ */
  /* Session                                                            */
  /* ------------------------------------------------------------------ */

  function isSessionPayload(payload) {
    return isObject(payload)
      && asString(payload.identityMode, 64) !== null
      && asString(payload.environmentId, 64) !== null
      && isObject(payload.context)
      && asString(payload.context.acquirerRef, REFERENCE_MAX) !== null
      && asString(payload.context.merchantRef, REFERENCE_MAX) !== null
      && asString(payload.context.terminalRef, REFERENCE_MAX) !== null;
  }

  function applySession(payload) {
    state.session = {
      identityMode: asString(payload.identityMode, 64),
      subject: asString(payload.subject, 64),
      environmentId: asString(payload.environmentId, 64),
      context: {
        acquirerRef: asString(payload.context.acquirerRef, REFERENCE_MAX),
        merchantRef: asString(payload.context.merchantRef, REFERENCE_MAX),
        terminalRef: asString(payload.context.terminalRef, REFERENCE_MAX),
      },
      expiresAt: asString(payload.expiresAt, 64),
    };

    show(nodes["login-section"], false);
    show(nodes["app"], true);
    show(nodes["signout-button"], true);
    setBadge(nodes["session-badge"], "Sesión local activa", "ok");
    setBadge(nodes["fixture-badge"], `Fixture de identidad · ${state.session.identityMode}`, "warn");
    show(nodes["fixture-badge"], true);
    setBadge(nodes["identity-mode-badge"], state.session.identityMode, "warn");
    show(nodes["identity-mode-badge"], true);

    setText(nodes["ctx-environment"], state.session.environmentId);
    setText(nodes["ctx-acquirer"], state.session.context.acquirerRef);
    setText(nodes["ctx-merchant"], state.session.context.merchantRef);
    setText(nodes["ctx-terminal"], state.session.context.terminalRef);
    setText(nodes["ctx-session-expiry"], formatTime(state.session.expiresAt));

    scheduleSessionExpiry();
    startSessionPoll();
    resetForm({ keepContext: true });
    renderCounters();
    setLiveNote("Transporte en vivo: preparando conexión (opcional; el envío HTTP no depende de él).");
    connectLive();
  }

  function clearSessionState() {
    stopSessionTimers();
    closeLive({ silent: true });
    state.session = null;
    state.activeTransactionId = null;
    state.lastResult = null;
    state.lastResultReceivedAt = null;
    state.lastFingerprint = null;
    state.duplicateArmed = false;
    state.history.items = [];
    state.history.nextCursor = null;
    state.history.selected = null;
    state.history.detail = null;

    show(nodes["app"], false);
    show(nodes["login-section"], true);
    show(nodes["signout-button"], false);
    setBadge(nodes["session-badge"], "Sin sesión", "neutral");
    show(nodes["fixture-badge"], false);
    nodes["access-code"].value = "";
    renderResult(null);
    renderTimeline();
    renderLiveMeta();
    renderHistoryRows([]);
    show(nodes["history-detail"], false);
  }

  function scheduleSessionExpiry() {
    stopSessionTimers();
    const expiresAt = state.session && state.session.expiresAt ? Date.parse(state.session.expiresAt) : NaN;
    if (Number.isNaN(expiresAt)) {
      startSessionPoll();
      return;
    }
    const remaining = expiresAt - Date.now();
    if (remaining <= 0) {
      expireSession("La sesión local ya expiró.");
      return;
    }
    state.sessionTimer = setTimeout(() => {
      expireSession("La sesión local expiró. Vuelva a iniciar sesión de demostración.");
    }, Math.min(remaining, 2147483647));
    startSessionPoll();
  }

  function startSessionPoll() {
    stopSessionPoll();
    state.sessionPoll = setInterval(async () => {
      if (!state.session) return;
      const response = await api(API.session);
      if (response.status === 401) {
        expireSession("La sesión local ya no es válida.");
      } else if (response.status === 200 && isSessionPayload(response.payload)) {
        const nextExpiry = asString(response.payload.expiresAt, 64);
        if (nextExpiry !== state.session.expiresAt) {
          state.session.expiresAt = nextExpiry;
          setText(nodes["ctx-session-expiry"], formatTime(nextExpiry));
          scheduleSessionExpiry();
        }
      }
    }, 60000);
  }

  function stopSessionTimer() {
    if (state.sessionTimer) {
      clearTimeout(state.sessionTimer);
      state.sessionTimer = null;
    }
  }

  function stopSessionPoll() {
    if (state.sessionPoll) {
      clearInterval(state.sessionPoll);
      state.sessionPoll = null;
    }
  }

  function stopSessionTimers() {
    stopSessionTimer();
    stopSessionPoll();
  }

  function expireSession(message) {
    const hadSession = state.session !== null;
    clearSessionState();
    if (hadSession) {
      setNotice(nodes["global-notice"], message, "warn");
      clearText(nodes["login-error"]);
      nodes["access-code"].focus();
    }
  }

  async function detectSession() {
    const response = await api(API.session);
    if (response.status === 200 && isSessionPayload(response.payload)) {
      applySession(response.payload);
      return;
    }
    if (response.status === 401) {
      clearSessionState();
      return;
    }
    if (response.status === 0) {
      state.environmentAvailable = false;
      showUnavailable("No se pudo contactar el relay local. La consola no inventa sesión ni resultados.");
      return;
    }
    state.environmentAvailable = false;
    showUnavailable(
      "El relay local no expone todavía la sesión de presentación (endpoint ausente). " +
      "La interfaz no simula una sesión ni un resultado."
    );
  }

  function showUnavailable(message) {
    setNotice(nodes["global-notice"], message, "warn");
    nodes["login-button"].disabled = true;
    nodes["access-code"].disabled = true;
  }

  async function signIn(event) {
    event.preventDefault();
    const code = nodes["access-code"].value;
    clearText(nodes["login-error"]);
    show(nodes["login-error"], false);
    if (typeof code !== "string" || code.length < 16) {
      setText(nodes["login-error"], "El código de acceso debe tener al menos 16 caracteres.");
      show(nodes["login-error"], true);
      nodes["access-code"].focus();
      return;
    }
    nodes["login-button"].disabled = true;
    nodes["login-button"].textContent = "Entrando…";
    const response = await api(API.session, { method: "POST", body: { accessCode: code } });
    nodes["login-button"].disabled = false;
    nodes["login-button"].textContent = "Entrar";

    if (response.status === 200 && isSessionPayload(response.payload)) {
      nodes["access-code"].value = "";
      setNotice(nodes["global-notice"], "", "warn");
      applySession(response.payload);
      return;
    }
    if (response.status === 401) {
      setText(nodes["login-error"], "Código de acceso incorrecto.");
      show(nodes["login-error"], true);
      nodes["access-code"].select();
      return;
    }
    if (response.status === 429) {
      setText(nodes["login-error"], "Demasiados intentos. Espere y vuelva a intentar.");
      show(nodes["login-error"], true);
      return;
    }
    if (response.status === 0) {
      setText(nodes["login-error"], "No se pudo contactar el relay local.");
      show(nodes["login-error"], true);
      return;
    }
    setText(nodes["login-error"], "El relay local no pudo iniciar la sesión de demostración.");
    show(nodes["login-error"], true);
  }

  async function signOut() {
    const response = await api(API.session, { method: "DELETE" });
    if (response.status === 0) {
      setNotice(nodes["global-notice"], "No se pudo contactar el relay para cerrar la sesión local.", "warn");
    }
    clearSessionState();
    setBadge(nodes["session-badge"], "Sin sesión", "neutral");
    nodes["access-code"].focus();
  }

  /* ------------------------------------------------------------------ */
  /* Closed C1 form                                                     */
  /* ------------------------------------------------------------------ */

  function resetForm(options = {}) {
    const form = nodes["purchase-form"];
    clearInvalid(form);
    show(nodes["form-error"], false);
    show(nodes["form-warning"], false);
    state.lastFingerprint = null;
    state.duplicateArmed = false;
    state.lastResult = null;
    state.lastResultReceivedAt = null;
    renderResult(null);
    if (!options.keepContext) {
      detachLiveTransaction();
    }
    nodes["payment-ref"].value = `PAY-${randomId(10)}`;
    nodes["correlation-id"].value = `CORR-${randomId(12)}`;
    nodes["credential-ref"].value = "SIM-APP-001";
    nodes["amount"].value = "12.34";
    nodes["entry-mode"].value = "simulated-contact";
    nodes["cvm"].value = "simulated-verified";
    nodes["risk"].value = "";
    nodes["prior-authorization"].value = "";
    nodes["cryptogram"].checked = false;
    nodes["submit-button"].disabled = false;
    nodes["submit-button"].textContent = "Enviar autorización";
  }

  function readReference(input, label, max, required, problems) {
    const raw = input.value;
    const value = typeof raw === "string" ? raw.trim() : "";
    if (value.length === 0) {
      if (required) {
        problems.push(`${label} es obligatorio.`);
        markInvalid(input, true);
      }
      return null;
    }
    if (value.length > max) {
      problems.push(`${label} no puede superar ${max} caracteres.`);
      markInvalid(input, true);
      return null;
    }
    if (!ASCII_PATTERN.test(value) || !REFERENCE_PATTERN.test(value)) {
      problems.push(`${label} solo admite A–Z a–z 0–9 . _ : -`);
      markInvalid(input, true);
      return null;
    }
    return value;
  }

  function buildSubmission() {
    clearInvalid(nodes["purchase-form"]);
    const problems = [];
    const context = state.session && state.session.context;

    const paymentRef = readReference(nodes["payment-ref"], "La referencia de pago", REFERENCE_MAX, true, problems);
    const correlationId = readReference(nodes["correlation-id"], "La correlación", CORRELATION_MAX, true, problems);
    const credentialFixtureRef = readReference(
      nodes["credential-ref"], "La referencia de credencial", CREDENTIAL_MAX, true, problems);
    const priorAuthorizationId = readReference(
      nodes["prior-authorization"], "La autorización previa", PRIOR_MAX, false, problems);

    const amountText = nodes["amount"].value.trim();
    let amountOk = AMOUNT_PATTERN.test(amountText);
    if (amountOk) {
      const [integral, fraction] = amountText.split(".");
      const minor = Number.parseInt(integral, 10) * 100 + Number.parseInt(fraction, 10);
      if (minor < 100 || minor > 999999) {
        problems.push("El importe debe estar entre 1.00 y 9999.99.");
        markInvalid(nodes["amount"], true);
        amountOk = false;
      }
    } else {
      problems.push("El importe debe tener el formato 1.00–9999.99, con dos decimales y sin ceros iniciales.");
      markInvalid(nodes["amount"], true);
    }

    const entryMode = nodes["entry-mode"].value;
    const cvm = nodes["cvm"].value;
    const risk = nodes["risk"].value;

    if (!["simulated-contact", "simulated-contactless"].includes(entryMode)) {
      problems.push("Seleccione un modo de entrada admitido.");
      markInvalid(nodes["entry-mode"], true);
    }
    if (!["simulated-verified", "simulated-not-required"].includes(cvm)) {
      problems.push("Seleccione una verificación del titular admitida.");
      markInvalid(nodes["cvm"], true);
    }
    if (risk !== "" && !["pass", "reject"].includes(risk)) {
      problems.push("El indicador de riesgo solo admite pass o reject.");
      markInvalid(nodes["risk"], true);
    }

    if (problems.length > 0) {
      setText(nodes["form-error"], problems[0]);
      show(nodes["form-error"], true);
      return null;
    }

    const body = {
      contractVersion: "1",
      operation: "A",
      correlationId,
      acquirerRef: context.acquirerRef,
      merchantRef: context.merchantRef,
      terminalRef: context.terminalRef,
      paymentRef,
      credentialFixtureRef,
      amount: amountText,
      currency: "USD",
      entryMode,
      cvm,
      cryptogramPresent: nodes["cryptogram"].checked,
    };
    if (risk !== "") body.risk = risk;
    if (priorAuthorizationId !== null) body.priorAuthorizationId = priorAuthorizationId;

    if (byteLength(JSON.stringify(body)) > 4096) {
      setText(nodes["form-error"], "La solicitud supera el límite C1 de 4096 bytes.");
      show(nodes["form-error"], true);
      return null;
    }
    return body;
  }

  function fingerprint(body) {
    return JSON.stringify(body);
  }

  function armDuplicate(body) {
    state.duplicateArmed = true;
    state.lastFingerprint = fingerprint(body);
    setText(nodes["form-warning"], "Reenvío deliberado armado: se enviará exactamente el mismo cuerpo y la correlación. " +
      "El servidor puede responder con un replay; no es un pago nuevo.");
    show(nodes["form-warning"], true);
    nodes["submit-button"].textContent = "Reenviar duplicado (deliberado)";
  }

  async function submitPurchase(event) {
    event.preventDefault();
    if (!state.session || state.submitInFlight) return;

    const body = buildSubmission();
    if (!body) return;

    const bodyFingerprint = fingerprint(body);
    if (bodyFingerprint === state.lastFingerprint && state.lastResult !== null && !state.duplicateArmed) {
      armDuplicate(body);
      return;
    }
    state.duplicateArmed = false;
    show(nodes["form-warning"], false);
    show(nodes["form-error"], false);

    state.submitInFlight = true;
    state.counters.submissions += 1;
    renderCounters();
    nodes["submit-button"].disabled = true;
    nodes["submit-button"].textContent = "Enviando…";

    let response;
    try {
      response = await api(API.submit, { method: "POST", body, timeoutMs: 15000 });
    } finally {
      state.submitInFlight = false;
      nodes["submit-button"].disabled = false;
      nodes["submit-button"].textContent = "Enviar autorización";
    }

    state.lastFingerprint = bodyFingerprint;

    if (response.status === 401) {
      expireSession("La sesión local expiró antes de procesar el envío.");
      return;
    }

    if (response.status === 0) {
      renderUncertainSubmit(body, "La conexión falló o expiró el tiempo de espera. El resultado del envío es desconocido.");
      return;
    }

    const payload = response.payload;
    if (isObject(payload) && isEnvelope(payload)) {
      renderResult(payload, response.status);
      state.lastResult = payload;
      state.lastResultReceivedAt = Date.now();

      const transactionId = asString(payload.transactionId, REFERENCE_MAX);
      if (transactionId) {
        state.activeTransactionId = transactionId;
        subscribeToTransaction(transactionId, { reset: true });
      }
      if (payload.error === "LIVE_NOTIFICATIONS_UNAVAILABLE" || payload.error === "LIVE_DELIVERY_LAG") {
        setLiveAlert("El servidor informó una limitación de notificaciones en vivo. Use la recuperación HTTP retenida.");
      }
      return;
    }

    if (response.status === 404 || response.status === 501) {
      showUnavailable(
        "El relay local no expone todavía el endpoint C1 de autorizaciones. No se envió ningún pago y la interfaz no inventa un resultado."
      );
      return;
    }

    if (response.status >= 500) {
      renderUncertainSubmit(body, `El relay respondió HTTP ${response.status} sin un envelope C3 reconocible.`);
      return;
    }

    setText(nodes["form-error"], `Respuesta HTTP ${response.status} no reconocida como contrato C3. No se muestra un resultado inventado.`);
    show(nodes["form-error"], true);
  }

  function isEnvelope(payload) {
    if (payload.contractVersion !== "1") return false;
    for (const key of Object.keys(payload)) {
      if (!ENVELOPE_KEYS.includes(key)) return false;
    }
    return true;
  }

  function renderUncertainSubmit(body, detail) {
    // Display-only, browser-built uncertainty. It is deliberately NOT a C3 envelope: no server
    // evaluation time, version, outcome or error code may be invented locally.
    const display = {
      localOnly: true,
      correlationId: asString(body.correlationId, 64),
      note: detail,
    };
    renderResult(display, 0);
    setText(nodes["form-error"], `${detail} No reenvíe automáticamente: consulte el historial por la misma referencia.`);
    show(nodes["form-error"], true);
    state.lastResult = null;
    state.lastResultReceivedAt = null;
  }

  /* ------------------------------------------------------------------ */
  /* C3 result rendering                                                */
  /* ------------------------------------------------------------------ */

  function renderResult(payload, httpStatus) {
    if (!payload) {
      show(nodes["result-body"], false);
      show(nodes["result-empty"], true);
      setBadge(nodes["result-status-badge"], "Sin enviar", "neutral");
      return;
    }

    show(nodes["result-empty"], false);
    show(nodes["result-body"], true);

    if (payload.localOnly === true) {
      _lastHttpStatus = 0;
      setBadge(nodes["result-disposition"], "sin confirmación", "danger");
      setText(nodes["result-outcome"], "Sin confirmación del servidor");
      nodes["result-outcome"].className = "outcome is-unknown";
      setText(nodes["res-authorization"], null);
      setText(nodes["res-transaction"], null);
      setText(nodes["res-attempt"], null);
      setText(nodes["res-correlation"], payload.correlationId);
      setText(nodes["res-original-correlation"], null);
      setText(nodes["res-business-version"], null);
      setText(nodes["res-observation"], "DESCONOCIDO");
      setText(nodes["res-freshness"], "DESCONOCIDO");
      setText(nodes["res-error"], null);
      setText(nodes["res-asof"], null);
      setText(nodes["res-http"], "sin respuesta HTTP");
      setBadge(nodes["result-status-badge"], "Sin respuesta del servidor", "danger");
      setText(
        nodes["result-caveat"],
        "Esta tarjeta la construyó el navegador tras un fallo de transporte; no es un envelope C3 " +
        "ni una evaluación del servidor. El resultado del envío es desconocido."
      );
      show(nodes["result-caveat"], true);
      setText(nodes["res-received"], "—");
      return;
    }

    _lastHttpStatus = httpStatus;

    const outcome = payload.outcome === null ? null : enumOrUnknown(payload.outcome, OUTCOMES);
    const disposition = enumOrUnknown(payload.disposition, DISPOSITIONS);
    const observation = enumOrUnknown(payload.observation, OBSERVATIONS);
    const freshness = enumOrUnknown(payload.serverFreshness, FRESHNESS);
    const error = payload.error === null ? null : enumOrUnknown(payload.error, ERRORS);

    const outcomeText = outcome === null ? "Sin decisión de pago" : outcome;
    const outcomeNode = nodes["result-outcome"];
    outcomeNode.textContent = outcomeText;
    outcomeNode.className = "outcome " + outcomeTone(outcome, disposition);

    const dispositionTone = disposition === "COMPLETED" ? "ok"
      : ["REJECTED", "NOT_DISPATCHED", "UNAVAILABLE"].includes(disposition) ? "danger"
        : "warn";
    setBadge(nodes["result-disposition"], disposition, dispositionTone);

    setText(nodes["res-authorization"], payload.authorizationId);
    setText(nodes["res-transaction"], payload.transactionId);
    setText(nodes["res-attempt"], payload.attemptId);
    setText(nodes["res-correlation"], payload.correlationId);
    setText(nodes["res-original-correlation"], payload.originalCorrelationId);
    setText(nodes["res-business-version"], asCounter(payload.businessVersion));
    setText(nodes["res-observation"], observation);
    setText(nodes["res-freshness"], freshness);
    setText(nodes["res-error"], error);
    setText(nodes["res-asof"], formatTime(payload.asOf));
    setText(nodes["res-http"], httpStatus > 0 ? String(httpStatus) : "sin respuesta HTTP");

    if (httpStatus > 0) {
      const tone = httpStatus === 200 ? "ok"
        : httpStatus === 202 ? "info"
          : httpStatus >= 500 ? "danger" : "warn";
      setBadge(nodes["result-status-badge"], `HTTP ${httpStatus}`, tone);
    } else {
      setBadge(nodes["result-status-badge"], "Resultado desconocido", "danger");
    }

    const caveats = [];
    if (outcome === "NOT_CONFIRMED" || disposition === "PENDING") {
      caveats.push("No confirmado: no implica aprobación ni rechazo; el envío pudo o no haberse realizado.");
    }
    if (disposition === "UNAVAILABLE" || disposition === "NOT_DISPATCHED") {
      caveats.push("No hay confirmación de envío ni de resultado. Consulte el historial; no reenvíe automáticamente.");
    }
    if (disposition === "REPLAY") {
      caveats.push("Replay de una operación ya registrada; no es un nuevo pago.");
    }
    if (observation !== "COMPLETE") {
      caveats.push(`Observación ${observation}: la cobertura de eventos no es completa.`);
    }
    if (freshness !== "CURRENT") {
      caveats.push(`Frescura del servidor ${freshness}: no es certeza de pago.`);
    }
    if (error) {
      caveats.push(`Código de error del contrato: ${error}.`);
    }
    if (live.status === "DISCONNECTED") {
      caveats.push("Transporte en vivo desconectado: esta tarjeta es la última observación recibida, no una autoridad nueva.");
    }
    if (caveats.length > 0) {
      setText(nodes["result-caveat"], caveats.join(" "));
      show(nodes["result-caveat"], true);
    } else {
      show(nodes["result-caveat"], false);
    }

    updateResultAge();
  }

  function outcomeTone(outcome, disposition) {
    if (outcome === "APPROVED") return "is-approved";
    if (outcome === "DECLINED" || outcome === "PROCESSOR_REJECTED") return "is-declined";
    if (disposition === "REJECTED" || disposition === "NOT_DISPATCHED" || disposition === "UNAVAILABLE") {
      return "is-rejected";
    }
    return "is-unknown";
  }

  function updateResultAge() {
    if (state.lastResultReceivedAt === null) {
      if (state.lastResult === null) setText(nodes["res-received"], "—");
      return;
    }
    const age = Date.now() - state.lastResultReceivedAt;
    const stamp = new Date(state.lastResultReceivedAt).toLocaleTimeString("es");
    const stale = age > LIMITS.syncWatchdogMs;
    setText(nodes["res-received"], `${stamp} (${formatAge(age)})${stale ? " · observación local antigua" : ""}`);
  }

  /* ------------------------------------------------------------------ */
  /* Live transport (O5a / O6)                                          */
  /* ------------------------------------------------------------------ */

  function setLiveStatus(status) {
    live.status = status;
    if (status === "CONNECTED") setBadge(nodes["live-badge"], "Conectado", "ok");
    else if (status === "CONNECTING") setBadge(nodes["live-badge"], "Conectando…", "info");
    else if (status === "DISCONNECTED") setBadge(nodes["live-badge"], "Desconectado", "danger");
    else setBadge(nodes["live-badge"], "Desconocido", "warn");
  }

  function setLiveNote(text) {
    setText(nodes["live-note"], text);
  }

  function setLiveAlert(text) {
    if (!text) {
      show(nodes["live-alert"], false);
      nodes["live-alert"].textContent = "";
      return;
    }
    nodes["live-alert"].textContent = text;
    nodes["live-alert"].className = "notice notice-warn";
    show(nodes["live-alert"], true);
  }

  function connectLive() {
    if (!state.session) return;
    if (live.ws && (live.ws.readyState === WebSocket.OPEN || live.ws.readyState === WebSocket.CONNECTING)) return;

    clearTimeout(live.reconnectTimer);
    live.reconnectTimer = null;
    setLiveStatus("CONNECTING");
    setLiveNote("Abriendo transporte en vivo (opcional). Un fallo aquí no bloquea el envío HTTP explícito.");

    let socket;
    try {
      socket = new WebSocket(API.live());
    } catch {
      setLiveStatus("DISCONNECTED");
      scheduleReconnect("No se pudo abrir el WebSocket local.");
      return;
    }
    live.ws = socket;

    socket.addEventListener("message", (messageEvent) => {
      if (typeof messageEvent.data !== "string" || messageEvent.data.length > 65536) {
        state.counters.framesDiscarded += 1;
        renderCounters();
        return;
      }
      let frame;
      try {
        frame = JSON.parse(messageEvent.data);
      } catch {
        state.counters.framesDiscarded += 1;
        renderCounters();
        return;
      }
      handleFrame(frame);
    });

    socket.addEventListener("close", () => {
      if (live.ws !== socket) return;
      live.ws = null;
      onLiveClosed();
    });

    socket.addEventListener("error", () => {
      // A close event follows; nothing else to do here.
    });
  }

  function onLiveClosed() {
    stopHeartbeat();
    live.epoch = null;
    live.readyEnvironment = null;
    live.pending = null;
    live.active = null;
    live.buffered = [];
    live.outOfOrder.clear();
    live.lastSyncSentAt = null;
    live.lastSyncOkAt = null;
    setLiveStatus("DISCONNECTED");
    renderLiveMeta();
    setLiveNote("Transporte en vivo desconectado. La fuente retenida sigue disponible por HTTP; no se inicia ningún pago.");
    if (state.session && !live.closing) {
      scheduleReconnect("Reconectando con espera creciente; la recuperación usa la fuente retenida.");
    }
  }

  function scheduleReconnect(note) {
    if (live.reconnectTimer) return;
    const index = Math.min(live.reconnectAttempt, LIMITS.reconnectDelaysMs.length - 1);
    const base = LIMITS.reconnectDelaysMs[index];
    const jitter = 0.8 + Math.random() * 0.4;
    const delay = Math.round(base * jitter);
    live.reconnectAttempt += 1;
    state.counters.reconnects += 1;
    renderCounters();
    if (note) setLiveNote(`${note} Próximo intento en ${Math.round(delay / 1000)} s.`);
    live.reconnectTimer = setTimeout(() => {
      live.reconnectTimer = null;
      connectLive();
    }, delay);
  }

  function closeLive(options = {}) {
    live.closing = true;
    clearTimeout(live.reconnectTimer);
    live.reconnectTimer = null;
    stopHeartbeat();
    if (live.ws) {
      try {
        live.ws.close(1000, "client-close");
      } catch {
        // Ignore: the socket may already be closed.
      }
    }
    live.ws = null;
    live.epoch = null;
    live.pending = null;
    live.active = null;
    live.buffered = [];
    live.outOfOrder.clear();
    live.followTransactionId = null;
    live.resyncRequired = false;
    live.integrityGap = false;
    live.covered = 0;
    live.events = [];
    live.seen.clear();
    live.sequenceHash.clear();
    live.snapshotHead = 0;
    live.expiryRevision = 0;
    live.firstRetained = 0;
    live.expiredRanges = [];
    live.gapRanges = [];
    live.completeThroughSnapshot = false;
    live.retentionTruncated = false;
    live.reconnectAttempt = 0;
    setLiveStatus("UNKNOWN");
    renderLiveMeta();
    renderTimeline();
    if (!options.silent) {
      setLiveNote("Sin transporte ni transacción seguida.");
    }
    live.closing = false;
  }

  function sendFrame(frame) {
    if (!live.ws || live.ws.readyState !== WebSocket.OPEN) return false;
    try {
      live.ws.send(JSON.stringify(frame));
      return true;
    } catch {
      return false;
    }
  }

  function startHeartbeat() {
    stopHeartbeat();
    live.heartbeatTimer = setInterval(() => {
      if (live.ws && live.ws.readyState === WebSocket.OPEN && live.active) {
        if (document.hidden) return;
        if (sendFrame({ version: 1, action: "sync" })) {
          live.lastSyncSentAt = Date.now();
        }
      }
    }, LIMITS.heartbeatMs);

    live.tickTimer = setInterval(() => {
      updateResultAge();
      if (live.lastSyncSentAt !== null && live.lastSyncOkAt !== null
        && live.lastSyncSentAt - live.lastSyncOkAt > LIMITS.syncWatchdogMs) {
        if (live.status === "CONNECTED") {
          setLiveStatus("UNKNOWN");
          setLiveNote("Sin respuesta de sincronización dentro de la ventana: el estado de la conexión es DESCONOCIDO, no un resultado.");
        }
      }
    }, 5000);
  }

  function stopHeartbeat() {
    if (live.heartbeatTimer) {
      clearInterval(live.heartbeatTimer);
      live.heartbeatTimer = null;
    }
    if (live.tickTimer) {
      clearInterval(live.tickTimer);
      live.tickTimer = null;
    }
  }

  function handleFrame(frame) {
    if (!isObject(frame) || frame.version !== 1 || typeof frame.action !== "string") {
      state.counters.framesDiscarded += 1;
      renderCounters();
      return;
    }
    switch (frame.action) {
      case "ready":
        handleReady(frame);
        return;
      case "subscribed":
        handleSubscribed(frame);
        return;
      case "subscriptionRejected":
        handleSubscriptionRejected(frame);
        return;
      case "unsubscribed":
        handleUnsubscribed(frame);
        return;
      case "event":
        handleEventFrame(frame);
        return;
      case "sync":
        handleSync(frame);
        return;
      default:
        state.counters.framesDiscarded += 1;
        renderCounters();
    }
  }

  function handleReady(frame) {
    const epoch = asString(frame.connectionEpoch, 64);
    const environmentId = asString(frame.environmentId, 64);
    if (!epoch || !environmentId) {
      state.counters.framesDiscarded += 1;
      renderCounters();
      return;
    }
    if (state.session && environmentId !== state.session.environmentId) {
      setLiveAlert("El transporte en vivo pertenece a otro ambiente. Se cierra la conexión.");
      try {
        live.ws.close(1000, "environment-mismatch");
      } catch {
        // Ignore.
      }
      return;
    }
    live.epoch = epoch;
    live.readyEnvironment = environmentId;
    live.reconnectAttempt = 0;
    live.lastSyncOkAt = Date.now();
    setLiveStatus("CONNECTED");
    setLiveNote("Sesión de transporte autenticada. La suscripción se instala antes de la instantánea HTTP.");
    startHeartbeat();

    if (live.followTransactionId) {
      subscribeToTransaction(live.followTransactionId, { reset: live.active === null && live.covered === 0 });
    }
  }

  function contextMatches(frameContext, expected) {
    return isObject(frameContext) && isObject(expected)
      && asString(frameContext.acquirerRef, REFERENCE_MAX) === expected.acquirerRef
      && asString(frameContext.merchantRef, REFERENCE_MAX) === expected.merchantRef;
  }

  function handleSubscribed(frame) {
    const pending = live.pending;
    const requestId = asString(frame.requestId, 64);
    const subscriptionId = asString(frame.subscriptionId, 64);
    const transactionId = asString(frame.transactionId, 128);
    const generation = asCounter(frame.generation);
    const epoch = asString(frame.connectionEpoch, 64);
    const environmentId = asString(frame.environmentId, 64);

    if (!pending || !requestId || !subscriptionId || generation === null) {
      state.counters.framesDiscarded += 1;
      renderCounters();
      return;
    }
    if (requestId !== pending.requestId
      || subscriptionId !== pending.subscriptionId
      || transactionId !== pending.transactionId
      || epoch !== live.epoch
      || environmentId !== state.session.environmentId
      || !contextMatches(frame.context, pending.context)) {
      // A late/foreign ACK must never retarget the UI to another transaction.
      state.counters.framesDiscarded += 1;
      renderCounters();
      return;
    }

    clearTimeout(pending.timer);
    live.pending = null;
    live.active = {
      requestId,
      subscriptionId,
      transactionId,
      generation,
      context: { ...pending.context },
      ackHead: asCounter(frame.headSequence) ?? 0,
      expiryRevision: asCounter(frame.expiryRevision) ?? 0,
      firstRetained: asCounter(frame.firstRetainedSequence) ?? 0,
    };
    live.firstRetained = live.active.firstRetained;
    live.expiryRevision = live.active.expiryRevision;

    // Discard buffered pre-ACK frames whose generation does not match the ACK.
    const kept = [];
    for (const buffered of live.buffered) {
      if (buffered.generation === generation) kept.push(buffered);
      else state.counters.framesDiscarded += 1;
    }
    live.buffered = [];
    for (const buffered of kept) applyLiveEvent(buffered);

    setLiveAlert("");
    show(nodes["unsubscribe-button"], true);
    show(nodes["resync-button"], live.resyncRequired);
    setLiveNote(`Suscripción confirmada (generación ${generation}). Recuperando la instantánea retenida.`);
    renderLiveMeta();
    renderCounters();

    catchUp({ after: live.covered, generation, reason: "subscription" });
  }

  function handleSubscriptionRejected(frame) {
    const pending = live.pending;
    const requestId = asString(frame.requestId, 64);
    if (pending && requestId && requestId !== pending.requestId) {
      state.counters.framesDiscarded += 1;
      renderCounters();
      return;
    }
    if (pending && pending.timer) clearTimeout(pending.timer);
    live.pending = null;
    live.active = null;
    live.covered = 0;
    live.events = [];
    live.seen.clear();
    live.sequenceHash.clear();
    show(nodes["unsubscribe-button"], false);
    renderTimeline();
    const code = enumOrUnknown(frame.code, [
      "REQUEST_CONFLICT", "SUBSCRIPTION_CONFLICT", "REQUEST_RETIRED", "STALE_SUBSCRIPTION",
      "CONTROL_LIMIT", "CONTEXT_DENIED", "INVALID_REQUEST",
    ]);
    const message = {
      REQUEST_CONFLICT: "El mismo requestId se usó con otro contenido. No se redirige la vista; use una operación nueva.",
      SUBSCRIPTION_CONFLICT: "La suscripción ya está activa con otro requestId. No se reemplaza automáticamente.",
      REQUEST_RETIRED: "La solicitud fue retirada. Use identificadores nuevos para volver a suscribirse.",
      STALE_SUBSCRIPTION: "La generación solicitada está vencida. Reintente con una suscripción nueva.",
      CONTROL_LIMIT: "Se agotó el límite de controles de esta conexión. Reconecte para obtener una época nueva.",
      CONTEXT_DENIED: "El contexto de la suscripción no corresponde al ámbito de la sesión.",
      INVALID_REQUEST: "La suscripción no cumplió el esquema del contrato.",
    }[code] || "Suscripción rechazada por el servidor.";
    setLiveAlert(`Suscripción rechazada (${code}): ${message}`);
    if (code === "CONTROL_LIMIT") {
      try {
        live.ws.close(1000, "control-limit");
      } catch {
        // Ignore.
      }
    }
    renderLiveMeta();
  }

  function handleUnsubscribed(frame) {
    const active = live.active;
    if (!active) {
      state.counters.framesDiscarded += 1;
      renderCounters();
      return;
    }
    if (asString(frame.requestId, 64) !== active.requestId
      || asString(frame.subscriptionId, 64) !== active.subscriptionId) {
      state.counters.framesDiscarded += 1;
      renderCounters();
      return;
    }
    live.active = null;
    live.followTransactionId = null;
    show(nodes["unsubscribe-button"], false);
    setLiveNote("Suscripción retirada explícitamente. No se inició ningún pago.");
    renderLiveMeta();
  }

  function handleEventFrame(frame) {
    const subscriptionId = asString(frame.subscriptionId, 64);
    const requestId = asString(frame.requestId, 64);
    const transactionId = asString(frame.transactionId, 128);
    const epoch = asString(frame.connectionEpoch, 64);
    const environmentId = asString(frame.environmentId, 64);
    const sourceSequence = asCounter(frame.sourceSequence);
    const eventId = asString(frame.eventId, 64);
    const producerId = asString(frame.producerId, 64);
    const eventSha256 = asString(frame.eventSha256, 64);
    const authorization = asString(frame.authority, 32);

    if (sourceSequence === null || !eventId || !producerId || !subscriptionId || !requestId
      || epoch !== live.epoch || environmentId !== state.session.environmentId
      || transactionId === null || authorization !== "RECORDED_EVENT") {
      state.counters.framesDiscarded += 1;
      renderCounters();
      return;
    }

    if (live.pending) {
      if (requestId !== live.pending.requestId
        || subscriptionId !== live.pending.subscriptionId
        || transactionId !== live.pending.transactionId
        || !contextMatches(frame.context, live.pending.context)) {
        state.counters.framesDiscarded += 1;
        renderCounters();
        return;
      }
      const bufferedBytes = live.buffered.reduce((total, item) => total + byteLength(JSON.stringify(item)), 0);
      if (live.buffered.length >= LIMITS.bufferEvents || bufferedBytes > LIMITS.bufferBytes) {
        live.resyncRequired = true;
        setLiveAlert("El búfer previo al ACK superó el límite. Se requiere una nueva instantánea retenida.");
        renderLiveMeta();
        return;
      }
      live.buffered.push({
        requestId,
        subscriptionId,
        transactionId,
        generation: asCounter(frame.generation),
        sourceSequence,
        eventId,
        producerId,
        eventSha256,
        record: frame.record,
        context: frame.context,
      });
      return;
    }

    if (!live.active) {
      state.counters.framesDiscarded += 1;
      renderCounters();
      return;
    }
    if (requestId !== live.active.requestId
      || subscriptionId !== live.active.subscriptionId
      || transactionId !== live.active.transactionId
      || asCounter(frame.generation) !== live.active.generation
      || !contextMatches(frame.context, live.active.context)) {
      state.counters.framesDiscarded += 1;
      renderCounters();
      return;
    }

    const key = `${producerId}:${eventId}`;
    const knownHash = live.seen.get(key);
    if (knownHash !== undefined) {
      if (knownHash === eventSha256) state.counters.framesDuplicate += 1;
      else {
        live.integrityGap = true;
        setLiveAlert("Conflicto de integridad: el mismo eventId llegó con otro hash. No se sobrescribe; revise la fuente.");
      }
      renderCounters();
      return;
    }
    if (sourceSequence <= live.covered) {
      const sequenceHash = live.sequenceHash.get(sourceSequence);
      if (sequenceHash !== undefined && sequenceHash !== eventSha256) {
        live.integrityGap = true;
        setLiveAlert("Conflicto de integridad de secuencia: dos contenidos distintos para la misma secuencia. No se sobrescribe.");
      } else {
        state.counters.framesDuplicate += 1;
      }
      renderCounters();
      return;
    }

    applyLiveEvent({
      requestId,
      subscriptionId,
      transactionId,
      generation: live.active.generation,
      sourceSequence,
      eventId,
      producerId,
      eventSha256,
      record: frame.record,
      context: frame.context,
      deliveryClass: frame.deliveryClass,
      journalVersion: frame.journalVersion,
      committedAt: frame.committedAt,
      collectedAt: frame.collectedAt,
      storedAt: frame.storedAt,
      stateAfter: frame.stateAfter,
      authority: authorization,
    });

    if (live.outOfOrder.has(sourceSequence)) live.outOfOrder.delete(sourceSequence);
    drainOutOfOrder();

    if (live.covered + 1 < sourceSequence) {
      requestCatchUpSoon("Falta una secuencia contigua; se recupera desde la fuente retenida.");
    }
  }

  function requestCatchUpSoon(reason) {
    if (!live.active || live.catchingUp) {
      live.catchUpAgain = true;
      return;
    }
    if (live.catchUpHandle) return;
    if (reason) setLiveNote(reason);
    live.catchUpHandle = setTimeout(() => {
      live.catchUpHandle = null;
      if (live.active) catchUp({ after: live.covered, generation: live.active.generation, reason: "gap" });
    }, 250);
  }

  function applyLiveEvent(item) {
    if (!item || typeof item.sourceSequence !== "number") return false;
    if (item.sourceSequence <= live.covered) {
      return false;
    }
    if (item.sourceSequence > live.covered + 1) {
      if (live.outOfOrder.size < LIMITS.bufferEvents) live.outOfOrder.set(item.sourceSequence, item);
      return false;
    }

    const key = `${item.producerId}:${item.eventId}`;
    if (live.seen.has(key)) {
      const previous = live.seen.get(key);
      if (previous === item.eventSha256) {
        state.counters.framesDuplicate += 1;
      } else {
        live.integrityGap = true;
        setLiveAlert("Conflicto de integridad en la recuperación: mismo eventId con hash distinto. No se sobrescribe.");
      }
      renderCounters();
      return false;
    }

    live.seen.set(key, item.eventSha256);
    live.sequenceHash.set(item.sourceSequence, item.eventSha256);
    live.events.push(item);
    live.events.sort((left, right) => left.sourceSequence - right.sourceSequence);
    if (live.events.length > LIMITS.maxTimeline) {
      live.events = live.events.slice(live.events.length - LIMITS.maxTimeline);
      live.resyncRequired = true;
      setLiveAlert("La cronología en el navegador superó el límite acotado. Establezca una nueva instantánea desde la fuente retenida.");
      show(nodes["resync-button"], true);
    }
    live.covered = item.sourceSequence;
    state.counters.framesApplied += 1;
    renderCounters();
    renderTimeline();
    renderLiveMeta();
    updateResultCaveatConnection();
    return true;
  }

  function drainOutOfOrder() {
    let progressed = true;
    while (progressed) {
      progressed = false;
      const next = live.outOfOrder.get(live.covered + 1);
      if (next) {
        live.outOfOrder.delete(live.covered + 1);
        if (applyLiveEvent(next)) progressed = true;
      }
    }
  }

  function handleSync(frame) {
    live.lastSyncOkAt = Date.now();
    const hasInventory = Array.isArray(frame.subscriptions);
    const subscriptions = hasInventory ? frame.subscriptions : [];
    if (!live.active) return;
    if (!hasInventory) {
      // A sync frame without a subscription inventory is a source revalidation request, not a
      // statement that the subscription vanished. Re-ask the retained source from the covered
      // cursor: the relay either extends the view or answers 410, which surfaces an explicit
      // resync. Nothing is skipped, invented or resubmitted here.
      setLiveNote("Sincronización sin inventario: se revalida el cursor contra la fuente retenida (no inicia ningún pago).");
      catchUp({ after: live.covered, generation: live.active.generation, reason: "sync-revalidate" });
      return;
    }
    const match = subscriptions.find((entry) => isObject(entry)
      && asString(entry.subscriptionId, 64) === live.active.subscriptionId
      && asCounter(entry.generation) === live.active.generation);
    if (!match) {
      setLiveAlert("El servidor ya no conoce esta suscripción. Reconecte para obtener una época nueva; la vista no se redirige.");
      return;
    }
    const head = asCounter(match.headSequence);
    const expiryRevision = asCounter(match.expiryRevision);
    if (head !== null) live.snapshotHead = Math.max(live.snapshotHead, head);
    if (expiryRevision !== null && live.expiryRevision !== 0 && expiryRevision !== live.expiryRevision) {
      live.resyncRequired = true;
      setLiveAlert("Cambió la revisión de expiración retenida. Se requiere una nueva instantánea (410/RESYNC_REQUIRED).");
      show(nodes["resync-button"], true);
    }
    if (head !== null && head > live.covered) {
      requestCatchUpSoon("La cabeza avanzó más allá de lo cubierto; recuperando por HTTP.");
    }
    renderLiveMeta();
  }

  function subscribeToTransaction(transactionId, options = {}) {
    if (!state.session) return;
    if (!live.ws || live.ws.readyState !== WebSocket.OPEN || !live.epoch) {
      live.followTransactionId = transactionId;
      connectLive();
      return;
    }
    if (live.active && live.active.transactionId === transactionId && !options.reset) {
      return;
    }

    if (live.active && live.active.transactionId !== transactionId) {
      unsubscribeActive();
    }

    if (options.reset) {
      stopFollowingState();
    }

    const context = {
      acquirerRef: state.session.context.acquirerRef,
      merchantRef: state.session.context.merchantRef,
    };
    const requestId = randomId(20);
    const subscriptionId = randomId(24);
    live.pending = {
      requestId,
      subscriptionId,
      transactionId,
      context,
      requestedAt: Date.now(),
      retried: false,
      timer: null,
    };
    live.followTransactionId = transactionId;
    live.buffered = [];
    show(nodes["unsubscribe-button"], false);
    setLiveAlert("");
    setLiveNote("Suscripción enviada. Ningún frame se muestra antes del ACK correspondiente.");
    nodes["live-transaction"].textContent = transactionId;
    renderLiveMeta();
    sendSubscribeFrame(true);
  }

  function sendSubscribeFrame(firstAttempt) {
    const pending = live.pending;
    if (!pending || !state.session) return;
    const sent = sendFrame({
      version: 1,
      action: "subscribe",
      requestId: pending.requestId,
      subscriptionId: pending.subscriptionId,
      environmentId: state.session.environmentId,
      context: { acquirerRef: pending.context.acquirerRef, merchantRef: pending.context.merchantRef },
      transactionId: pending.transactionId,
    });
    if (!sent) {
      setLiveAlert("No hay transporte abierto para suscribirse. El historial HTTP sigue disponible.");
      return;
    }
    clearTimeout(pending.timer);
    pending.timer = setTimeout(() => {
      if (!live.pending || live.pending.requestId !== pending.requestId) return;
      if (!pending.retried) {
        pending.retried = true;
        setLiveNote("ACK no recibido. Se reintenta el MISMO requestId/tupla una vez (recupera el ACK cacheado).");
        sendSubscribeFrame(false);
      } else {
        setLiveAlert("Sin ACK de suscripción tras el reintento idéntico. No se redirige ni se inicia un pago.");
      }
    }, LIMITS.ackTimeoutMs);
    if (firstAttempt) renderLiveMeta();
  }

  function stopFollowingState() {
    live.pending = null;
    live.active = null;
    live.buffered = [];
    live.outOfOrder.clear();
    live.events = [];
    live.seen.clear();
    live.sequenceHash.clear();
    live.covered = 0;
    live.snapshotHead = 0;
    live.expiryRevision = 0;
    live.firstRetained = 0;
    live.expiredRanges = [];
    live.gapRanges = [];
    live.completeThroughSnapshot = false;
    live.retentionTruncated = false;
    live.resyncRequired = false;
    live.integrityGap = false;
    renderTimeline();
    renderLiveMeta();
  }

  function detachLiveTransaction() {
    if (live.active) unsubscribeActive();
    live.followTransactionId = null;
    stopFollowingState();
    show(nodes["unsubscribe-button"], false);
    show(nodes["resync-button"], false);
    setLiveAlert("");
    setLiveNote("Sin transacción seguida.");
  }

  function unsubscribeActive() {
    const active = live.active;
    if (!active || !state.session) return;
    sendFrame({
      version: 1,
      action: "unsubscribe",
      requestId: randomId(20),
      subscriptionId: active.subscriptionId,
      generation: active.generation,
      environmentId: state.session.environmentId,
      context: { acquirerRef: active.context.acquirerRef, merchantRef: active.context.merchantRef },
      transactionId: active.transactionId,
    });
  }

  /* ------------------------------------------------------------------ */
  /* HTTP catch-up (O6)                                                 */
  /* ------------------------------------------------------------------ */

  async function catchUp(options) {
    if (!state.session || !live.active) return;
    if (!live.pending && !live.active) return;
    if (live.catchingUp) {
      live.catchUpAgain = true;
      return;
    }
    live.catchingUp = true;
    live.catchUpAgain = false;
    setLiveNote("Recuperando la instantánea retenida por HTTP (no inicia ningún pago).");

    let after = typeof options.after === "number" ? options.after : live.covered;
    let cursor = null;
    let pages = 0;
    let events = 0;
    let bytes = 0;
    const generation = options.generation ?? live.active.generation;

    try {
      while (pages < LIMITS.maxPages && events < LIMITS.passEvents && bytes < LIMITS.passBytes) {
        pages += 1;
        state.counters.catchups += 1;
        renderCounters();

        const query = new URLSearchParams({
          snapshot: "new",
          after: String(after),
          generation: String(generation),
        });
        if (cursor) query.set("cursor", cursor);

        const response = await api(`${API.events(live.active.transactionId)}?${query.toString()}`);
        if (response.status === 401) {
          expireSession("La sesión local expiró durante la recuperación.");
          return;
        }
        if (response.status === 0) {
          setLiveAlert("No se pudo contactar el relay durante la recuperación. La vista conserva lo ya observado.");
          return;
        }
        if (response.status === 404 || response.status === 501) {
          setLiveAlert("El relay local no expone todavía la recuperación de eventos. Use el historial cuando esté disponible.");
          return;
        }
        if (response.status === 410) {
          handleResyncRequired(response.payload);
          return;
        }
        if (response.status === 403 || response.status === 400) {
          const code = isObject(response.payload) && typeof response.payload.code === "string"
            ? response.payload.code : `HTTP ${response.status}`;
          setLiveAlert(`La recuperación fue rechazada (${code}). No se reinicia el cursor silenciosamente.`);
          return;
        }
        if (response.status !== 200 || !isObject(response.payload)) {
          setLiveAlert(`Recuperación no disponible (HTTP ${response.status}).`);
          return;
        }

        const payload = response.payload;
        if (payload.version !== 1
          || payload.transactionId !== live.active.transactionId
          || asCounter(payload.snapshotHead) === null
          || asCounter(payload.expiryRevision) === null
          || asCounter(payload.firstRetainedSequence) === null
          || !Array.isArray(payload.events)) {
          setLiveAlert("La respuesta de recuperación no cumple el esquema O6. No se aplica.");
          return;
        }

        bytes += byteLength(response.text);

        live.snapshotHead = Math.max(live.snapshotHead, payload.snapshotHead);
        live.expiryRevision = payload.expiryRevision;
        live.firstRetained = payload.firstRetainedSequence;
        live.expiredRanges = Array.isArray(payload.expiredRanges) ? payload.expiredRanges : [];
        live.gapRanges = Array.isArray(payload.gapRanges) ? payload.gapRanges : [];
        live.retentionTruncated = payload.retentionTruncated === true;

        for (const raw of payload.events) {
          const item = normaliseHistoryEvent(raw);
          if (!item) {
            state.counters.framesDiscarded += 1;
            continue;
          }
          events += 1;
          if (item.sourceSequence <= live.covered) {
            if (live.sequenceHash.get(item.sourceSequence) !== undefined
              && live.sequenceHash.get(item.sourceSequence) !== item.eventSha256) {
              live.integrityGap = true;
              setLiveAlert("Conflicto de integridad: distinto contenido para la misma secuencia en la recuperación.");
            } else {
              state.counters.framesDuplicate += 1;
            }
            continue;
          }
          if (item.sourceSequence > live.covered + 1) {
            if (live.outOfOrder.size < LIMITS.bufferEvents) live.outOfOrder.set(item.sourceSequence, item);
            continue;
          }
          applyLiveEvent(item);
          drainOutOfOrder();
        }

        const coveredThrough = asCounter(payload.coveredThrough);
        if (coveredThrough !== null && coveredThrough >= after) after = coveredThrough;
        if (payload.expiryRevision !== null && asCounter(payload.expiryRevision) === null) {
          setLiveAlert("Revisión de expiración ilegible en la recuperación. No se afirma cobertura completa.");
          return;
        }
        if (payload.retentionTruncated === true) {
          setLiveNote("La recuperación indica truncamiento por retención; los rangos perdidos se muestran como brechas explícitas.");
        }
        const complete = payload.completeThroughSnapshot === true;
        live.completeThroughSnapshot = complete;
        cursor = asString(payload.nextCursor, 4096);
        renderLiveMeta();
        if (complete || !cursor) break;
      }

      if (events >= LIMITS.passEvents || bytes >= LIMITS.passBytes) {
        live.resyncRequired = true;
        setLiveAlert("La pasada de recuperación alcanzó el límite acotado (1000 eventos/1 MiB). Se requiere una nueva instantánea.");
        show(nodes["resync-button"], true);
      } else if (live.outOfOrder.size > 0) {
        setLiveNote("Quedaron secuencias no contiguas; se solicitará otra recuperación desde la fuente retenida.");
        requestCatchUpSoon(null);
      } else {
        setLiveNote("Instantánea retenida aplicada. La cronología se actualiza solo desde eventos registrados.");
      }
    } finally {
      live.catchingUp = false;
      renderCounters();
      renderLiveMeta();
      renderTimeline();
      if (live.catchUpAgain) {
        live.catchUpAgain = false;
        if (live.active) requestCatchUpSoon(null);
      }
    }
  }

  function handleResyncRequired(payload) {
    live.resyncRequired = true;
    show(nodes["resync-button"], true);
    const details = isObject(payload) ? payload : {};
    if (asCounter(details.snapshotHead) !== null) live.snapshotHead = details.snapshotHead;
    if (asCounter(details.expiryRevision) !== null) live.expiryRevision = details.expiryRevision;
    if (asCounter(details.firstRetainedSequence) !== null) live.firstRetained = details.firstRetainedSequence;
    if (Array.isArray(details.expiredRanges)) live.expiredRanges = details.expiredRanges;
    if (Array.isArray(details.gapRanges)) live.gapRanges = details.gapRanges;
    const code = typeof details.code === "string" ? details.code : "RESYNC_REQUIRED";
    setLiveAlert(`La instantánea ya no es válida (${code}). No se salta al evento más nuevo: use "Resincronizar" para una instantánea nueva.`);
    renderLiveMeta();
  }

  function normaliseHistoryEvent(raw) {
    if (!isObject(raw)) return null;
    const record = isObject(raw.record) ? raw.record : (isObject(raw.payload) ? raw.payload : null);
    const sourceSequence = asCounter(raw.sourceSequence);
    const eventSha256 = asString(raw.eventSha256, 64);
    if (sourceSequence === null || !eventSha256 || !record) return null;
    const eventId = asString(record.eventId, 64);
    const producerId = asString(record.producerId, 64);
    if (!eventId || !producerId) return null;
    return {
      sourceSequence,
      eventId,
      producerId,
      eventSha256,
      generation: live.active ? live.active.generation : 0,
      requestId: live.active ? live.active.requestId : null,
      subscriptionId: live.active ? live.active.subscriptionId : null,
      transactionId: live.active ? live.active.transactionId : null,
      context: live.active ? { ...live.active.context } : null,
      record,
      authority: "RECORDED_EVENT",
      deliveryClass: asString(raw.deliveryClass, 32),
      journalVersion: asCounter(raw.journalVersion),
      committedAt: asString(raw.committedAt, 64),
      collectedAt: asString(raw.collectedAt, 64),
      storedAt: asString(raw.storedAt, 64),
      stateAfter: asString(raw.stateAfter, 128),
    };
  }

  function resyncFromSource() {
    if (!live.active) return;
    live.covered = 0;
    live.events = [];
    live.seen.clear();
    live.sequenceHash.clear();
    live.outOfOrder.clear();
    live.snapshotHead = 0;
    live.expiryRevision = 0;
    live.firstRetained = 0;
    live.expiredRanges = [];
    live.gapRanges = [];
    live.completeThroughSnapshot = false;
    live.retentionTruncated = false;
    live.resyncRequired = false;
    live.integrityGap = false;
    show(nodes["resync-button"], false);
    setLiveAlert("");
    renderTimeline();
    renderLiveMeta();
    catchUp({ after: 0, generation: live.active.generation, reason: "resync" });
  }

  /* ------------------------------------------------------------------ */
  /* Rendering: timeline and live metadata                              */
  /* ------------------------------------------------------------------ */

  function renderTimeline() {
    const list = nodes["timeline"];
    if (!list) return;
    list.replaceChildren();
    if (live.events.length === 0) {
      show(nodes["timeline-empty"], true);
      return;
    }
    show(nodes["timeline-empty"], false);
    const fragment = document.createDocumentFragment();
    for (const item of live.events) fragment.appendChild(timelineItem(item));
    list.appendChild(fragment);
  }

  function timelineItem(item) {
    const record = isObject(item.record) ? item.record : {};
    const status = enumOrUnknown(record.status, EVENT_STATUSES);
    const listItem = document.createElement("li");
    listItem.className = `timeline-item is-${String(status).toLowerCase()}`;

    const head = document.createElement("div");
    head.className = "timeline-head";

    const task = document.createElement("span");
    task.className = "timeline-task";
    task.textContent = asString(record.taskName, 64) || "tarea sin nombre";
    head.appendChild(task);

    const sequence = document.createElement("span");
    sequence.className = "timeline-seq";
    sequence.textContent = `#${item.sourceSequence} · ${status}`;
    head.appendChild(sequence);
    listItem.appendChild(head);

    const meta = document.createElement("div");
    meta.className = "timeline-meta-line";
    const metaParts = [
      ["actor", asString(record.actor, 32)],
      ["provenance", asString(record.provenance, 32)],
      ["occurredAt", asString(record.occurredAt, 64)],
      ["elapsed", typeof record.elapsedMicros === "number" ? `${record.elapsedMicros} µs` : null],
      ["entrega", item.deliveryClass],
      ["journalVersion", item.journalVersion === null ? null : String(item.journalVersion)],
    ];
    for (const [label, value] of metaParts) {
      if (!value) continue;
      const span = document.createElement("span");
      span.textContent = `${label}: ${value}`;
      meta.appendChild(span);
    }
    listItem.appendChild(meta);

    const details = document.createElement("div");
    details.className = "timeline-detail";
    const detailParts = [
      ["eventId", asString(record.eventId, 64)],
      ["producerId", asString(record.producerId, 64)],
      ["sha256", item.eventSha256],
      ["attemptId", asString(record.attemptId, 64)],
      ["correlationId", asString(record.correlationId, 64)],
      ["reason", asString(record.reason, 64)],
      ["gapCode", asString(record.gapCode, 32)],
      ["stateAfter", item.stateAfter],
      ["authority", item.authority],
    ];
    const pieces = [];
    for (const [label, value] of detailParts) {
      if (!value) continue;
      pieces.push(`${label}=${value}`);
    }
    details.textContent = pieces.join(" · ");
    if (pieces.length > 0) listItem.appendChild(details);
    return listItem;
  }

  function renderLiveMeta() {
    const active = live.active;
    const pending = live.pending;
    setText(nodes["live-transaction"], active ? active.transactionId : (pending ? pending.transactionId : state.activeTransactionId));
    setText(nodes["live-generation"], active ? String(active.generation) : null);
    setText(nodes["live-snapshot-head"], live.snapshotHead > 0 ? String(live.snapshotHead) : null);
    setText(nodes["live-covered"], live.covered > 0 ? String(live.covered) : null);
    setText(nodes["live-first-retained"], live.firstRetained > 0 ? String(live.firstRetained) : null);
    setText(nodes["live-expiry-revision"], live.expiryRevision > 0 ? String(live.expiryRevision) : null);
    show(nodes["unsubscribe-button"], active !== null);
    if (live.resyncRequired) show(nodes["resync-button"], true);

    const rangeParts = [];
    if (live.expiredRanges.length > 0) rangeParts.push(`rangos expirados: ${formatRanges(live.expiredRanges)}`);
    if (live.gapRanges.length > 0) rangeParts.push(`brechas: ${formatRanges(live.gapRanges)}`);
    clearText(nodes["live-ranges"]);
    if (rangeParts.length > 0) {
      nodes["live-ranges"].textContent = rangeParts.join(" · ");
    }
    if (!live.resyncRequired) show(nodes["resync-button"], false);
  }

  function updateResultCaveatConnection() {
    if (state.lastResult) renderResult(state.lastResult, lastHttpStatus());
  }

  let _lastHttpStatus = 0;
  function lastHttpStatus() {
    return _lastHttpStatus;
  }

  /* ------------------------------------------------------------------ */
  /* History                                                            */
  /* ------------------------------------------------------------------ */

  async function loadHistory(options = {}) {
    if (!state.session || state.history.loading) return;
    state.history.loading = true;
    state.counters.history += 1;
    renderCounters();
    setText(nodes["history-status"], "Cargando historial…");

    const query = new URLSearchParams({ window: "7d", limit: "20" });
    if (options.append && state.history.nextCursor) query.set("cursor", state.history.nextCursor);
    const response = await api(`${API.history}?${query.toString()}`);
    state.history.loading = false;

    if (response.status === 401) {
      expireSession("La sesión local expiró al consultar el historial.");
      return;
    }
    if (response.status === 0) {
      setText(nodes["history-status"], "No se pudo contactar el relay. No se muestran datos inventados.");
      return;
    }
    if (response.status === 404 || response.status === 501) {
      setText(nodes["history-status"], "El relay local aún no expone el historial de siete días.");
      renderHistoryRows([]);
      return;
    }
    if (response.status !== 200 || !isObject(response.payload) || !Array.isArray(response.payload.transactions)) {
      setText(nodes["history-status"], `Historial no disponible (HTTP ${response.status}).`);
      return;
    }

    const payload = response.payload;
    const items = [];
    for (const raw of payload.transactions) {
      if (!isObject(raw)) continue;
      items.push({
        authorizationId: asString(raw.authorizationId, 128),
        transactionId: asString(raw.transactionId, 128),
        channelPaymentRef: asString(raw.channelPaymentRef, 128),
        amount: asString(raw.amount, 16),
        currency: asString(raw.currency, 8),
        outcome: raw.outcome === null ? null : enumOrUnknown(raw.outcome, OUTCOMES),
        businessVersion: asCounter(raw.businessVersion),
        asOf: asString(raw.asOf, 64),
        serverFreshness: enumOrUnknown(raw.serverFreshness, FRESHNESS),
        journalReadable: raw.journalReadable === true,
        receivedAt: asString(raw.receivedAt ?? raw.firstReceivedAt, 64),
      });
    }
    state.history.items = options.append ? state.history.items.concat(items) : items;
    state.history.nextCursor = asString(payload.nextCursor, 4096);
    renderHistoryRows(state.history.items);
    show(nodes["history-more"], state.history.nextCursor !== null);
    setText(
      nodes["history-status"],
      `${state.history.items.length} transacción(es) en la ventana de siete días.` +
      (payload.retentionTruncated === true ? " La retención puede truncar rangos antiguos." : "")
    );
  }

  function renderHistoryRows(items) {
    const body = nodes["history-rows"];
    if (!body) return;
    body.replaceChildren();
    if (!items || items.length === 0) {
      const row = document.createElement("tr");
      const cell = document.createElement("td");
      cell.colSpan = 7;
      cell.className = "empty";
      cell.textContent = "Sin transacciones en la ventana.";
      row.appendChild(cell);
      body.appendChild(row);
      return;
    }
    for (const item of items) {
      const row = document.createElement("tr");

      const received = document.createElement("td");
      received.textContent = formatTime(item.receivedAt);
      row.appendChild(received);

      const reference = document.createElement("td");
      reference.className = "mono";
      reference.textContent = item.channelPaymentRef || item.transactionId || "—";
      row.appendChild(reference);

      const amount = document.createElement("td");
      amount.className = "num";
      amount.textContent = item.amount ? `${item.amount} ${item.currency || ""}`.trim() : "—";
      row.appendChild(amount);

      const outcome = document.createElement("td");
      outcome.textContent = item.outcome === null ? "Sin decisión" : item.outcome;
      row.appendChild(outcome);

      const version = document.createElement("td");
      version.className = "num";
      version.textContent = item.businessVersion === null ? "—" : String(item.businessVersion);
      row.appendChild(version);

      const freshness = document.createElement("td");
      freshness.textContent = item.serverFreshness;
      row.appendChild(freshness);

      const actions = document.createElement("td");
      const button = document.createElement("button");
      button.type = "button";
      button.className = "button button-ghost";
      button.textContent = "Detalle";
      button.addEventListener("click", () => openDetail(item.authorizationId || item.transactionId, item));
      actions.appendChild(button);
      row.appendChild(actions);

      body.appendChild(row);
    }
  }

  async function openDetail(id, summary) {
    if (!id) return;
    state.history.selected = id;
    state.counters.history += 1;
    renderCounters();
    show(nodes["history-detail"], true);
    setText(nodes["det-authority"], "cargando…");
    show(nodes["det-authority-note"], false);

    const response = await api(API.detail(id));
    if (response.status === 401) {
      expireSession("La sesión local expiró al consultar el detalle.");
      return;
    }
    if (response.status === 0) {
      setText(nodes["det-authority"], "sin conexión");
      setNotice(nodes["history-status"], "No se pudo contactar el relay para el detalle.", "warn");
      return;
    }
    if (response.status === 404 || response.status === 501) {
      setText(nodes["det-authority"], "no disponible");
      show(nodes["det-authority-note"], true);
      setText(nodes["det-authority-note"], "El relay local aún no expone el detalle del historial.");
      return;
    }
    if (response.status !== 200 || !isObject(response.payload)) {
      setText(nodes["det-authority"], `HTTP ${response.status}`);
      return;
    }

    const payload = response.payload;
    const authority = enumOrUnknown(payload.authority, AUTHORITIES);
    setText(nodes["det-authority"], authority);
    setText(nodes["det-journal-outcome"], payload.journalOutcome === null || payload.journalOutcome === undefined
      ? "—" : enumOrUnknown(payload.journalOutcome, OUTCOMES));
    setText(nodes["det-journal-version"], asCounter(payload.journalVersion));
    setText(nodes["det-journal-asof"], formatTime(payload.journalAsOf ?? null));
    setText(nodes["det-last-recorded"], asString(payload.lastRecordedEventId, 64));
    setText(nodes["det-known-head"], asCounter(payload.knownHead));
    setText(nodes["det-covered"], asCounter(payload.coveredThrough));
    setText(nodes["det-truncated"], payload.retentionTruncated === true ? "sí" : "no");
    setText(
      nodes["det-ranges"],
      `Rangos expirados: ${formatRanges(payload.expiredRanges)} · Brechas de tareas: ${formatRanges(payload.taskGaps)}`
    );

    if (authority === "LAST_RECORDED") {
      setText(
        nodes["det-authority-note"],
        "El journal no está legible: se muestra solo el último registro retenido, sin resultado financiero actual."
      );
      show(nodes["det-authority-note"], true);
    } else if (payload.journalReadable === false) {
      setText(nodes["det-authority-note"], "Journal no legible; los campos de resultado se muestran vacíos a propósito.");
      show(nodes["det-authority-note"], true);
    } else {
      show(nodes["det-authority-note"], false);
    }

    const transactionId = asString(payload.transactionId, 128)
      || (summary && summary.transactionId) || null;
    state.history.detail = { id, transactionId, payload };
    nodes["detail-follow"].disabled = transactionId === null;
  }

  /* ------------------------------------------------------------------ */
  /* Counters                                                           */
  /* ------------------------------------------------------------------ */

  function renderCounters() {
    setText(nodes["counter-submissions"], String(state.counters.submissions));
    setText(nodes["counter-catchups"], String(state.counters.catchups));
    setText(nodes["counter-reconnects"], String(state.counters.reconnects));
    setText(nodes["counter-frames-applied"], String(state.counters.framesApplied));
    setText(nodes["counter-frames-duplicate"], String(state.counters.framesDuplicate));
    setText(nodes["counter-frames-discarded"], String(state.counters.framesDiscarded));
    setText(nodes["counter-history"], String(state.counters.history));
  }

  /* ------------------------------------------------------------------ */
  /* Wiring                                                             */
  /* ------------------------------------------------------------------ */

  function wire() {
    nodes["login-form"].addEventListener("submit", signIn);
    nodes["signout-button"].addEventListener("click", signOut);
    nodes["purchase-form"].addEventListener("submit", submitPurchase);
    nodes["new-operation-button"].addEventListener("click", () => {
      detachLiveTransaction();
      resetForm({ keepContext: true });
    });
    nodes["correlation-generate"].addEventListener("click", () => {
      nodes["correlation-id"].value = `CORR-${randomId(12)}`;
      markInvalid(nodes["correlation-id"], false);
    });
    nodes["history-refresh"].addEventListener("click", () => loadHistory({ append: false }));
    nodes["history-more"].addEventListener("click", () => loadHistory({ append: true }));
    nodes["detail-close"].addEventListener("click", () => {
      show(nodes["history-detail"], false);
      state.history.detail = null;
    });
    nodes["detail-follow"].addEventListener("click", () => {
      const detail = state.history.detail;
      if (!detail || !detail.transactionId) return;
      state.activeTransactionId = detail.transactionId;
      subscribeToTransaction(detail.transactionId, { reset: true });
    });
    nodes["resync-button"].addEventListener("click", resyncFromSource);
    nodes["unsubscribe-button"].addEventListener("click", () => {
      unsubscribeActive();
      live.followTransactionId = null;
      setLiveNote("Retirada solicitada. La cronología conserva lo ya registrado.");
    });

    document.addEventListener("visibilitychange", () => {
      if (!document.hidden && live.ws && live.ws.readyState === WebSocket.OPEN && live.active) {
        sendFrame({ version: 1, action: "sync" });
        live.lastSyncSentAt = Date.now();
      }
    });

    // The form changes invalidate the "same body" duplicate guard.
    for (const input of nodes["purchase-form"].querySelectorAll("input, select")) {
      input.addEventListener("input", () => {
        show(nodes["form-warning"], false);
        state.duplicateArmed = false;
        markInvalid(input, false);
      });
    }
  }

  async function start() {
    cacheNodes();
    wire();
    setLiveStatus("UNKNOWN");
    renderCounters();
    await detectSession();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start, { once: true });
  } else {
    start();
  }
})();
