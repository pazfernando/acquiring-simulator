# SIM-ISO87A-v1 — complete candidate
Status: PROPOSED / NOT APPROVED / specification only. All profile choices below are DESIGN ASSUMPTIONS, not ISO/scheme rules. Authorization A only; no inquiry, reversal, posting, settlement, stand-in or partial approval.
Structural source: research-sources/jpos-iso87ascii.xml at research manifest commit 5ef6c9be0fce8c4c00415b05ee443f4178f8112b, SHA256 d128a0bb435af8193da09539937515052019a8eb3fa5c888c5dd6e9410ecb397. This document is the strict codec configuration contract: subset widths match that snapshot; all other fields disabled. No choice of runtime library is implied. Do not merge pyiso8583 defaults.

## P1 — packing and separate presence matrices
Body = MTI (4 digits) + primary bitmap (16 uppercase hexadecimal ASCII bytes, high bit first) + ascending fields. No secondary bitmap, header, delimiter, BOM, trailer or length frame. Every octet ASCII; reject Unicode, control characters and trailing bytes. Numeric fields exact digit widths, no signs/decimal separators; encoder explicitly zero-formats amounts/STAN, parser never pads. Fixed character aliases have exact widths, no implicit space padding. LL/LLL are respectively two/three ASCII decimal byte lengths excluding prefix. No truncation or rounding anywhere. Reject before downstream dispatch if any input cannot be represented.

| DE | Encoding/allowed value | 0100 | 0110 |
| --- | --- | --- | --- |
| 3 | n6 = 000000 (private purchase choice) | required | required echo |
| 4 | n12 minor USD units 000000000100–000000999999 | required | required echo |
| 7 | n10 MMDDhhmmss UTC | required | required echo of request, not reply time |
| 11 | n6 000000–999999 STAN | required | required echo |
| 22 | n3 051 contact / 071 contactless; private synthetic choices | required | required echo |
| 25 | n2 = 00 | required | required echo |
| 32 | LL n1–11; registered durable acquirer alias | required | required echo |
| 37 | fixed 12 uppercase letters/digits; RRN | required | required echo |
| 38 | fixed 6 uppercase letters/digits; actual issuer auth reference | forbidden | conditional: only 00 AND actual verified issuer reference exists |
| 39 | fixed 2 digits per P4 | forbidden | required |
| 41 | fixed 8 uppercase letters/digits; terminal alias | required | required echo |
| 42 | fixed 15 uppercase letters/digits; merchant alias | required | required echo |
| 48 | LLL ASCII canonical extension, 1–999 bytes | required request schema | required response schema |
| 49 | fixed 3 = 840 | required | required echo |
| all others, including 1/2/35/36/45/52/55/64/65–128 | disabled; reject even if apparently synthetic | forbidden | forbidden |

DE38 is optional on verified approval, never mandatory without real evidence. No synthetic code is minted to fill absence. Replay preserves the received value or absence. Missing DE38 is invalid only when the trusted evidence record asserts a reference that the response omits; unsolicited/mismatching DE38 is invalid. DE38 is not a transaction ID or approval proof.

## P2 — canonical DE48
Canonical ordered ASCII key=value pairs separated by semicolon, no terminal delimiter, spaces or escaping. Keys case-sensitive. Values restricted to [A-Za-z0-9._:-], except specified enums/digits. Empty optional fields are omitted. No percent encoding, nested data, duplicate keys, unknown keys or out-of-order keys; reject rather than normalize. Maxima are byte lengths. Sum including keys/separators must be <=999, even if each individual field fits. This aggregate constraint deliberately means not all maxima fit together.

Request order (all required except prior): v,op,aid,cid,tid,att,acq,mid,term,pay,gen,fixture,cvm,crypto,risk,sent,prior.
Response order: all request keys in same order, then actor,stage,result,reason,evidence,at. All six response keys required.
| Key | Meaning / bounds |
| --- | --- |
| v / op | literal 1 / A |
| aid / cid / tid / att | authorization/correlation/transaction/submission attempt IDs; 1–64 each |
| acq / mid / term / pay | original business acquirer/merchant/terminal/payment refs; 1–128 each |
| gen | generation decimal 1–10 digits, 1–2147483647, no leading zeros |
| fixture | registry credential fixture reference, 1–64 |
| cvm | VERIFIED or NOT_REQUIRED |
| crypto | 0 or 1; synthetic presence only |
| risk | PASS or REJECT, resolved default PASS |
| sent | exactly YYYYMMDDhhmmss (14 digits), full UTC request year |
| prior | optional authorized original authorization ID, 1–64 |
| actor | ISSUER, NETWORK or PROCESSOR |
| stage | ISSUER_REPLIED, BEFORE_ISSUER, POSSIBLE_ISSUER or BEFORE_NETWORK |
| result | APPROVED, DECLINED, PROCESSOR_REJECTED or NOT_CONFIRMED |
| reason | APPROVED, DELIBERATE_DECLINE, FORMAT, DISALLOWED, UNAVAILABLE or UNCERTAIN |
| evidence | 1–64, server evidence identifier, never a bearer credential |
| at | YYYYMMDDhhmmss actual evidence availability time |

Original refs are not squeezed into aliases. IDs are server generated except channel correlation/payment/prior references. Request metadata is server-derived where applicable; a caller cannot select aid, generation, fixture outcome or provenance. Responses echo exact request identity/context/value metadata. Evidence time may be later than sent.

