# Identity and authority map — candidate
All named example URLs/subjects/environments are synthetic fixture values, not deployment configuration.

| Actor/credential | Issuance / accepted surface | Authority and explicit rejection |
|---|---|---|
| demo subject / console-demo HTTP access | public code+PKCE; scope openid console.demo; sole console resource aud | HTTP explicit submit and authorized retained reads; never WS, service hop or C4 |
| SAME demo subject / console-demo WS access | second code+PKCE prompt=none (interactive on login_required); openid ws.observe; sole WS resource aud | observation connect only; never HTTP history/payment/evidence |
| svc-console | confidential private_key_jwt RS256 + mTLS; ACL audience console.dispatch | console→ACL current subject/service mandate intersection via immutable mandateRef; no issuer proof |
| svc-acl | confidential private_key_jwt RS256 + mTLS; simulator audience sim.iso87a.authorize | ACL→processor original invocation; no browser login |
| svc-console-ws / svc-console-relay | deleted in candidate, grants/keys/registry entries retired | rejected even if recreated/signed/right aud+scope; not an accepted token class |
| notifier execution role | AWS IAM ManageConnections exact API/stage | callback transport only, never original send/result authority |
| permitted internal proof sender | C4/P5 Ed25519 + existing restricted internal admission | original correlated evidence only; no console invocation or browser route |
| merchant/acquirer/terminal | business context, NOT authenticated human | current registry limits; cannot select subject/environment |

OIDC issuer realm is acq-<environment>; actual issuer/audience/origin/subject are supplied deployment configuration. Both optional client-scope names exactly match requested scope values console.demo/ws.observe; no console-api/ws-observe alias is accepted. Resource audience mapping is optional, never default. Retired client migration is a reviewed configuration operation, not performed here. Workload IAM, user JWT, service OAuth/mTLS, mandate record, cursor signature and proof signature remain separate authority systems.

Mandate record (D8/G4): console-only conditional immutable create; ACL strong read; current registry intersection before claim/disclosure; bodySha256 is exact decoded unchanged C1 body bytes. Fields: mandateRef,assertingClient,issuer,azp,sub,environment,registryMandateId,registryVersion,acquirerRef,merchantRef,terminalRef,operation,correlationId,bodySha256,verifiedAt,validUntil. Internal metadata carries only mandateRef alongside unchanged C1 body. No token/raw body stored. ValidUntil<=HTTP token expiry; same reference cannot change fields, safe replay within validity allowed. New authentication can issue fresh reference for SAME pinned payment retry, not new send. Public supplied same-named header has no authority.
