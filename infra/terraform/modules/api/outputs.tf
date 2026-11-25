output "api_id" {
  value = aws_apigatewayv2_api.console.id
}

output "origin_domain_name" {
  description = "Dedicated regional custom domain used as the CloudFront /api/* origin. The default execute-api host is disabled (N-02)."
  value       = aws_apigatewayv2_domain_name.console.domain_name
}

output "regional_domain_name" {
  description = "Regional custom-domain target used for the Route 53 alias record."
  value       = aws_apigatewayv2_domain_name.console.domain_name_configuration[0].target_domain_name
}

output "regional_zone_id" {
  value = aws_apigatewayv2_domain_name.console.domain_name_configuration[0].hosted_zone_id
}

output "api_endpoint_disabled" {
  description = "True when the default execute-api endpoint is disabled (it must not be used as an origin)."
  value       = aws_apigatewayv2_api.console.disable_execute_api_endpoint
}

output "api_arn" {
  value = aws_apigatewayv2_api.console.arn
}

output "stage_arn" {
  value = aws_apigatewayv2_stage.default.arn
}

output "lambda_function_name" {
  value = aws_lambda_function.console.function_name
}

output "log_group_name" {
  value = aws_cloudwatch_log_group.access.name
}
