output "api_id" {
  value = aws_api_gateway_rest_api.this.id
}

output "execution_arn" {
  value = aws_api_gateway_rest_api.this.execution_arn
}

output "invoke_url" {
  value = aws_api_gateway_stage.this.invoke_url
}

output "custom_domain_name" {
  value = aws_api_gateway_domain_name.this.domain_name
}

output "regional_domain_name" {
  description = "Target for the Route 53 alias record."
  value       = aws_api_gateway_domain_name.this.regional_domain_name
}

output "regional_zone_id" {
  description = "Hosted zone id of the regional API Gateway domain."
  value       = aws_api_gateway_domain_name.this.regional_zone_id
}

output "stage_arn" {
  value = aws_api_gateway_stage.this.arn
}

output "authorizer_function_name" {
  value = aws_lambda_function.authorizer.function_name
}

output "access_log_group_name" {
  value = aws_cloudwatch_log_group.access.name
}
