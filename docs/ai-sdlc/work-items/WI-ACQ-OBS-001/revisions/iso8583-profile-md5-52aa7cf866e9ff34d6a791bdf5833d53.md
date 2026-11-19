# SIM-ISO87A-v1 — selected private family; revised details
D1 private authorization profile and HTTPS boundary APPROVED per owner-decisions.md, including reviewed P2a/P3a/P5/P6 and C3. This is selection only: D2/D3, revised C1–C3 corrections and detailed architecture/crypto remain PROPOSED pending re-review and later approval. Not ISO/scheme rules or construction permission. Authorization A only; no inquiry, reversal, posting, settlement, stand-in or partial approval.
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

### P2a — deterministic exchange admission budget
Structural parser accepts DE48 up to 999 bytes. Exchange admission before business claim/dispatch requires request DE48 <=826 bytes. Reserve 173 bytes for the longest supported suffix: ;actor=PROCESSOR;stage=BEFORE_NETWORK;result=PROCESSOR_REJECTED;reason=DISALLOWED;evidence= plus 64 ASCII evidence-ID bytes plus ;at= plus 14 digits. Every P4 alternative fits. This uniform limit applies even when a shorter result is expected. 827–999 structurally valid requests fail ADMISSION_RESPONSE_CAPACITY, no claim or dispatch. Never discover response overflow after a permitted send, omit echoed keys, shorten refs or truncate evidence. Profile exact aliases/no padding supersede research's illustrative space-padding recommendation.

## P3 — identity, aliases and fixtures
Preserve existing A rules: ../WI-ACQ-AUTH-001/requirements.md R15–R20, simulator-profile.md P01–P08 and aws-architecture-update.md. Their existing approved cores versus pending supplements retain their original status.
Business key = (acq,mid,pay). Atomically claim generation/authorization and one dispatch intent. Window [firstReceipt,firstReceipt+24h); exact end permits new generation only for unlinked request. A supplied prior is validated first: existence, current subject access, same key and identical resolved data; foreign/unknown/malformed/conflicting links reject generically even when local key matches. Linked retry never becomes new at expiry. Preserve original ID/correlation and refs across restart; a new submission has a new attempt ID and links the stored instruction. Identical replay reads latest result without re-encoding/reissuing wire instruction. UNKNOWN/in-flight also replay without redispatch. New exchange correlation is recorded on attempt, not substituted into original instruction.
Equality = resolved amount,currency,credential fixture,terminal,channel POS,entry mode,CVM,crypto,risk; omit exchange/prior references. Conflicts reject, no mutation/dispatch. Registry changes cannot re-evaluate already claimed instruction.
Provision aliases via conditional unique writes: DE32 globally unique decimal alias (1–11 digits); DE42 unique within acquirer; DE41 unique within acquirer+merchant. Mappings store full original refs and inverse scope, resolve under current authorization, survive restart; never trust alias alone as access. Example 1 / MERCH0000000001 / TERM0001 maps ACQ-A / MERCHANT-A / TERMINAL-A. ACQ-B may reuse merchant/terminal aliases, not DE32=1. Allocation collision rejects/reallocates before activation, never overwrites. Exhaustion fails closed. Never truncate/hash without collision checking; no alias reuse/deletion while linked business records exist.
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

### P3a — replay resolution order (PROPOSED)
1. Authenticate and check CURRENT subject/context mandate, merchant/terminal association, and submitted schema/amount/currency/CP enum validity. Revoked context always denies; no stored outcome disclosure.
2. Validate supplied prior first (authorized existence and same key), else locate key/generation. For retained instruction compare submitted literal fixture ID and resolved payment fields against persisted canonical instruction. Omitted risk resolves to persisted risk on replay, not a newly changed registry default. Entry/CVM/crypto are mandatory, not inferred. A newly explicit risk different from persisted value conflicts.
3. Replay does NOT require the old credential fixture/scenario/route to remain active: fixture retired/removed or route/issuer scenario/default changed cannot recompute the instruction or deny replay by itself. Current context access is mandatory; a separate explicit replay-access revocation on that context denies. Credential input different from pinned fixture conflicts. Return stored outcome/latest admitted evidence without another route/issuer decision. In-flight/POSSIBLE_SEND replay explicitly returns NOT_CONFIRMED, HTTP 202 PENDING. CLAIMED_NOT_SENT follows O3a, not UNKNOWN.
4. Only new unlinked generation resolves CURRENT fixture eligibility/defaults/route, then equality/capacity/claim. Missing registry fixture/route denies NEW work without dispatch. Existing equality set, prior precedence and 24h boundary remain unchanged.

