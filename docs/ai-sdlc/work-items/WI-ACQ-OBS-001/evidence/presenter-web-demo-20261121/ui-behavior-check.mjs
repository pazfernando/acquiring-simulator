/*
 * Local behaviour check for src/main/resources/presenter/app.js.
 *
 * No browser or npm package is installed in this environment, so this script provides a minimal
 * DOM/WebSocket/fetch stub and drives the real app.js file through its recorded event handlers.
 * It verifies observable client behaviour only; it is NOT a browser test and does not verify
 * rendering, layout, CSP enforcement or the (not yet implemented) relay endpoints.
 *
 * Run: node docs/ai-sdlc/work-items/WI-ACQ-OBS-001/evidence/presenter-web-demo-20261121/ui-behavior-check.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";

const repoRoot = process.cwd();
const htmlPath = path.join(repoRoot, "src/main/resources/presenter/index.html");
const appPath = path.join(repoRoot, "src/main/resources/presenter/app.js");

const html = fs.readFileSync(htmlPath, "utf8");
const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map((match) => match[1]);

function openingTag(id) {
  const escaped = id.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = html.match(new RegExp(`<[a-zA-Z][^>]*\\bid="${escaped}"[^>]*>`));
  return match ? match[0] : "";
}

class StubElement {
  constructor(tagName, id = null) {
    this.tagName = tagName.toUpperCase();
    this.id = id;
    this.textContent = "";
    this.hidden = false;
    this.disabled = false;
    this.value = "";
    this.checked = false;
    this.className = "";
    this.children = [];
    this.attributes = new Map();
    this.listeners = new Map();
    this.isFragment = false;
  }

  addEventListener(type, handler) {
    if (!this.listeners.has(type)) this.listeners.set(type, []);
    this.listeners.get(type).push(handler);
  }

  dispatch(type, event = {}) {
    const handlers = this.listeners.get(type) || [];
    const safeEvent = { preventDefault() {}, target: this, ...event };
    for (const handler of handlers) handler(safeEvent);
    return safeEvent;
  }

  appendChild(child) {
    if (child && child.isFragment) {
      this.children.push(...child.children);
    } else {
      this.children.push(child);
    }
    return child;
  }

  replaceChildren(...children) {
    this.children = [];
    for (const child of children) this.appendChild(child);
  }

  setAttribute(name, value) {
    this.attributes.set(name, String(value));
    if (name === "aria-invalid") this.ariaInvalid = true;
  }

  removeAttribute(name) {
    this.attributes.delete(name);
    if (name === "aria-invalid") this.ariaInvalid = false;
  }

  getAttribute(name) {
    return this.attributes.has(name) ? this.attributes.get(name) : null;
  }

  querySelectorAll(selector) {
    if (this.id === "purchase-form" && selector.includes("input")) {
      return ["payment-ref", "correlation-id", "credential-ref", "amount", "entry-mode",
        "cvm", "risk", "prior-authorization", "cryptogram"].map((key) => registry.get(key));
    }
    return [];
  }

  focus() {}
  select() {}
}

const registry = new Map();
for (const id of ids) {
  const element = new StubElement("div", id);
  const tag = openingTag(id);
  element.hidden = /\bhidden\b/.test(tag);
  element.disabled = /\bdisabled\b/.test(tag);
  registry.set(id, element);
}

const documentStub = {
  readyState: "complete",
  getElementById: (id) => registry.get(id) || null,
  createElement: (tagName) => new StubElement(tagName),
  createDocumentFragment: () => {
    const fragment = new StubElement("#fragment");
    fragment.isFragment = true;
    return fragment;
  },
  addEventListener() {},
};

const sockets = [];
class FakeWebSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;

  constructor(url) {
    this.url = url;
    this.readyState = FakeWebSocket.OPEN;
    this.listeners = new Map();
    this.sent = [];
    sockets.push(this);
  }

  addEventListener(type, handler) {
    if (!this.listeners.has(type)) this.listeners.set(type, []);
    this.listeners.get(type).push(handler);
  }

  emit(type, event) {
    for (const handler of this.listeners.get(type) || []) handler(event);
  }

  send(data) {
    this.sent.push(JSON.parse(data));
  }

  close() {
    if (this.readyState === FakeWebSocket.CLOSED) return;
    this.readyState = FakeWebSocket.CLOSED;
    this.emit("close", {});
  }

  serverSend(frame) {
    this.emit("message", { data: JSON.stringify(frame) });
  }
}

/* ----------------------------- HTTP stub ----------------------------- */

