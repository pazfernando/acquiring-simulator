# ISO 8583 public-reference research and simulator profile recommendation

Date: 2026-11-18 America/Bogota (retrieval 2026-11-19 UTC).
Status: RESEARCH COMPLETE; profile candidate PROPOSED, not approved or implemented.
Owner direction: investigate public good-practice message structures and use a reference rather than ask the owner to invent ISO field definitions. This authorizes research/specification, not deployment or architecture self-approval.

## Findings and source authority

1. **jPOS reference packager:** `research-sources/jpos-iso87ascii.xml`, upstream commit `5ef6c9be0fce8c4c00415b05ee443f4178f8112b`, explicitly describes ISO 8583:1987 ASCII. It supplies executable field packing definitions, not a complete network business/interface contract. Recommended technical baseline, consistent with the existing Java stack; using its structure does not itself select the jPOS runtime/dependency or license disposition.
2. **jPOS official GenericPackager documentation:** https://jpos.org/docs/tutorial/iso-packager/generic-packager/ documents field declarations and distinct ISO87 ASCII/binary, ISO93 and network-specific packagers. https://jpos.org/docs/tutorial/iso-packager/ describes non-self-describing byte streams. The XML/source is preferred to illustrative prose for exact field widths. Context7 `/jpos/jpos` corroborates field definitions and separate channel framing; it is supplementary, not normative.
3. **pyiso8583 official documentation:** https://pyiso8583.readthedocs.io/en/latest/specifications.html supplies configurable field dictionaries (data encoding, length encoding/type/count, maximum length, padding). Useful independent comparison and potential future codec cross-check, not a dependency proposal. A starter dictionary does not establish mandatory fields or universal scheme semantics.
4. **ISO catalogue:** search located https://www.iso.org/standard/79451.html, ISO 8583:2023. Its indexed abstract describes a common interface and structure; direct fetch returned 403. Full normative text was NOT obtained or reviewed. ISO87 is therefore a deliberate legacy reference profile for the simulator, not the newest ISO edition or a claim of compliance with 2023.

Fetched source bytes, URLs, timestamps, upstream revision where available and SHA-256 identities are in `research-sources/manifest.json`. Public source snapshots remain research evidence, not executable instructions. The sources are not network certification specifications.

### Important incompatibility found

Do not merge catalogs: jPOS iso87ascii defines DE55 as reserved ISO, LLLCHAR maximum 999; pyiso8583's default sample defines DE55 as binary ICC data maximum 255. DE54 also differs in maximum length (120 vs 240). These examples prove a named/versioned profile is necessary. Keep DE55 out of the initial simulator profile; existing synthetic CP indicators can use an explicitly private extension, not fabricated EMV evidence.

## Recommended profile family

Name candidate: **SIM-ISO87A-v1**. Adopt the pinned jPOS ISO87 ASCII field packing as baseline with a strict allowlist and an explicitly simulator-private extension. Preserve semantic domain models independent of the wire codec.

Wire anatomy: four ASCII MTI digits + primary bitmap as 16 hexadecimal ASCII characters + fields in ascending DE order. If future allowed fields exceed 64, set primary bit 1 and carry a secondary bitmap; initial A candidate uses only fields below 65 and rejects undeclared fields. Fixed numeric fields are zero padded; fixed character fields have explicit right-space padding; reject overflow, invalid characters and trailing bytes, never silently truncate. Variable fields use ASCII decimal length prefixes: LLVAR two digits, LLLVAR three. Encoding/length validation is necessary but not sufficient: requiredness, value constraints and business validation belong to the profile.

### Message family recommendation

| Capability | Recommendation | Boundary |
| --- | --- | --- |
| Authorization | 0100 request / 0110 response | Common examples in product guide section 3; selected simulator convention, not a scheme mandate |
| Reversal | Reserve future 0400/0410 request/response profile; evaluate 0420/0430 advice only when semantics require it | Not implemented/commissioned here; original linkage, amounts and repeat behavior require its own reviewed profile |
| Transaction status lookup | Keep an explicit simulator query contract until a reviewed private mapping is selected | Never use 0800/0810 as generic transaction-status inquiry |
| Network management | 0800/0810 only if later network management is commissioned | Not payment approval, not transaction lookup |
| Other future capabilities | Declare a profile or justified non-ISO control interface on activation | Shared trace hook applies regardless; do not force every auxiliary task into a financial message |

Do not switch A to 0200/0210 merely because examples call it a purchase: the existing scope is authorization only, not financial presentment/posting.

### A field subset to develop into a normative simulator contract

The formats below are verified from the pinned jPOS XML. Requiredness and values are proposals, not supplied by that XML.

