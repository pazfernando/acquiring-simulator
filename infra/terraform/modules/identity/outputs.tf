output "deploy_role_arn" {
  description = "Role ARN to configure as the GitHub environment variable AWS_ROLE_ARN."
  value       = local.deploy_role_arn
}

output "deploy_role_name" {
  value = local.deploy_role_name
}

output "oidc_provider_arn" {
  value = local.oidc_provider_arn
}

output "runtime_guardrail_policy_arn" {
  description = "Policy ARN to attach to every runtime execution role."
  value       = aws_iam_policy.runtime_guardrail.arn
}