const SESSION = {
  identityMode: "LOCAL_TEST_FIXTURE",
  subject: "DEMO-USER",
  environmentId: "local-demo",
  context: {
    acquirerRef: "ACQ-SIM-01",
    merchantRef: "MID-SIM-01",
    terminalRef: "TERM-SIM-01",
  },
  expiresAt: new Date(Date.now() + 14 * 60 * 1000).toISOString(),
};

const server = {
  signedIn: false,
  submissions: [],
  eventsRequests: [],
  eventsQueue: [],
  historyRequests: [],
  historyPayload: {
    version: 1,
    transactions: [{
      authorizationId: "AUTH-1",
      transactionId: "TX-0001",
      channelPaymentRef: "PAY-1",
      amount: "12.34",
      currency: "USD",
      outcome: "APPROVED",
      businessVersion: 3,
      asOf: "2026-11-21T12:00:00Z",
      serverFreshness: "CURRENT",
      journalReadable: true,
      receivedAt: "2026-11-21T11:59:00Z",
    }],
    nextCursor: null,
    retentionTruncated: false,
  },
  detailPayload: {
    version: 1,
    authorizationId: "AUTH-1",
    transactionId: "TX-0001",
    authority: "LAST_RECORDED",
    journalOutcome: null,
    journalVersion: null,
    journalAsOf: null,
    journalReadable: false,
    lastRecordedEventId: "EV-2",
    knownHead: 2,
    coveredThrough: 2,
    expiredRanges: [[1, 1]],
    taskGaps: [],
    retentionTruncated: false,
  },
  expireHistory: false,
};

function jsonResponse(status, payload) {
  return {
    status,
    text: async () => (payload === null || payload === undefined ? "" : JSON.stringify(payload)),
  };
}

function eventPage(entries, options = {}) {
  const coveredThrough = entries.length === 0 ? 0 : entries[entries.length - 1].sourceSequence;
  return {
    version: 1,
    transactionId: "TX-0001",
    snapshotHead: options.snapshotHead ?? coveredThrough,
    expiryRevision: options.expiryRevision ?? 1,
    firstRetainedSequence: options.firstRetainedSequence ?? 1,
    events: entries,
    coveredThrough,
    expiredRanges: options.expiredRanges ?? [],
    gapRanges: options.gapRanges ?? [],
    nextCursor: options.nextCursor ?? null,
    completeThroughSnapshot: options.completeThroughSnapshot ?? true,
    retentionTruncated: options.retentionTruncated ?? false,
  };
}

function eventEntry(sequence, eventId, status, hashChar = "a") {
  return {
    sourceSequence: sequence,
    deliveryClass: "CRITICAL_COMMITTED",
    eventSha256: hashChar.repeat(64),
    journalVersion: 1,
    committedAt: "2026-11-21T12:00:00Z",
    collectedAt: "2026-11-21T12:00:00Z",
    storedAt: "2026-11-21T12:00:00Z",
    stateAfter: "APPROVED",
    record: {
      schemaVersion: 1,
      eventId,
      producerId: "producer-a",
      taskName: `task-${sequence}`,
      status,
      occurredAt: "2026-11-21T12:00:00Z",
      actor: "ACL",
      provenance: "OBSERVED",
      attemptId: "ATT-1",
      correlationId: "CORR-1",
    },
  };
}

function envelope(overrides = {}) {
  return {
    contractVersion: "1",
    authorizationId: "AUTH-1",
    transactionId: "TX-0001",
    attemptId: "ATT-1",
    correlationId: "CORR-REQUEST",
    originalCorrelationId: "CORR-ORIGINAL",
    outcome: "APPROVED",
    disposition: "COMPLETED",
    businessVersion: 3,
    asOf: "2026-11-21T12:00:00Z",
    observation: "COMPLETE",
    serverFreshness: "CURRENT",
    error: null,
    ...overrides,
  };
}

