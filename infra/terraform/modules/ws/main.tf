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

############################################################
# $connect authorizer
############################################################

resource "aws_iam_role" "authorizer" {
  name               = "${var.name_prefix}-ws-authorizer"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json

  tags = var.tags
}

resource "aws_cloudwatch_log_group" "authorizer" {
  name              = "/aws/lambda/${var.name_prefix}-ws-authorizer"
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
  function_name     = "${var.name_prefix}-ws-authorizer"
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
      WS_API_AUDIENCE  = var.ws_api_audience
      DEMO_SUBJECT     = var.demo_subject
      ALLOWED_ORIGIN   = var.allowed_origin
      SUBPROTOCOL_NAME = var.subprotocol_name
    }
  }

  tags = var.tags

  depends_on = [aws_cloudwatch_log_group.authorizer]
}

############################################################
# Notifier (server push only)
############################################################

resource "aws_iam_role" "notifier" {
  name               = "${var.name_prefix}-ws-notifier"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json

  tags = var.tags
}

resource "aws_cloudwatch_log_group" "notifier" {
  name              = "/aws/lambda/${var.name_prefix}-ws-notifier"
  retention_in_days = var.log_retention_days

  tags = var.tags
}

data "aws_iam_policy_document" "notifier" {
  statement {
    sid       = "WriteOwnLogs"
    effect    = "Allow"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.notifier.arn}:*"]
  }

  statement {
    sid       = "ConnectionRecords"
    effect    = "Allow"
    actions   = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:DeleteItem", "dynamodb:Query"]
    resources = [var.connections_table_arn]
  }

  statement {
    sid       = "ManageConnectionsOnThisApiOnly"
    effect    = "Allow"
    actions   = ["execute-api:ManageConnections"]
    resources = ["arn:aws:execute-api:${var.aws_region}:${var.aws_account_id}:${aws_apigatewayv2_api.ws.id}/${var.stage_name}/POST/@connections/*"]
  }
}

resource "aws_iam_role_policy" "notifier" {
  name   = "notifier"
  role   = aws_iam_role.notifier.id
  policy = data.aws_iam_policy_document.notifier.json
}

resource "aws_iam_role_policy_attachment" "notifier_guardrail" {
  role       = aws_iam_role.notifier.name
  policy_arn = var.guardrail_policy_arn
}

resource "aws_lambda_function" "notifier" {
  function_name     = "${var.name_prefix}-ws-notifier"
  role              = aws_iam_role.notifier.arn
  handler           = var.notifier_handler
  runtime           = "java25"
  architectures     = ["arm64"]
  memory_size       = 512
  timeout           = 15
  s3_bucket         = var.notifier_artifact.bucket
  s3_key            = var.notifier_artifact.key
  s3_object_version = var.notifier_artifact.version
  source_code_hash  = var.notifier_artifact.sha256_b64

  # N-04: callbacks go through the WebSocket custom domain (the default
  # execute-api endpoint is disabled). Per the API Gateway @connections
  # documentation, a custom-domain callback URL omits the stage.
  environment {
    variables = {
      CONNECTIONS_TABLE      = var.connections_table_name
      WS_MANAGEMENT_ENDPOINT = "https://${var.ws_fqdn}"
      ENVIRONMENT            = var.name_prefix
    }
  }

  tags = var.tags

  depends_on = [aws_cloudwatch_log_group.notifier]
}

############################################################
# WebSocket API
############################################################

resource "aws_apigatewayv2_api" "ws" {
  name                       = "${var.name_prefix}-ws"
  protocol_type              = "WEBSOCKET"
  route_selection_expression = "$request.body.action"

  # N-04 (rev2 Security SEC-I3): the pinned provider (hashicorp/aws 6.74.0)
  # exposes disable_execute_api_endpoint on aws_apigatewayv2_api for both HTTP
  # and WebSocket APIs. Disabling the default host removes the secondary
  # wss://{api-id}.execute-api hostname; the custom domain remains the single
  # ingress and the $connect authorizer (token + exact Origin, TTL 0) is
  # unchanged. Notifier callbacks use the custom domain above.
  disable_execute_api_endpoint = true

  tags = var.tags
}

resource "aws_apigatewayv2_authorizer" "connect" {
  api_id          = aws_apigatewayv2_api.ws.id
  name            = "demo-ws-token"
  authorizer_type = "REQUEST"
  authorizer_uri  = aws_lambda_function.authorizer.invoke_arn
  identity_sources = [
    "route.request.header.Sec-WebSocket-Protocol",
    "route.request.header.Origin",
  ]

  # Zero cache: every handshake is re-evaluated, never inherited.
  authorizer_result_ttl_in_seconds = 0
}

resource "aws_apigatewayv2_integration" "connect_mock" {
  api_id           = aws_apigatewayv2_api.ws.id
  integration_type = "MOCK"

  request_templates = {
    "$default" = "{\"statusCode\": 200}"
  }
}

resource "aws_apigatewayv2_integration" "disconnect_mock" {
  api_id           = aws_apigatewayv2_api.ws.id
  integration_type = "MOCK"

  request_templates = {
    "$default" = "{\"statusCode\": 200}"
  }
}

resource "aws_apigatewayv2_route" "connect" {
  api_id             = aws_apigatewayv2_api.ws.id
  route_key          = "$connect"
  authorization_type = "CUSTOM"
  authorizer_id      = aws_apigatewayv2_authorizer.connect.id
  target             = "integrations/${aws_apigatewayv2_integration.connect_mock.id}"
}

resource "aws_apigatewayv2_route" "disconnect" {
  api_id             = aws_apigatewayv2_api.ws.id
  route_key          = "$disconnect"
  authorization_type = "NONE"
  target             = "integrations/${aws_apigatewayv2_integration.disconnect_mock.id}"
}

resource "aws_cloudwatch_log_group" "access" {
  name              = "/aws/apigateway/${var.name_prefix}-ws"
  retention_in_days = var.log_retention_days

  tags = var.tags
}

resource "aws_apigatewayv2_stage" "ws" {
  api_id      = aws_apigatewayv2_api.ws.id
  name        = var.stage_name
  auto_deploy = true

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.access.arn

    format = jsonencode({
      requestId    = "$context.requestId"
      ip           = "$context.identity.sourceIp"
      requestTime  = "$context.requestTime"
      routeKey     = "$context.routeKey"
      status       = "$context.status"
      connectionId = "$context.connectionId"
      eventType    = "$context.eventType"
      errorMessage = "$context.error.message"
    })
  }

  default_route_settings {
    detailed_metrics_enabled = true
    throttling_burst_limit   = var.throttle_burst_limit
    throttling_rate_limit    = var.throttle_rate_limit
  }

  tags = var.tags
}

resource "aws_lambda_permission" "authorizer" {
  statement_id  = "AllowWsAuthorizerInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.authorizer.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.ws.execution_arn}/authorizers/${aws_apigatewayv2_authorizer.connect.id}"
}

resource "aws_apigatewayv2_domain_name" "ws" {
  domain_name = var.ws_fqdn

  domain_name_configuration {
    certificate_arn = var.certificate_arn
    endpoint_type   = "REGIONAL"
    security_policy = "TLS_1_2"
  }

  tags = var.tags
}

resource "aws_apigatewayv2_api_mapping" "ws" {
  api_id      = aws_apigatewayv2_api.ws.id
  domain_name = aws_apigatewayv2_domain_name.ws.id
  stage       = aws_apigatewayv2_stage.ws.id
}
