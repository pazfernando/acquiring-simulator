output "console_url" {
  description = "Public console URL (after DNS propagation)."
  value       = "https://${var.console_fqdn}"
}

output "issuer_url" {
  value = local.issuer_url
}

output "ws_url" {
  value = "wss://${var.ws_fqdn}"
}

output "acl_api_url" {
  value = "https://${var.acl_fqdn}"
}

output "processor_api_url" {
  value = "https://${var.processor_fqdn}"
}

output "console_api_origin" {
  description = "Dedicated regional custom domain used as the CloudFront /api/* origin. The default execute-api endpoint is disabled (N-02)."
  value       = module.api.origin_domain_name
}

output "console_api_endpoint_disabled" {
  description = "Confirms the console HTTP API default execute-api endpoint is disabled; it must never be used as an origin."
  value       = module.api.api_endpoint_disabled
}

output "console_distribution_id" {
  value = module.console.distribution_id
}

output "console_bucket_name" {
  value = module.console.console_bucket_name
}

output "deploy_role_arn" {
  description = "Value for the GitHub environment variable AWS_ROLE_ARN."
  value       = module.identity.deploy_role_arn
}

output "table_names" {
  value = module.data.table_names
}

output "keycloak_alb_dns_name" {
  value = module.keycloak.alb_dns_name
}

output "keycloak_service_name" {
  value = module.keycloak.service_name
}

output "alarm_names" {
  value = module.observability.alarm_names
}
