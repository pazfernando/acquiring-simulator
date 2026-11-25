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

resource "aws_iam_role" "console" {
  name               = "${var.name_prefix}-console-api"
  description        = "Console HTTP API Lambda execution role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json

  tags = var.tags
}

resource "aws_cloudwatch_log_group" "console" {
  name              = "/aws/lambda/${var.name_prefix}-console-api"
  retention_in_days = var.log_retention_days

  tags = var.tags
}

data "aws_iam_policy_document" "console" {
  statement {
    sid       = "WriteOwnLogs"
    effect    = "Allow"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.console.arn}:*"]
  }

  statement {
    sid    = "JournalReadWrite"
    effect = "Allow"
    actions = [
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:UpdateItem",
      "dynamodb:Query",
      "dynamodb:TransactWriteItems",
    ]
    resources = [var.journal_table_arn]
  }

  # N-06 (rev2 Security SEC-I5): the append-only event source is written only
  # through the same atomic TransactWriteItems as the journal. A standalone
  # PutItem on events would allow a non-atomic append path and break the
  # no-duplicate-projection invariant, so it is not granted.
  statement {
    sid    = "EventReadAppend"
    effect = "Allow"
    actions = [
      "dynamodb:GetItem",
      "dynamodb:Query",
      "dynamodb:TransactWriteItems",
    ]
    resources = [var.event_table_arn]
  }

  statement {
    sid       = "RegistryReadOnly"
    effect    = "Allow"
    actions   = ["dynamodb:GetItem", "dynamodb:Query"]
    resources = [var.registry_table_arn]
  }
}

resource "aws_iam_role_policy" "console" {
  name   = "console-api"
  role   = aws_iam_role.console.id
  policy = data.aws_iam_policy_document.console.json
}

resource "aws_iam_role_policy_attachment" "console_guardrail" {
  role       = aws_iam_role.console.name
  policy_arn = var.guardrail_policy_arn
}

resource "aws_lambda_function" "console" {
  function_name     = "${var.name_prefix}-console-api"
  description       = "Console HTTP API for the synthetic acquiring simulator"
  role              = aws_iam_role.console.arn
  handler           = var.handler
  runtime           = var.runtime
  architectures     = ["arm64"]
  memory_size       = var.memory_mb
  timeout           = var.timeout_seconds
  s3_bucket         = var.artifact.bucket
  s3_key            = var.artifact.key
  s3_object_version = var.artifact.version
  source_code_hash  = var.artifact.sha256_b64

  environment {
    variables = {
      ENVIRONMENT                = var.environment
      JOURNAL_TABLE              = var.journal_table_name
      EVENT_TABLE                = var.event_table_name
      REGISTRY_TABLE             = var.registry_table_name
      OIDC_ISSUER_URL            = var.issuer_url
      OAUTH_AUDIENCE_CONSOLE     = var.api_audience
      DEMO_SUBJECT               = var.demo_subject
      REGISTRY_CACHE_TTL_SECONDS = "30"
      HISTORY_TTL_DAYS           = "7"
      # N-02 closure (owner Option B): the boundary check compares the received
      # X-Origin-Verify against this value, byte-exactly and case-sensitively,
      # and fails closed on absent, empty, duplicated or mismatched input. The
      # application must never log the value, its length or a comparison result.
      EDGE_ORIGIN_VERIFY = var.edge_origin_verify
    }
  }

  tags = var.tags

  depends_on = [aws_cloudwatch_log_group.console]
}

resource "aws_apigatewayv2_api" "console" {
  name          = "${var.name_prefix}-console-api"
  description   = "Console API, same-origin behind CloudFront"
  protocol_type = "HTTP"

  # N-02 (rev2 Security SEC-I1): the default execute-api host must not remain a
  # second ingress. It bypasses the CloudFront edge WAF rate bound, the response
  # headers policy and the CORS shape. With this flag only the dedicated regional
  # custom domain below is invokable.
  disable_execute_api_endpoint = true

  tags = var.tags
}

# Dedicated regional custom domain used as the CloudFront /api/* origin. It is
# deliberately not the shared execute-api hostname, so the origin trust boundary
# is explicit and can later carry provider-supported mutual TLS or an
# application-level origin-verify header without re-addressing the edge.
resource "aws_apigatewayv2_domain_name" "console" {
  domain_name = var.api_fqdn

  domain_name_configuration {
    certificate_arn = var.certificate_arn
    endpoint_type   = "REGIONAL"
    security_policy = "TLS_1_2"
  }

  tags = var.tags
}

resource "aws_apigatewayv2_api_mapping" "console" {
  api_id      = aws_apigatewayv2_api.console.id
  domain_name = aws_apigatewayv2_domain_name.console.id
  stage       = aws_apigatewayv2_stage.default.id
}

resource "aws_apigatewayv2_authorizer" "jwt" {
  api_id           = aws_apigatewayv2_api.console.id
  name             = "demo-jwt"
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]

  jwt_configuration {
    audience = [var.api_audience]
    issuer   = var.issuer_url
  }
}

resource "aws_apigatewayv2_integration" "console" {
  api_id                 = aws_apigatewayv2_api.console.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.console.invoke_arn
  integration_method     = "POST"
  payload_format_version = "2.0"
  timeout_milliseconds   = 15000
}

resource "aws_apigatewayv2_route" "console_proxy" {
  api_id             = aws_apigatewayv2_api.console.id
  route_key          = "ANY /api/{proxy+}"
  target             = "integrations/${aws_apigatewayv2_integration.console.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
}

resource "aws_cloudwatch_log_group" "access" {
  name              = "/aws/apigateway/${var.name_prefix}-console-api"
  retention_in_days = var.log_retention_days

  tags = var.tags
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.console.id
  name        = "$default"
  auto_deploy = true

  # No Authorization or Sec-WebSocket-Protocol field appears in this format:
  # request headers are never logged for the console API.
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.access.arn

    format = jsonencode({
      requestId      = "$context.requestId"
      ip             = "$context.identity.sourceIp"
      requestTime    = "$context.requestTime"
      httpMethod     = "$context.httpMethod"
      routeKey       = "$context.routeKey"
      status         = "$context.status"
      protocol       = "$context.protocol"
      responseLength = "$context.responseLength"
      integrationErr = "$context.integrationErrorMessage"
    })
  }

  default_route_settings {
    detailed_metrics_enabled = true
    throttling_burst_limit   = var.throttle_burst_limit
    throttling_rate_limit    = var.throttle_rate_limit
  }

  tags = var.tags
}

resource "aws_lambda_permission" "apigw" {
  statement_id  = "AllowConsoleApiInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.console.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.console.execution_arn}/*/*"
}
