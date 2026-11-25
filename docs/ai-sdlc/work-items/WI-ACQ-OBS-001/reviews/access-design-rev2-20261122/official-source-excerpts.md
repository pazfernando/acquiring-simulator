# Official AWS documentation — bounded excerpts

Retrieved read-only 2026-11-22. These are evidence quotations, not executed AWS permission tests. No authenticated account calls occurred. Public official documentation is the specific Q3 exception to the commission's no-external-calls rule.

## Lambda Java context

URL: https://docs.aws.amazon.com/lambda/latest/dg/java-context.html
HTTP 200; fetched 2026-11-22T23:36:54.296Z; not truncated.

> `getInvokedFunctionArn()` – Returns the Amazon Resource Name (ARN) that's used to invoke the function. Indicates if the invoker specified a version number or alias.

> `getIdentity()` – (mobile apps) Returns information about the Amazon Cognito identity that authorized the request.

> `getClientContext()` – (mobile apps) Returns the client context that's provided to Lambda by the client application.

The document's example returns `arn:aws:lambda:us-east-2:123456789012:function:my-function`. The number is an AWS documentation example, not an observed account.

## IAM identifiers

URL: https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_identifiers.html
HTTP 200; fetched 2026-11-22T23:36:54.660Z; output truncated after 20,000 chars; syntax cited below was present before truncation.

> `arn:aws:iam::{{account}}:role/{{role-name-with-path}}`

> `arn:aws:sts::{{account}}:assumed-role/{{role-name}}/{{role-session-name}}`

## IAM and STS quotas

URL: https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_iam-quotas.html
HTTP 200; fetched 2026-11-22T23:36:55.088Z; not truncated.

Character-limits table: Path 512 characters; Role name 64 characters; Role session name 64 characters. These are name/path limits, not a universal total ARN maximum. Locally computed synthetic example lengths (standard aws partition, 12 account digits): one-char IAM role ARN 32; 64-char IAM role ARN 95; STS 64-char role plus 64-char session ARN 168. Every role ARN contains a slash, disallowed in the current C4 sender alphabet.

## IAM policy evaluation logic

URL: https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_evaluation-logic.html
HTTP 200; fetched 2026-11-22T23:37:02.816Z; not truncated.

> When an IAM entity (user or role) requests access to a resource within the same account, AWS evaluates all the permissions granted by the identity-based and resource-based policies. The resulting permissions are the union of the permissions of the two types. If an action is allowed by an identity-based policy, a resource-based policy, or both, then AWS allows the action. An explicit deny in either of these policies overrides the allow.

## SourceArn and SourceAccount

URL: https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_condition-keys.html#condition-keys-sourcearn
HTTP 200; fetched 2026-11-22T23:38:28.814Z. Initial response truncated at 1,000 chars. Required sections recovered by bounded Python extraction from full web-fetch spill `/tmp/openclaw-web-fetch-e6ee6f93ea6ab813.log` (166,890 characters); temporary spill is not a deliverable dependency.

SourceArn:

> Use this key to compare the Amazon Resource Name (ARN) of the resource making a service-to-service request with the ARN that you specify in the policy, but only when the request is made by an AWS service principal.

> This key does not work with the ARN of the principal making the request. Instead, use `aws:PrincipalArn`.

> Availability – This key is included in the request context only when the call to your resource is being made directly by an AWS service principal on behalf of a resource for which the configuration triggered the service-to-service request.

SourceAccount:

> Use this key to compare the account ID of the resource making a service-to-service request with the account ID that you specify in the policy, but only when the request is made by an AWS service principal.

## Lambda cross-account invocation permissions

URL: https://docs.aws.amazon.com/lambda/latest/dg/permissions-function-cross-account.html
HTTP 200; fetched 2026-11-22T23:38:28.851Z; not truncated.

> The resource-based policy grants permission for the other account to access the function, but doesn't allow users in that account to exceed their permissions. Users in the other account must have the corresponding user permissions to use the Lambda API.

The examples scope a resource grant to a target alias ARN. This is not proof that all unqualified/other-alias or same-account invocation paths are denied. No command from the documentation was executed.
