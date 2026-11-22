# P1b5a approved NETWORK_REJECTED rule citations

Exact approved rule text for the network-rejection composer. Each line is copied verbatim from the
live specification named; historical `revisions/` copies are evidence, not the source quoted here.

## Functional guide — `docs/ai-sdlc/sources/acquiring_simulator_functional_guide.md:356`

```
| NET-01 | Mastercard rejects malformed or disallowed message before issuer dispatch | Explicit network rejection; no issuer decision | Report network/processor rejection with provenance; do not label issuer decline. |
```

## Requirements — `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/requirements.md`

### R05 — line 41

```
| R05 | SOURCED | Preserve separate processor rejection, issuer approval, issuer decline and unconfirmed outcome with actual provenance; missing/dropped/invalid response is not an issuer decline. | F §5 A, §13 NET-01–05 / ISS-01–05; I |
```

### R10 — line 46

```
| R10 | SOURCED | Preserve logical network/issuer separation and actual decision provenance; diagnostic issuer evidence cannot replace processor-known outcome. | F §13; W essential failure behavior; I |
```

### R22 — line 58

```
| R22 | DESIGN ASSUMPTION | ACL response translation validates profile, correlation and trusted provenance before yielding the allowed channel result; distinguish channel/adapter failure, explicit processor/network rejection and issuer decision. Invalid/unmapped replies after possible dispatch preserve uncertainty, not fabricated issuer decline. | A02/A03 |
```

## Acceptance scenario — `docs/ai-sdlc/work-items/WI-ACQ-OBS-001/acceptance-scenarios.md:16`

```
| OBS-03 | Valid input and explicit pre-issuer network rejection | Network rejects before forwarding | Display PROCESSOR_REJECTED with network provenance; no issuer task/decision is fabricated; skipped issuer work is identified only by evidence | R05, R10, R12, R22 |
```

## Rules the composer must therefore preserve

1. An explicit pre-issuer network rejection is committed with `DecisionSource.NETWORK` provenance and
   is never recorded as an issuer decline or issuer decision (NET-01, R05, R10).
2. `OBS-03` requires that no issuer task or issuer decision is fabricated: the committed critical event
   stays `PROCESSOR`/`business-persist`, with no `ISSUER` actor and no `issuer-decide` task row.
3. Correlation and availability are validated before anything is written; uncorrelated or
   pre-dispatch evidence fails closed rather than committing (R22).

## Known citation conflict (recorded, not resolved)

`NetworkRejectedCriticalComposer`'s class javadoc attributes the `NETWORK_REJECTION` reason category
to "the existing domain mapping" and cites `OBS-03` plus `R05/R10/R22`. NET-01/OBS-03 themselves do
not name `NETWORK_REJECTION`; the reason string is an implementation constant in the composer, not a
quoted rule. No approved document was edited to reconcile this.