DE7 month/day/time must match sent. Validate Gregorian dates using sent year, including leap days; no nearest-year inference. At 2026-12-31T23:59:59Z and 2027-01-01T00:00:01Z, DE7 is respectively 1231235959 and 0101000001 with distinct full sent values. Late responses retain original DE7/sent, use at for reply time. New first dispatch sent must be within 300 seconds of server receipt; authorized historical replay/late evidence uses persisted request, not this freshness check. No timestamp is an identity.

## P3 — identity, aliases and fixtures
Preserve existing A rules: ../WI-ACQ-AUTH-001/requirements.md R15–R20, simulator-profile.md P01–P08 and aws-architecture-update.md. Their existing approved cores versus pending supplements retain their original status.
Business key = (acq,mid,pay). Atomically claim generation/authorization and one dispatch intent. Window [firstReceipt,firstReceipt+24h); exact end permits new generation only for unlinked request. A supplied prior is validated first: existence, current subject access, same key and identical resolved data; foreign/unknown/malformed/conflicting links reject generically even when local key matches. Linked retry never becomes new at expiry. Preserve original ID/correlation and refs across restart; a new submission has a new attempt ID and links the stored instruction. Identical replay reads latest result without re-encoding/reissuing wire instruction. UNKNOWN/in-flight also replay without redispatch. New exchange correlation is recorded on attempt, not substituted into original instruction.
Equality = resolved amount,currency,credential fixture,terminal,channel POS,entry mode,CVM,crypto,risk; omit exchange/prior references. Conflicts reject, no mutation/dispatch. Registry changes cannot re-evaluate already claimed instruction.
Provision aliases via conditional unique writes: DE32 globally unique decimal alias (1–11 digits); DE42 unique within acquirer; DE41 unique within acquirer+merchant. Mappings store full original refs and inverse scope, resolve under current authorization, survive restart; never trust alias alone as access. Example 1 / MERCHANT00000001 / TERM0001 maps ACQ-A / MERCHANT-A / TERMINAL-A. ACQ-B may reuse merchant/terminal aliases, not DE32=1. Allocation collision rejects/reallocates before activation, never overwrites. Exhaustion fails closed. Never truncate/hash without collision checking; no alias reuse/deletion while linked business records exist.
STAN is a durable per-acquirer counter wrapping 999999 to 000000; RRN is a durable registered 12-character allocation. Collision never merges identities: aid/cid/gen/context govern. Same STAN/RRN across tenants or after wrap must remain separate. Responses require all echoed identities plus trusted evidence, not STAN/RRN alone.
Active server registry resolves fixture, acquirer mandate, merchant/terminal association, one permitted DOMESTIC/INTERNATIONAL route, issuer scenario and CP capability. SIM-APP-001 and SIM-DEC-001 are fixture names, not PANs; approval/decline labels are not trusted without registry entry. Unknown or unauthorized fixture rejects before dispatch. Network faults operator-controlled only.

## P4 — actor/dispatch/result matrix
Dispatch stage distinguishes channel→simulator/network from network→issuer. The response producer authenticates at service boundary; actor text alone is not trust. Evidence identifier must resolve to correlated durable processor-received evidence from authenticated configured actor. Network may relay verified issuer evidence but cannot mint issuer decisions.
| Producing decision actor | Verified dispatch stage | DE39 / result / reason | Channel and business effect |
| --- | --- | --- | --- |
| ISSUER (possibly relayed) | ISSUER_REPLIED | 00 / APPROVED / APPROVED | APPROVED only after durable business evidence |
| ISSUER (possibly relayed) | ISSUER_REPLIED | 05 / DECLINED / DELIBERATE_DECLINE | DECLINED only for registered deliberate-decline scenario; not every decline reason |
| PROCESSOR | BEFORE_NETWORK | 30 / PROCESSOR_REJECTED / FORMAT or DISALLOWED | explicit validation rejection, no network dispatch |
| NETWORK | BEFORE_ISSUER | 30 / PROCESSOR_REJECTED / FORMAT or DISALLOWED | explicit network rejection, issuer not dispatched |
| NETWORK | BEFORE_ISSUER | 91 / PROCESSOR_REJECTED / UNAVAILABLE | only positive evidence of non-forwarding; not issuer decline |
| NETWORK or PROCESSOR | POSSIBLE_ISSUER | 91 / NOT_CONFIRMED / UNCERTAIN or UNAVAILABLE | UNKNOWN, no automatic retry |
| no response at all | possible dispatch | no MTI/DE39/extension manufactured | NOT_CONFIRMED at 2s AWS deadline, unknown issuer result |
| any other combination | any | invalid evidence | quarantine safe metadata; preserve known outcome or UNKNOWN |

A malformed input with insufficient correlation gets HTTP error, not manufactured 0110. Valid correlated network format rejection may get 30. Unknown DE39 (including unconfigured issuer decline reason), missing result extension, 00/05 without issuer evidence, 30 with issuer decision, 91 as issuer decline, incorrect context/op/correlation/amount or unsolicited DE38 cannot confirm. The evidence ID binds actor, dispatch stage, exact instruction and result, not merely existence of an event.
Verified late approval/decline appends to same journal and reconciles UNKNOWN. Exact repeat evidence idempotent. Conflicting confirmed evidence is quarantined and flagged for separately authorized resolution, never silently replaces prior confirmation. Delayed timeout cannot regress confirmation. Issuer diagnostic table alone is not processor receipt. Journal failures follow observation-contract.md; no trace-only confirmation.
