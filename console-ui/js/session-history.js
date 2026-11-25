/*
 * Session-only submission history.
 *
 * This is deliberately NOT the server read model. It is an in-memory array in the page's module
 * scope: it survives neither a reload nor a new tab, and it is never sent to the server. It exists
 * so the screen can show what this tab actually received while the approved history read
 * (GET /console/v1/transactions/{id}/events, G9/P3) is not implemented.
 */

export function createSessionHistory(limit = 50) {
  const entries = [];

  return {
    add(entry) {
      entries.unshift({ at: Date.now(), ...entry });
      if (entries.length > limit) {
        entries.length = limit;
      }
    },
    entries() {
      return entries.slice();
    },
    clear() {
      entries.length = 0;
    },
    size() {
      return entries.length;
    }
  };
}

/** A stable local time string for display. Locale only; no business meaning. */
export function localTime(millis) {
  try {
    return new Date(millis).toLocaleTimeString();
  } catch (error) {
    return "—";
  }
}