globalThis.fetch = async (url, options = {}) => {
  const method = options.method ?? "GET";
  const parsed = new URL(url, "http://127.0.0.1:8099");

  if (parsed.pathname === "/presenter/v1/session") {
    if (method === "POST") {
      const body = JSON.parse(options.body);
      if (body.accessCode === "demo-access-code-123") {
        server.signedIn = true;
        return jsonResponse(200, SESSION);
      }
      return jsonResponse(401, { error: "INVALID_ACCESS_CODE" });
    }
    if (method === "DELETE") {
      server.signedIn = false;
      return { status: 204, text: async () => "" };
    }
    return server.signedIn ? jsonResponse(200, SESSION) : jsonResponse(401, { error: "NO_SESSION" });
  }

  if (parsed.pathname === "/console/v1/authorizations" && method === "POST") {
    server.submissions.push(JSON.parse(options.body));
    return jsonResponse(200, envelope());
  }

  if (/^\/console\/v1\/transactions\/[^/]+\/events$/.test(parsed.pathname)) {
    server.eventsRequests.push({
      after: parsed.searchParams.get("after"),
      generation: parsed.searchParams.get("generation"),
      snapshot: parsed.searchParams.get("snapshot"),
    });
    const next = server.eventsQueue.shift();
    if (!next) return jsonResponse(200, eventPage([]));
    return jsonResponse(next.status ?? 200, next.payload);
  }

  if (/^\/console\/v1\/transactions\/[^/]+$/.test(parsed.pathname)) {
    return jsonResponse(200, server.detailPayload);
  }

  if (parsed.pathname === "/console/v1/transactions") {
    server.historyRequests.push(parsed.search);
    if (server.expireHistory) return jsonResponse(401, { error: "UNAUTHENTICATED" });
    return jsonResponse(200, server.historyPayload);
  }

  return jsonResponse(404, { error: "NOT_FOUND" });
};

globalThis.document = documentStub;
globalThis.WebSocket = FakeWebSocket;
globalThis.location = { protocol: "http:", host: "127.0.0.1:8099" };

/* ----------------------------- assertions ---------------------------- */

const results = [];
function check(name, condition, detail = "") {
  results.push({ name, ok: Boolean(condition), detail });
}

const wait = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

await import(pathToFileURL(appPath).href);
await wait(30);

function node(id) {
  return registry.get(id);
}

function sentFrames(socket, action) {
  return socket.sent.filter((frame) => frame.action === action);
}

/* 1. Unauthenticated start. */
check("login visible without a session", node("login-section").hidden === false);
check("app hidden without a session", node("app").hidden === true);
check("login button enabled when relay answers 401", node("login-button").disabled === false);

/* 2. Sign in through the recorded submit handler. */
node("access-code").value = "demo-access-code-123";
node("login-form").dispatch("submit");
await wait(30);
check("app visible after sign-in", node("app").hidden === false);
check("fixed acquirer context rendered", node("ctx-acquirer").textContent === "ACQ-SIM-01");
check("fixture identity labelled", node("fixture-badge").textContent.includes("LOCAL_TEST_FIXTURE"));
check("live socket opened after sign-in", sockets.length === 1);

/* 3. Ready frame, then a closed C1 submission. */
const firstSocket = sockets[0];
firstSocket.serverSend({
  version: 1,
  action: "ready",
  connectionEpoch: "EP-1",
  environmentId: "local-demo",
});

node("payment-ref").value = "PAY-TEST-1";
node("correlation-id").value = "CORR-TEST-1";
node("credential-ref").value = "CRED-SIM-0001";
node("amount").value = "12.34";
node("purchase-form").dispatch("submit");
await wait(40);

check("one payment submission recorded", server.submissions.length === 1);
const body = server.submissions[0] || {};
const expectedKeys = [
  "contractVersion", "operation", "correlationId", "acquirerRef", "merchantRef", "terminalRef",
  "paymentRef", "credentialFixtureRef", "amount", "currency", "entryMode", "cvm", "cryptogramPresent",
];
check("C1 body has the closed key set", JSON.stringify(Object.keys(body).sort()) === JSON.stringify([...expectedKeys].sort()),
  JSON.stringify(Object.keys(body)));
check("C1 body uses relay-fixed context", body.acquirerRef === "ACQ-SIM-01" && body.merchantRef === "MID-SIM-01"
  && body.terminalRef === "TERM-SIM-01");
check("no caller identity/decision/route fields", !("decision" in body) && !("route" in body)
  && !("user" in body) && !("fault" in body));
