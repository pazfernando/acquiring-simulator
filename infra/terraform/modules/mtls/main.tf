data "aws_iam_policy_document" "lambda_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "authorizer" {
  name               = "${var.name_prefix}-mtls-authorizer"
  description        = "Authorizer for the ${var.name_prefix} mTLS boundary"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json

  tags = var.tags
}

resource "aws_cloudwatch_log_group" "authorizer" {
  name              = "/aws/lambda/${var.name_prefix}-mtls-authorizer"
  retention_in_days = var.log_retention_days

  tags = var.tags
}

data "aws_iam_policy_document" "authorizer" {
  statement {
    sid       = "WriteOwnLogs"
    effect    = "Allow"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.authorizer.arn}:*"]
  }
}

resource "aws_iam_role_policy" "authorizer" {
  name   = "authorizer-logs"
  role   = aws_iam_role.authorizer.id
  policy = data.aws_iam_policy_document.authorizer.json
}

resource "aws_iam_role_policy_attachment" "authorizer_guardrail" {
  role       = aws_iam_role.authorizer.name
  policy_arn = var.guardrail_policy_arn
}

resource "aws_lambda_function" "authorizer" {
  function_name     = "${var.name_prefix}-mtls-authorizer"
  description       = "mTLS leaf + OAuth token authorizer for ${var.name_prefix}"
  role              = aws_iam_role.authorizer.arn
  handler           = var.authorizer_handler
  runtime           = "java25"
  architectures     = ["arm64"]
  memory_size       = 512
  timeout           = 10
  s3_bucket         = var.authorizer_artifact.bucket
  s3_key            = var.authorizer_artifact.key
  s3_object_version = var.authorizer_artifact.version
  source_code_hash  = var.authorizer_artifact.sha256_b64

  environment {
    variables = {
      BOUNDARY          = var.name_prefix
      EXPECTED_AUDIENCE = var.expected_audience
      REQUIRED_SCOPE    = var.required_scope
    }
  }

  tags = var.tags

  depends_on = [aws_cloudwatch_log_group.authorizer]
}

resource "aws_api_gateway_rest_api" "this" {
  name        = "${var.name_prefix}-mtls"
  description = "mTLS + OAuth boundary for ${var.name_prefix}"

  endpoint_configuration {
    types = ["REGIONAL"]
  }

  # The default execute-api endpoint is not an admissible ingress for a
  # certificate-bound boundary.
  disable_execute_api_endpoint = true

  tags = var.tags
}

resource "aws_api_gateway_resource" "proxy" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  parent_id   = aws_api_gateway_rest_api.this.root_resource_id
  path_part   = "{proxy+}"
}

resource "aws_api_gateway_authorizer" "mtls" {
  name                             = "mtls-oauth"
  rest_api_id                      = aws_api_gateway_rest_api.this.id
  type                             = "REQUEST"
  authorizer_uri                   = aws_lambda_function.authorizer.invoke_arn
  identity_source                  = "method.request.header.Authorization"
  authorizer_result_ttl_in_seconds = 0
}

resource "aws_api_gateway_method" "proxy_any" {
  rest_api_id        = aws_api_gateway_rest_api.this.id
  resource_id        = aws_api_gateway_resource.proxy.id
  http_method        = "ANY"
  authorization      = "CUSTOM"
  authorizer_id      = aws_api_gateway_authorizer.mtls.id
  request_parameters = { "method.request.header.Authorization" = true }
}

resource "aws_api_gateway_integration" "proxy" {
  rest_api_id             = aws_api_gateway_rest_api.this.id
  resource_id             = aws_api_gateway_resource.proxy.id
  http_method             = aws_api_gateway_method.proxy_any.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = var.backend_lambda_invoke_arn
  timeout_milliseconds    = 29000
}

resource "aws_cloudwatch_log_group" "access" {
  name              = "/aws/apigateway/${var.name_prefix}-mtls"
  retention_in_days = var.log_retention_days

  tags = var.tags
}

resource "aws_api_gateway_deployment" "this" {
  rest_api_id = aws_api_gateway_rest_api.this.id

  triggers = {
    redeployment = sha1(jsonencode([
      aws_api_gateway_resource.proxy.id,
      aws_api_gateway_method.proxy_any.id,
      aws_api_gateway_integration.proxy.id,
      aws_api_gateway_authorizer.mtls.id,
    ]))
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_api_gateway_stage" "this" {
  rest_api_id   = aws_api_gateway_rest_api.this.id
  deployment_id = aws_api_gateway_deployment.this.id
  stage_name    = var.stage_name

  # No Authorization header is included in the access-log format.
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.access.arn

    format = jsonencode({
      requestId      = "$context.requestId"
      ip             = "$context.identity.sourceIp"
      requestTime    = "$context.requestTime"
      httpMethod     = "$context.httpMethod"
      resourcePath   = "$context.resourcePath"
      status         = "$context.status"
      responseLength = "$context.responseLength"
      errorMessage   = "$context.error.message"
    })
  }

  tags = var.tags
}

resource "aws_api_gateway_method_settings" "this" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  stage_name  = aws_api_gateway_stage.this.stage_name
  method_path = "*/*"

  settings {
    throttling_burst_limit = var.throttle_burst_limit
    throttling_rate_limit  = var.throttle_rate_limit
    metrics_enabled        = true
  }
}

resource "aws_lambda_permission" "authorizer" {
  statement_id  = "AllowAuthorizerInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.authorizer.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.this.execution_arn}/authorizers/${aws_api_gateway_authorizer.mtls.id}"
}

resource "aws_lambda_permission" "backend" {
  statement_id  = "AllowBackendInvoke"
  action        = "lambda:InvokeFunction"
  function_name = var.backend_lambda_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.this.execution_arn}/*/*"
}

resource "aws_api_gateway_domain_name" "this" {
  domain_name              = var.api_fqdn
  regional_certificate_arn = var.certificate_arn
  security_policy          = "TLS_1_2"

  endpoint_configuration {
    types = ["REGIONAL"]
  }

  mutual_tls_authentication {
    truststore_uri     = var.truststore_uri
    truststore_version = var.truststore_version
  }

  tags = var.tags
}

resource "aws_api_gateway_base_path_mapping" "this" {
  api_id      = aws_api_gateway_rest_api.this.id
  stage_name  = aws_api_gateway_stage.this.stage_name
  domain_name = aws_api_gateway_domain_name.this.domain_name
}
