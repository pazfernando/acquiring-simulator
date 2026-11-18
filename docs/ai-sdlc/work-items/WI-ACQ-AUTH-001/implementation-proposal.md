> Superseded for construction by architecture-decision.md, approved 2026-11-18. In particular PostgreSQL is required for test/production environments; SQLite is only for local testing. The prior proposal below is retained as rationale, not current pending approval.

# Operation A — implementation proposal

Date: 2026-11-18. Status: PROPOSED; not architecture approval, code, domain PASS or security clearance.
Owner of decisions: Fernando Paz. Prepared by Coordinator for review.

## Pinned inputs

- requirements.md r3: MD5 b0c97974197a20dff1b72d38164d0e2b.
- simulator-profile.md: MD5 5483a4a378c0d1101391c11a628d2ae6.
- acceptance-scenarios.md, corrected A19/A25: MD5 307922ad99e7468d53e687587a670750.
- domain-review.md: MD5 6da7c386050eeedaecd63e67c9becf9d; PASS for the pinned functional bundle plus HD-04; F02/F04 closed. Architecture and Security are not approved.

## Decisions and remaining approvals

1. APPROVED by Fernando under HD-04: P01–P07 in simulator-profile.md as executable simulator policy. This includes synthetic credential fixtures, precision/enums/defaults, resolved equality, prior-link validation precedence, atomic duplicate suppression, expiry boundary and confirmation ordering.
2. APPROVED by Fernando under HD-04: P08 for a synthetic local exercise only: business outcome/reference to callers; internal provenance/evidence restricted to operators; retain links/evidence for the exercise, no automatic purge. Exact field allowlist below is a proposal, not a claim of security clearance.
3. Approve or replace the technical design below before construction.
4. Base branch main selected; remote deferred by Fernando (HD-06). Proposed local checkout /home/pazfernando/projects/acquiring-simulator; commit identity remains to verify. No provider or remote creation is authorized.

## Proposed first vertical slice

Java with Spring Boot 4.1.2 (latest stable observed at Spring Initializr on 2026-11-18), proposed Java 25 LTS/Maven, SQLite local persistence and deterministic automated tests; no web service, network listener, UI or external protocol in this slice. Java/Spring Boot is owner-selected (HD-05); Java toolchain/build/storage details remain proposed, not existing repository conventions. If an existing repository is selected, reconcile its conventions before architecture approval.

Use an in-process application interface receiving a trusted authorized context and a typed purchase request. The context must come from a trusted local test harness, never from request-supplied identity claims. A local exercise harness uses synthetic fixtures only. This is not production authentication or an externally exposed API.

- `authorize(context, request)`: four distinguishable outcomes: approved, issuer-declined, processor-rejected, not-confirmed; response contains only outcome and an authorized transaction reference where applicable. No internal history/reasons/foreign references returned.
- Internal simulated evidence and timer handlers: accept correlated simulated issuer evidence or timeout; not caller-accessible payment operations. They do not add Operation B or C.
- Inject a clock and deterministic issuer simulator to exercise the 30-second default without wall-clock sleeps. Approve/decline/timeout fixtures and permitted routes follow the owner-approved profile only.
- No live financial effects, real credentials, automatic redispatch/reversal, unsolicited late notification or scheme conformance claims.

## Runtime configuration

Use application.properties and validated typed configuration for simulator defaults and fixture route mappings. Test-only overrides/data belong under test resources; operational simulator fixtures are not merely test mocks. Markdown is decision evidence, never runtime configuration. No Spring web starter/listener in this proposed first slice.

## State and transactional behavior

Persist authorization records, active business-key generations, retained prior-reference links and append-only evidence/history. Proposed internal lifecycle: received -> rejected-before-dispatch or awaiting; awaiting -> approved/declined/not-confirmed; not-confirmed -> approved/declined on valid late evidence. Confirmed outcomes never regress to unknown. Contradictory confirmations are retained and flagged without replacing an established result.

Use short SQLite transactions to claim a business key and generation and persist the dispatch claim before invoking the in-process simulator; serialize concurrent claims with uniqueness constraints. Expiry creates a new unlinked generation, not deletion of prior links or records. Linked retries always resolve the original generation. Do not hold a write transaction while waiting for simulated downstream evidence.

A crash after dispatch claim may leave an unconfirmed operation; never automatically redispatch it on restart. Preserve ambiguity rather than inventing a confirmed result. The first slice does not promise exactly-once execution across external systems.

## Synthetic data and operator boundary

Allowlist stored fields: scoped synthetic acquirer/merchant/terminal identifiers, business/exchange/prior references, amount in integer cents and USD currency, symbolic credential ID, CP/risk fixture values, selected synthetic route, internal decision provenance/reason category, dispatch/correlation identifiers, timestamps and normalized simulator evidence. Do not store arbitrary raw request bodies, PAN/PIN/CVV, real cryptograms, tokens or secrets.

Caller response allowlist: business outcome and authorized transaction reference only. Operator inspection is a local trusted exercise function, not a public endpoint. Use owner-only local database file permissions and no request/evidence stdout logging by default. Local OS access is the proposed trust boundary, not multi-user production access control. No automatic purge during the exercise. Independent Security review is required for these interface/data/access choices; owner approval does not substitute for it. Wider interfaces, production retention, encryption and authentication require a separately approved design.

## Verification and handoff

Implementation must map all 24 v1 acceptance scenarios (A01–A09, A12–A26) to meaningful tests, including the corrected A19/A25 oracles. A10/A11 remain deferred. Exercise concurrency, key expiry and retained links, crash/restart ambiguity, invalid/foreign links, isolation, late evidence, timeout-boundary ordering and caller disclosure. Use separate temporary databases and a controlled clock. Record exact commands and observed results; none have run yet.

Before implementation: record owner dispositions, align spec/profile/scenarios as necessary, and obtain fresh independent domain review of the exact resulting hashes. Preserve prior reviews. Domain PASS does not approve architecture.

Delegate to ai-sdlc-implementation only after repository and architecture gates, with explicit external checkout/task branch and no `model` argument. Use its configured default model; record the actually observed model/harness. If the required native harness cannot use that default, report the incompatibility without a model override or shell fallback. No push, remote creation, PR, deployment or release is authorized.

QA and Security specialists are not installed in the current roster. Independent verification/security gates require a suitable independent reviewer and remain pending; developer tests are not those approvals. Do not mark the work Verified or Accepted without evidence.

## Authority update

See owner-decisions-2026-11-18.md for P01–P08 approval, Java/Spring selection and main/remote direction. Technical design beyond the selected stack remains PROPOSED. Earlier Python proposal preserved in revisions/.