check("result shows server outcome", node("result-outcome").textContent === "APPROVED");
check("result transaction displayed", node("res-transaction").textContent === "TX-0001");

const subscribeFrames = sentFrames(firstSocket, "subscribe");
check("exactly one subscribe frame sent", subscribeFrames.length === 1);
const firstSubscribe = subscribeFrames[0];
check("subscribe names the returned transaction", firstSubscribe.transactionId === "TX-0001");
check("subscribe carries fixed context only",
  firstSubscribe.context.acquirerRef === "ACQ-SIM-01" && firstSubscribe.context.merchantRef === "MID-SIM-01");

/* 4. Pre-ACK event must not render. */
const firstEntry = eventEntry(1, "EV-1", "STARTED");
firstSocket.serverSend({
  version: 1,
  action: "event",
  connectionEpoch: "EP-1",
  environmentId: "local-demo",
  context: firstSubscribe.context,
  requestId: firstSubscribe.requestId,
  subscriptionId: firstSubscribe.subscriptionId,
  generation: 7,
  transactionId: "TX-0001",
  sourceSequence: 1,
  eventId: "EV-1",
  producerId: "producer-a",
  eventSha256: "a".repeat(64),
  authority: "RECORDED_EVENT",
  record: firstEntry.record,
});
await wait(10);
check("no event rendered before the ACK", node("timeline").children.length === 0);

/* 5. ACK, then fixed-snapshot catch-up. */
server.eventsQueue.push({
  payload: eventPage([firstEntry, eventEntry(2, "EV-2", "SUCCEEDED", "b")], { snapshotHead: 2 }),
});
firstSocket.serverSend({
  version: 1,
  action: "subscribed",
  requestId: firstSubscribe.requestId,
  subscriptionId: firstSubscribe.subscriptionId,
  connectionEpoch: "EP-1",
  environmentId: "local-demo",
  context: firstSubscribe.context,
  transactionId: "TX-0001",
  generation: 7,
  headSequence: 1,
  expiryRevision: 1,
  firstRetainedSequence: 1,
});
await wait(60);

check("catch-up requested after the ACK", server.eventsRequests.length >= 1);
check("catch-up starts from the pre-subscription head", server.eventsRequests[0].after === "1");
check("catch-up binds the ACK generation", server.eventsRequests[0].generation === "7");
check("timeline renders the merged snapshot", node("timeline").children.length === 2);
check("snapshot head displayed", node("live-snapshot-head").textContent === "2");
check("live status connected", node("live-badge").textContent === "Conectado");

/* 6. Duplicate and conflicting deliveries. */
firstSocket.serverSend({
  version: 1,
  action: "event",
  connectionEpoch: "EP-1",
  environmentId: "local-demo",
  context: firstSubscribe.context,
  requestId: firstSubscribe.requestId,
  subscriptionId: firstSubscribe.subscriptionId,
  generation: 7,
  transactionId: "TX-0001",
  sourceSequence: 1,
  eventId: "EV-1",
  producerId: "producer-a",
  eventSha256: "a".repeat(64),
  authority: "RECORDED_EVENT",
  record: firstEntry.record,
});
await wait(10);
check("duplicate delivery does not duplicate the timeline", node("timeline").children.length === 2);
check("duplicate counter incremented", Number(node("counter-frames-duplicate").textContent) >= 1);

firstSocket.serverSend({
  version: 1,
  action: "event",
  connectionEpoch: "EP-1",
  environmentId: "local-demo",
  context: firstSubscribe.context,
  requestId: firstSubscribe.requestId,
  subscriptionId: firstSubscribe.subscriptionId,
  generation: 7,
  transactionId: "TX-0001",
  sourceSequence: 1,
  eventId: "EV-1",
  producerId: "producer-a",
  eventSha256: "c".repeat(64),
  authority: "RECORDED_EVENT",
  record: firstEntry.record,
});
await wait(10);
check("conflicting hash raises an integrity alert", node("live-alert").hidden === false);

