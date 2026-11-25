output "api_id" {
  value = aws_apigatewayv2_api.ws.id
}

output "api_arn" {
  value = aws_apigatewayv2_api.ws.arn
}

output "execution_arn" {
  value = aws_apigatewayv2_api.ws.execution_arn
}

output "stage_arn" {
  value = aws_apigatewayv2_stage.ws.arn
}

output "ws_endpoint" {
  value = "wss://${var.ws_fqdn}"
}

output "target_domain_name" {
  description = "Target for the Route 53 alias record."
  value       = aws_apigatewayv2_domain_name.ws.domain_name_configuration[0].target_domain_name
}

output "hosted_zone_id" {
  value = aws_apigatewayv2_domain_name.ws.domain_name_configuration[0].hosted_zone_id
}

output "authorizer_id" {
  value = aws_apigatewayv2_authorizer.connect.id
}

output "notifier_function_name" {
  value = aws_lambda_function.notifier.function_name
}

output "authorizer_function_name" {
  value = aws_lambda_function.authorizer.function_name
}

output "access_log_group_name" {
  value = aws_cloudwatch_log_group.access.name
}
