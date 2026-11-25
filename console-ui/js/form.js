/*
 * C1 form mapping for the local development console.
 *
 * This module only maps DOM values onto the approved C1 field set. It performs no
 * authentication, keeps no credential, decides no outcome and never adds a field: the server-side
 * ChannelRequestValidator stays authoritative, and any field not declared here cannot appear in
 * the request body.
 */

/** The exact C1 body keys this form can produce, in the approved order. */
export const C1_FIELDS = Object.freeze([
  "contractVersion",
  "operation",
  "correlationId",
  "acquirerRef",
  "merchantRef",
  "terminalRef",
  "paymentRef",
  "credentialFixtureRef",
  "amount",
  "currency",
  "entryMode",
  "cvm",
  "cryptogramPresent"
]);

const alphabet = /^[A-Za-z0-9._:-]+$/;

/** A random, profile-safe reference. Never used as an identity or a credential. */
export function newReference(prefix, length = 10) {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  let suffix = "";
  for (const byte of bytes) {
    suffix += "0123456789ABCDEFGHJKMNPQRSTVWXYZ"[byte % 32];
  }
  return prefix + suffix;
}

/**
 * Fill the trusted demo context the harness served. These values are not secrets and the server
 * still refuses a mismatch; prefilling them only avoids a guaranteed refusal.
 */
export function applyDemoContext(form, context) {
  if (!context || typeof context !== "object") return;
  setValue(form, "acquirerRef", context.acquirerRef);
  setValue(form, "merchantRef", context.merchantRef);
  setValue(form, "terminalRef", context.terminalRef);
  setValue(form, "currency", context.currency);
}

/** Put fresh correlation and payment references into the form. */
export function fillDefaultReferences(form) {
  setValue(form, "correlationId", newReference("CORR-"));
  setValue(form, "paymentRef", newReference("PAY-"));
}

/**
 * Read the form into the closed C1 body.
 *
 * @throws {Error} when a required value is missing or the client-side shape is wrong. The server
 *     repeats every check; this is a convenience, not the authority.
 */
export function readSubmission(form) {
  const values = new Map();
  for (const name of C1_FIELDS) {
    if (name === "contractVersion" || name === "operation") continue;
    const element = form.elements.namedItem(name);
    if (!element) {
      throw new Error("the form is missing field " + name);
    }
    values.set(name, String(element.value ?? "").trim());
  }

  for (const [name, value] of values) {
    if (value === "") {
      throw new Error("field " + name + " is required");
    }
  }
  for (const name of [
    "correlationId",
    "acquirerRef",
    "merchantRef",
    "terminalRef",
    "paymentRef",
    "credentialFixtureRef"
  ]) {
    if (!alphabet.test(values.get(name))) {
      throw new Error("field " + name + " contains a character outside the approved alphabet");
    }
  }
  if (!/^[0-9]{1,4}\.[0-9]{2}$/.test(values.get("amount"))) {
    throw new Error("amount must match 0.00 with 1-4 integral digits");
  }
  if (values.get("currency") !== "USD") {
    throw new Error("currency must be USD");
  }

  return {
    contractVersion: "1",
    operation: "A",
    correlationId: values.get("correlationId"),
    acquirerRef: values.get("acquirerRef"),
    merchantRef: values.get("merchantRef"),
    terminalRef: values.get("terminalRef"),
    paymentRef: values.get("paymentRef"),
    credentialFixtureRef: values.get("credentialFixtureRef"),
    amount: values.get("amount"),
    currency: values.get("currency"),
    entryMode: values.get("entryMode"),
    cvm: values.get("cvm"),
    cryptogramPresent: values.get("cryptogramPresent") === "true"
  };
}

function setValue(form, name, value) {
  if (typeof value !== "string" || value === "") return;
  const element = form.elements.namedItem(name);
  if (element) element.value = value;
}