| DE | Reference format | Proposed use / disposition |
| --- | --- | --- |
| 3 | numeric 6 | Processing intent; candidate purchase code 000000 is a simulator choice |
| 4 | numeric 12 | Amount in minor units; USD 10.00 becomes 000000001000; existing allowed amount range still applies |
| 7 | numeric 10 | Transmission MMDDhhmmss; propose UTC, with full year/time stored separately for observation |
| 11 | numeric 6 | STAN; never globally unique transaction ID or the sole duplicate key |
| 22 | numeric 3 | POS entry mode; exact synthetic-fixture code mapping still to specify |
| 32 | numeric LLVAR max 11 | Acquirer numeric wire alias; registry maps approved domain identity, never caller-chosen authority |
| 37 | character 12 | Retrieval reference; exact generator and collision scope still to specify |
| 38 | character 6 | Authorization reference in confirmed approval response only; not stand-in evidence |
| 39 | character 2 | Response code; code table must preserve provenance and uncertainty |
| 41 | character 8 | Terminal wire alias mapped to existing terminalRef, without truncation |
| 42 | character 15 | Merchant wire alias mapped to existing merchantRef, without truncation |
| 48 | character LLLVAR max 999 | Versioned, private simulator extension: fixture reference, domain/correlation/idempotency references and permitted synthetic metadata |
| 49 | character 3 | Numeric currency representation; recommend 840 for existing USD profile |

Do NOT stuff `SIM-APP-001` into numeric DE2/PAN. Recommend omitting DE2 in this explicitly private synthetic profile and using an allowlisted fixture reference in DE48; this is a deliberate simulator restriction, not a realistic card-network claim. No track data, PIN block, real cryptogram or MAC emulation is introduced. Transport authentication/integrity remains mandatory and separate.

DE48 needs a canonical, versioned sub-schema, bounded lengths and validated semantics before construction. Raw DE48/ISO bodies must not be copied into trace/history stores. Existing long terminalRef/merchantRef values require registry aliases; truncation would break identity/isolation. Cross-boundary correlation and original business key must survive adaptation and restarts; STAN/RRN alone are insufficient.

### Outcomes and response mapping

Candidate DE39 values: 00 for a verified approval; 05 for a deliberate generic issuer-decline fixture; 30 for a verified pre-dispatch format rejection; 91 for an unavailable/uncertain downstream result. These are proposed simulation mappings, not universal network rules. A timeout can also produce no ISO response; it is never evidence of an issuer decline. DE39 alone cannot prove the decision maker or whether dispatch occurred.

Preserve APPROVED, DECLINED, PROCESSOR_REJECTED and NOT_CONFIRMED semantics through a mandatory private result/provenance extension or trusted adapter metadata; malformed, mismatched or unknown replies cannot confirm payment. The complete mandatory-field matrix, DE48 result schema, response codes and no-response behavior need independent review and exact request/response vectors before this recommendation becomes a construction-ready contract.

### Transport recommendation compatible with current AWS shape

Separate message packing from transport. Recommend a bounded ISO byte body over an authenticated HTTPS internal boundary (one message per request/response, application/octet-stream), leveraging the existing HTTP/Lambda architecture rather than introducing long-lived TCP sessions only to mimic a POS network. This is an architecture proposal, not a jPOS/ISO mandate. API Gateway/Lambda binary-body handling, caller context, endpoint/version, maximum size and error handling must be explicitly specified and verified. Do not add TCP length prefixes/TPDU inside that HTTP body. If a later real integration requires TCP, select and test its own framing/channel adapter separately.

Browser input can remain versioned structured JSON to the ACL; ACL-to-simulator must contain actual packed ISO bytes, not merely JSON with ISO field labels. The simulator boundary unpacks/validates before invoking existing business components. This does not require every internal component-to-component call to use ISO. Confirm that boundary placement in the architecture decision.

## Verification plan / remaining work

No production code or codec was executed in this research. Source definitions and contrasts were inspected; no round-trip/interoperability claim is made.

Before construction: independent domain review; complete channel schema, per-MTI mandatory/conditional field matrix, aliases, DE48 schema, code tables, exact transport/identity contract and max lengths; fixed golden byte vectors; positive round-trip and negative malformed/overflow/bitmap/trailing-byte tests; duplicates, timeout, late evidence and provenance preservation. Where an independent codec is used, configure the same profile rather than trust its built-in default.

This answers the reference-selection investigation. It does not resolve other console decisions such as trace admission failure policy, retention/access or freshness objectives. Human architecture approval, independent QA and triggered Security remain pending. The cross-cutting hook obligation extends beyond A; only the A wire subset is concretely proposed now because future business operation semantics are not yet commissioned.