## P5 — trusted business evidence admission (PROPOSED, distinct from traces)
Service principals are configured roles ACL-AUTH, PROCESSOR-AUTH, NETWORK-SIM, ISSUER-SIM scoped by environment and acquirer mandate. Principal-to-role bindings and keys are provisioned outside browser inputs. NETWORK-SIM may attest its own BEFORE_ISSUER rejection or POSSIBLE_ISSUER uncertainty; PROCESSOR-AUTH its BEFORE_NETWORK rejection or POSSIBLE_ISSUER uncertainty. Only ISSUER-SIM attests issuer decisions. NETWORK-SIM may relay an unchanged issuer-signed attestation, never replace it with its own signature and an ISSUER actor string. ACL-AUTH has no issuer-decision signing authority.
Evidence candidate envelope schemaVersion=1, environmentId, producerPrincipal, relayPrincipal (nullable), evidenceId, instructionSha256, profile=SIM-ISO87A-v1, authorizationId, transactionId, correlationId, instructionAttemptId, acquirerRef, merchantRef, terminalRef, paymentRef, generation, amountMinor, currency, STAN, RRN, DE7, sent, actor, stage, DE39, result, reason, authorizationReference (null or exact DE38), availableAt, issuerAttestation (nullable), transportReceiptId. All must equal persisted instruction/response and P4 except actual result fields. evidenceId 1–64 is immutable in (environmentId,attestingPrincipal,evidenceId); enforce one canonical content hash per identity. A reused ID with different data quarantines, no overwrite. Instruction hash binds exact original 0100 bytes, not re-encoded replay. No secrets/raw payload stored in trace; restricted journal stores normalized evidence/identity hashes and references.
Issuer attestation is a signed canonical JSON object containing every preceding bound business field except relayPrincipal/transportReceiptId/issuerAttestation, with signingPrincipal=ISSUER-SIM and keyId. Canonicalization: RFC8785 JSON, integers only for amountMinor/generation, UTF-8 ASCII strings, explicit null authorizationReference, RFC3339 UTC availableAt. Signature verification covers bytes; algorithm candidate Ed25519, provisioned issuer public key pinned per environment. Network relay authenticates independently and carries exactly this attestation plus its own receipt ID. Nonissuer attestation uses the same bound fields signed by its permitted actor principal. Invalid signature/principal/mandate fails before business evidence admission. This is a domain trust contract proposal; IAM/SigV4 boundary, key custody/rotation and crypto realization remain architecture/Security work, not completed verification.
Normal and late admission use the SAME internal processor handler contract: (a) authenticate sender/relay and verify original attestation; (b) load authorized original instruction; parse/validate response and all bound fields; (c) conditional transaction writes normalized evidence, processorReceivedAt, transportReceiptId and new business version together, or records duplicate/conflict without changing confirmed state; (d) expose committed result; (e) append sanitized observation/outbox. A candidate evidence ID need not already exist in processor storage on arrival: trusted attestation supplies it, and step (c) creates the durable processor receipt. Thus durable lookup is required to READ known evidence, not a circular prerequisite for first admission. Failed journal transaction leaves volatile candidate NOT authoritative; O3 applies. External issuer table is never queried by processor to manufacture receipt.
Late handling is an authenticated internal invocation of this same handler by NETWORK-SIM/ISSUER-SIM or explicitly authorized diagnostic evidence-delivery role carrying original signed attestation, not an operator-asserted outcome. It requires original dispatch/instruction; no public endpoint, periodic reconciliation worker or redispatch is commissioned. Arrival after deadline may reconcile UNKNOWN only after step (c); identical late evidence idempotent; contradictory signed decision quarantines and preserves established confirmation. Signed attestation without actual processor admission is issuer truth, not known processor state.

## P6 — validation phase and first-failure order
The fixed acceptance ordering is: authenticated sender/attestation verification when evidence is submitted; ASCII/body header/MTI/bitmap/secondary and required/forbidden presence; ascending field length/alphabet checks; exact end-body; DE48 known/unique/nonempty/ordered keys, individual bounds and enum checks; domain profile/amount/currency; full sent Gregorian date and DE7 match; for NEW requests freshness then <=826 exchange budget before claim; for responses P4 matrix then original instruction/context binding, exact authorization-reference binding, then conditional business conflict/duplicate admission. Decoder failures never claim domain execution. Generic safe external error may hide internal phase, but future verification records the first violated rule. Wrong op fails schema enum before correlation; malformed 1000 prefix fails DE49 alphabet, not a fictional four-digit LLL parser. Full evidence signature/attestation remains mandatory, with fixture oracles explicitly not cryptographic execution.

## P7 — C1–C3 correction integration
Use channel C4 for exact octet-stream-to-proof carriage: signed attestation additionally binds responseSha256 of exact received 0110 bytes and signed signingPrincipal/keyId. DIRECT sender and RELAY countersignature bindings, bounded sidecar/signature carriage and late envelope are mandatory candidate detail, not already approved cryptographic implementation. P5 admission order remains authenticate/verify carried proof→load original→P6 parse/bind→atomic receipt+business version→result→trace. A first evidence record is CREATED by admission, not fetched circularly. C3 channel precedence controls failure responses.
Capacity packets with prior references are isolated structural/parser/response-budget inputs only. Linked retry returns its original instruction/result without re-encoding or redispatch; parser acceptance never commissions a second send. New exchange admission <=826, structural packing <=999. Preserve the whole selected wire family P1–P6 (P2a/P3a/P5/P6 included); P7/C4 are submitted construction-detail corrections and remain PROPOSED.
