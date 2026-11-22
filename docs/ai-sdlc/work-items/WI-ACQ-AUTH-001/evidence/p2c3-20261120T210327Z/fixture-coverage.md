# P2c3 fixture coverage

Authority: `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/response-vectors.json`
(status "PROPOSED exact C3 JSON fixtures, not executed HTTP/QA"), read-only at test time.

| Case | Branch | HTTP | outcome | disposition | error |
| --- | --- | --- | --- | --- | --- |
| H01 | readable confirmation | 200 | APPROVED | REPLAY | null |
| H02 | confirmation, event history down | 200 | APPROVED | REPLAY | EVENT_HISTORY_UNAVAILABLE |
| H03 | confirmation read, then store down | 200 | APPROVED | REPLAY | null |
| H04 | initial confirmation, notifier down | 200 | APPROVED | COMPLETED | LIVE_NOTIFICATIONS_UNAVAILABLE |
| H05 | unsent claim, replay | 503 | null | NOT_DISPATCHED | RESERVED_UNSENT |
| H06 | no claim, admission unavailable | 503 | null | NOT_DISPATCHED | BUSINESS_ADMISSION_UNAVAILABLE |
| H07 | possible send, journal unavailable | 503 | NOT_CONFIRMED | PENDING | BUSINESS_JOURNAL_UNAVAILABLE |
| H08 | active initial | 202 | NOT_CONFIRMED | PENDING | null |
| H09 | active duplicate | 202 | NOT_CONFIRMED | PENDING | null |
| H10 | deadline initial | 200 | NOT_CONFIRMED | COMPLETED | null |
| H11 | deadline duplicate | 200 | NOT_CONFIRMED | REPLAY | null |
| H12 | unreadable restart | 503 | null | UNAVAILABLE | BUSINESS_JOURNAL_UNAVAILABLE |
| H13 | ambiguous commit, no proof | 503 | null | UNAVAILABLE | BUSINESS_JOURNAL_UNAVAILABLE |
| H14 | context denied | 403 | null | REJECTED | CONTEXT_DENIED |
| H15 | invalid input | 400 | null | REJECTED | INVALID_INPUT |
| H16 | unsent claim, history | 200 | null | NOT_DISPATCHED | RESERVED_UNSENT |
| H17 | confirmed decline | 200 | DECLINED | COMPLETED | null |
| H18 | confirmed processor rejection | 200 | PROCESSOR_REJECTED | COMPLETED | null |
| H19 | confirmed, ADOT down | 200 | APPROVED | COMPLETED | null |
| H20 | confirmed, live delivery gap | 200 | APPROVED | COMPLETED | LIVE_DELIVERY_LAG |
| H21 | no claim, admission unavailable | 503 | null | NOT_DISPATCHED | BUSINESS_ADMISSION_UNAVAILABLE |
| H22 | no claim, capacity | 503 | null | NOT_DISPATCHED | EVENT_CAPACITY |
| H23 | deadline, notifier down | 200 | NOT_CONFIRMED | COMPLETED | LIVE_NOTIFICATIONS_UNAVAILABLE |
| H24 | reserved outcome, gap | 200 | APPROVED | COMPLETED | LIVE_DELIVERY_LAG |
| H25 | live outage, known replay | 200 | APPROVED | REPLAY | LIVE_NOTIFICATIONS_UNAVAILABLE |
| H26 | history+business unreadable | 503 | null | UNAVAILABLE | BUSINESS_JOURNAL_UNAVAILABLE |

Assertion per case: `HttpStatus.code()` equals the fixture `httpStatus`; the builder's
`toCanonicalJson()` equals the fixture `bodyUtf8` byte for byte. The expected body/status
is never passed into the builder; each case is translated into semantic facts only.

Builders derive, rather than copy, disposition (origin), outcome (durable state), status
(branch), observation/freshness/error (async source health), id disclosure (branch) and
version (durable fact).

Not covered by fixtures, covered by focused unit tests: 401 UNAUTHENTICATED,
404 NOT_FOUND, 409 PAYLOAD_CONFLICT, 413 BODY_TOO_LARGE, 415 UNSUPPORTED_MEDIA,
400 INVALID_REFERENCE, closed 13-key set, denial non-leak, invalid envelope shapes.