/* 7. Gap detection triggers source catch-up, not fabrication. */
server.eventsQueue.push({
  payload: eventPage([eventEntry(3, "EV-3", "SUCCEEDED", "d"), eventEntry(4, "EV-4", "SUCCEEDED", "e")],
    { snapshotHead: 4 }),
});
firstSocket.serverSend({
  version: 1,
  action: "event",
  connectionEpoch: "EP-1",
  environmentId: "local-demo",
  context: firstSubscribe.context,
  requestId: firstSubscribe.requestId,
  subscriptionId: firstSubscribe.subscriptionId,
  generation: 7,
  transactionId: "TX-0001",
  sourceSequence: 4,
  eventId: "EV-4",
  producerId: "producer-a",
  eventSha256: "e".repeat(64),
  authority: "RECORDED_EVENT",
  record: eventEntry(4, "EV-4", "SUCCEEDED", "e").record,
});
await wait(400);
check("gap triggers a source catch-up from the last covered sequence",
  server.eventsRequests.some((request) => request.after === "2"),
  JSON.stringify(server.eventsRequests.map((request) => request.after)));
check("timeline fills the gap contiguously", node("timeline").children.length === 4);

/* 8. Reconnect uses a new epoch and never resubmits a payment. */
const submissionsBeforeReconnect = server.submissions.length;
firstSocket.close();
await wait(20);
check("socket close marks the transport disconnected", node("live-badge").textContent === "Desconectado");
await wait(1500);
check("reconnect opens a new socket", sockets.length === 2);
const secondSocket = sockets[1];
secondSocket.serverSend({
  version: 1,
  action: "ready",
  connectionEpoch: "EP-2",
  environmentId: "local-demo",
});
await wait(30);
const resubscribe = sentFrames(secondSocket, "subscribe");
check("reconnect resubscribes with a new request identity",
  resubscribe.length === 1
  && resubscribe[0].requestId !== firstSubscribe.requestId
  && resubscribe[0].subscriptionId !== firstSubscribe.subscriptionId);
check("reconnect does not POST a payment", server.submissions.length === submissionsBeforeReconnect);
server.eventsQueue.push({ payload: eventPage([]) });
secondSocket.serverSend({
  version: 1,
  action: "subscribed",
  requestId: resubscribe[0].requestId,
  subscriptionId: resubscribe[0].subscriptionId,
  connectionEpoch: "EP-2",
  environmentId: "local-demo",
  context: resubscribe[0].context,
  transactionId: "TX-0001",
  generation: 9,
  headSequence: 4,
  expiryRevision: 1,
  firstRetainedSequence: 1,
});
await wait(50);
check("reconnect catch-up resumes from covered progress",
  server.eventsRequests[server.eventsRequests.length - 1].after === "4",
  JSON.stringify(server.eventsRequests.map((request) => request.after)));
check("reconnect keeps the rendered timeline", node("timeline").children.length === 4);

/* 9. Resync is explicit, never a silent jump. */
server.eventsQueue.push({
  status: 410,
  payload: {
    code: "RESYNC_REQUIRED",
    snapshotHead: 9,
    expiryRevision: 2,
    firstRetainedSequence: 5,
    expiredRanges: [[1, 4]],
    gapRanges: [],
  },
});
secondSocket.serverSend({ version: 1, action: "sync" });
await wait(20);
check("expired cursor asks for an explicit resync", node("resync-button").hidden === false);

/* 10. History and detail. */
node("history-refresh").dispatch("click");
await wait(40);
check("history rows rendered", node("history-rows").children.length === 1);
check("history request is bounded to seven days", server.historyRequests.some((query) => query.includes("window=7d")));

/* 11. Session expiry stops writes and closes live connections. */
server.expireHistory = true;
node("history-refresh").dispatch("click");
await wait(40);
check("expired session returns to the login gate", node("login-section").hidden === false);
check("expired session hides the app", node("app").hidden === true);
check("expired session reports the cause", node("global-notice").textContent.length > 0);

/* ----------------------------- report ------------------------------- */

let failures = 0;
for (const result of results) {
  if (!result.ok) failures += 1;
  const status = result.ok ? "PASS" : "FAIL";
  console.log(`${status}  ${result.name}${result.detail ? `  [${result.detail}]` : ""}`);
}
console.log(`\n${results.length - failures}/${results.length} checks passed`);
console.log(`payment submissions: ${server.submissions.length}`);
console.log(`catch-up requests: ${server.eventsRequests.length}`);
console.log(`sockets opened: ${sockets.length}`);
process.exit(failures === 0 ? 0 : 1);
