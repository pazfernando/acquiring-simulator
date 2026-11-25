output "issuer_url" {
  description = "Realm issuer URL for authorizers and clients."
  value       = local.realm_url
}

output "realm_name" {
  value = var.realm_name
}

output "alb_dns_name" {
  value = aws_lb.keycloak.dns_name
}

output "alb_zone_id" {
  value = aws_lb.keycloak.zone_id
}

output "alb_arn_suffix" {
  description = "ALB suffix for CloudWatch metric dimensions."
  value       = aws_lb.keycloak.arn_suffix
}

output "cluster_name" {
  value = aws_ecs_cluster.keycloak.name
}

output "service_name" {
  value = aws_ecs_service.keycloak.name
}

output "task_definition_family" {
  value = aws_ecs_task_definition.keycloak.family
}

output "database_endpoint" {
  value     = aws_rds_cluster.keycloak.endpoint
  sensitive = true
}

output "waf_web_acl_arn" {
  value = var.create_waf ? aws_wafv2_web_acl.keycloak[0].arn : null
}
