locals {
  console_origin_id = "console-s3"
  api_origin_id     = "console-api"

  # N-02 closure (owner Option B, 2026-11-23): fixed header name CloudFront adds
  # to every /api/* origin request. Not in the CloudFront disallowed-custom-header
  # list (Cache-Control, Connection, Content-Length, Cookie, Host, If-*,
  # Max-Forwards, Pragma, Proxy-*, Range, Request-Range, TE, Trailer,
  # Transfer-Encoding, Upgrade, Via, X-Amz-*, X-Edge-*, X-Real-Ip) and not a
  # header the application treats semantically. Implementation must compare
  # against this exact name; policy check 18 fails any drift.
  origin_verify_header = "X-Origin-Verify"

  csp = join("; ", [
    "default-src 'self'",
    "connect-src 'self' https://${var.auth_fqdn} https://${var.ws_fqdn}",
    "img-src 'self' data:",
    "script-src 'self'",
    "style-src 'self'",
    "frame-ancestors 'none'",
    "form-action 'self' https://${var.auth_fqdn}",
    "base-uri 'none'",
    "object-src 'none'",
  ])
}

############################################################
# Private SPA bucket (us-east-2, default provider)
############################################################

resource "aws_s3_bucket" "console" {
  bucket        = var.console_bucket_name
  force_destroy = false

  tags = merge(var.tags, { "Name" = var.console_bucket_name })
}

resource "aws_s3_bucket_public_access_block" "console" {
  bucket = aws_s3_bucket.console.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "console" {
  bucket = aws_s3_bucket.console.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_versioning" "console" {
  bucket = aws_s3_bucket.console.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "console" {
  bucket = aws_s3_bucket.console.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = false
  }
}

############################################################
# CloudFront (viewer certificate and WAF must be us-east-1)
############################################################

resource "aws_cloudfront_origin_access_control" "console" {
  provider = aws.us_east_1

  name                              = "${var.name_prefix}-console-oac"
  description                       = "OAC for the private console bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

############################################################
# SPA deep-link fallback (viewer-request, static behavior only)
#
# N-03 (rev2 Security SEC-I2): the previous distribution-wide
# custom_error_response blocks rewrote origin 403/404 to 200 + index.html, which
# masked the API contract (401/403/404 JSON bodies) and authorization-denial
# telemetry for every /api/* response. Error rewriting is removed. Deep links
# are instead resolved before the origin request, and only for non-/api/* paths.
############################################################

resource "aws_cloudfront_function" "spa_fallback" {
  provider = aws.us_east_1

  name    = "${var.name_prefix}-spa-fallback"
  comment = "Rewrite extensionless non-API paths to index.html; never touch /api/*"
  runtime = "cloudfront-js-2.0"
  publish = true

  code = <<-EOT
    function handler(event) {
      var request = event.request;
      var uri = request.uri;

      // API responses keep their own status and JSON body (no SPA rewrite).
      if (uri === '/api' || uri.indexOf('/api/') === 0) {
        return request;
      }

      // Only extensionless paths are application routes; assets keep their
      // real 403/404 from the private origin instead of a fake 200.
      var lastSegment = uri.substring(uri.lastIndexOf('/') + 1);
      if (lastSegment.indexOf('.') === -1) {
        request.uri = '/index.html';
      }

      return request;
    }
  EOT
}

resource "aws_cloudfront_cache_policy" "s3_optimized" {
  provider = aws.us_east_1

  name        = "${var.name_prefix}-console-static"
  comment     = "Static SPA caching"
  default_ttl = 3600
  max_ttl     = 86400
  min_ttl     = 0

  parameters_in_cache_key_and_forwarded_to_origin {
    cookies_config {
      cookie_behavior = "none"
    }
    headers_config {
      header_behavior = "none"
    }
    query_strings_config {
      query_string_behavior = "none"
    }
    enable_accept_encoding_brotli = true
    enable_accept_encoding_gzip   = true
  }
}

resource "aws_cloudfront_cache_policy" "api_disabled" {
  provider = aws.us_east_1

  name        = "${var.name_prefix}-console-api-disabled"
  comment     = "No caching for console API responses"
  default_ttl = 0
  max_ttl     = 0
  min_ttl     = 0

  parameters_in_cache_key_and_forwarded_to_origin {
    cookies_config {
      cookie_behavior = "none"
    }
    headers_config {
      header_behavior = "none"
    }
    query_strings_config {
      query_string_behavior = "all"
    }
    enable_accept_encoding_brotli = true
    enable_accept_encoding_gzip   = true
  }
}

resource "aws_cloudfront_origin_request_policy" "api_all_viewer_except_host" {
  provider = aws.us_east_1

  name    = "${var.name_prefix}-console-api-origin-request"
  comment = "Forward viewer headers and query strings except Host"

  cookies_config {
    cookie_behavior = "none"
  }

  headers_config {
    header_behavior = "allExcept"

    headers {
      items = ["Host"]
    }
  }

  query_strings_config {
    query_string_behavior = "all"
  }
}

resource "aws_cloudfront_response_headers_policy" "console" {
  provider = aws.us_east_1

  name    = "${var.name_prefix}-console-headers"
  comment = "Approved CSP and security headers (design rev4 section 2.4)"

  security_headers_config {
    content_security_policy {
      override                = true
      content_security_policy = local.csp
    }

    content_type_options {
      override = true
    }

    frame_options {
      frame_option = "DENY"
      override     = true
    }

    referrer_policy {
      referrer_policy = "no-referrer"
      override        = true
    }

    strict_transport_security {
      access_control_max_age_sec = 31536000
      include_subdomains         = true
      preload                    = false
      override                   = true
    }
  }

  custom_headers_config {
    items {
      header   = "Permissions-Policy"
      value    = "camera=(), microphone=(), geolocation=(), payment=()"
      override = true
    }
    items {
      header   = "X-Permitted-Cross-Domain-Policies"
      value    = "none"
      override = true
    }
  }
}

resource "aws_cloudfront_distribution" "console" {
  provider = aws.us_east_1

  enabled             = true
  is_ipv6_enabled     = true
  comment             = "${var.name_prefix} console"
  default_root_object = "index.html"
  aliases             = [var.console_fqdn]
  price_class         = var.price_class
  web_acl_id          = aws_wafv2_web_acl.console.arn

  origin {
    origin_id                = local.console_origin_id
    domain_name              = aws_s3_bucket.console.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.console.id
  }

  origin {
    origin_id   = local.api_origin_id
    domain_name = var.http_api_domain_name

    custom_origin_config {
      http_port                = 80
      https_port               = 443
      origin_protocol_policy   = "https-only"
      origin_ssl_protocols     = ["TLSv1.2"]
      origin_read_timeout      = 30
      origin_keepalive_timeout = 5
    }

    # N-02 closure (owner Option B). CloudFront adds this header to every origin
    # request; if a viewer sends a header with the same name, CloudFront
    # overwrites the value before forwarding (AWS developer guide, "Add custom
    # headers to origin requests": "If a header is present, CloudFront
    # overwrites the header value before forwarding the request to the origin").
    # The value is Terraform-generated, exists only in encrypted remote state
    # and in this origin configuration, and is marked sensitive so plans redact
    # it. The console API boundary rejects any request whose header is absent
    # or mismatched (contract delta + Implementation check, fail-closed).
    custom_header {
      name  = local.origin_verify_header
      value = var.origin_verify_secret
    }
  }

  default_cache_behavior {
    target_origin_id           = local.console_origin_id
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = aws_cloudfront_cache_policy.s3_optimized.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.console.id
    compress                   = true

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_fallback.arn
    }
  }

  ordered_cache_behavior {
    path_pattern               = "/api/*"
    target_origin_id           = local.api_origin_id
    viewer_protocol_policy     = "https-only"
    allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = aws_cloudfront_cache_policy.api_disabled.id
    origin_request_policy_id   = aws_cloudfront_origin_request_policy.api_all_viewer_except_host.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.console.id
    compress                   = true
  }

  # No custom_error_response blocks: CloudFront error mapping is
  # distribution-wide and cannot be scoped to the static behavior. API 400/
  # 401/403/404/410/503 must reach the viewer unchanged (contract D-6/R-2).

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = var.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  # CloudFront standard (legacy) logging is intentionally not configured here:
  # it writes through bucket ACLs, which conflicts with BucketOwnerEnforced on
  # the private console bucket. Request-level visibility is provided by WAF
  # logging with Authorization / Sec-WebSocket-Protocol redacted. Recorded as a
  # missing check in the delivery report rather than shipped half-working.

  tags = var.tags
}

data "aws_iam_policy_document" "console_bucket" {
  statement {
    sid       = "AllowCloudFrontServicePrincipalReadOnly"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.console.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.console.arn]
    }
  }

  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.console.arn, "${aws_s3_bucket.console.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "console" {
  bucket = aws_s3_bucket.console.id
  policy = data.aws_iam_policy_document.console_bucket.json
}

############################################################
# Runtime configuration object (public values only)
############################################################

resource "aws_s3_object" "config" {
  bucket       = aws_s3_bucket.console.id
  key          = "config.json"
  content_type = "application/json"

  content = jsonencode({
    issuer                = var.issuer_url
    clientId              = var.console_client_id
    redirectUri           = "https://${var.console_fqdn}/auth/callback"
    apiBaseUrl            = "/api"
    apiAudience           = var.console_audience
    wsUrl                 = "wss://${var.ws_fqdn}"
    wsSubprotocol         = var.ws_subprotocol
    accessTokenTtlSeconds = 900
  })

  tags = var.tags
}

############################################################
# WAF (CLOUDFRONT scope, us-east-1)
############################################################

resource "aws_cloudwatch_log_group" "waf" {
  provider = aws.us_east_1

  name              = "aws-waf-logs-${var.name_prefix}-console"
  retention_in_days = var.log_retention_days

  tags = var.tags
}

data "aws_iam_policy_document" "waf_logs" {
  statement {
    sid    = "AWSWAFLogDelivery"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["delivery.logs.amazonaws.com"]
    }

    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.waf.arn}:*"]

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [var.aws_account_id]
    }
  }
}

resource "aws_cloudwatch_log_resource_policy" "waf" {
  provider = aws.us_east_1

  policy_name     = "${var.name_prefix}-waf-logs"
  policy_document = data.aws_iam_policy_document.waf_logs.json
}

resource "aws_wafv2_web_acl" "console" {
  provider = aws.us_east_1

  name        = "${var.name_prefix}-console"
  description = "Console edge rules: AWS managed baseline and /api rate bound"
  scope       = "CLOUDFRONT"

  default_action {
    allow {}
  }

  rule {
    name     = "aws-common-rule-set"
    priority = 10

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name_prefix}-common"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "aws-known-bad-inputs"
    priority = 20

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name_prefix}-bad-inputs"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "api-rate-limit"
    priority = 30

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit                 = var.waf_api_rate_limit
        aggregate_key_type    = "IP"
        evaluation_window_sec = 300

        scope_down_statement {
          byte_match_statement {
            search_string         = "/api/"
            positional_constraint = "STARTS_WITH"
            field_to_match {
              uri_path {}
            }
            text_transformation {
              priority = 0
              type     = "LOWERCASE"
            }
          }
        }
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name_prefix}-api-rate"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.name_prefix}-console"
    sampled_requests_enabled   = true
  }

  tags = var.tags
}

resource "aws_wafv2_web_acl_logging_configuration" "console" {
  provider = aws.us_east_1

  log_destination_configs = [aws_cloudwatch_log_group.waf.arn]
  resource_arn            = aws_wafv2_web_acl.console.arn

  # Never persist bearer material: the WebSocket credential travels in
  # Sec-WebSocket-Protocol and the API credential in Authorization.
  redacted_fields {
    single_header {
      name = "authorization"
    }
  }

  redacted_fields {
    single_header {
      name = "sec-websocket-protocol"
    }
  }

  # N-02 closure: WAF logs viewer requests only, so the CloudFront-injected
  # value never reaches them. The header is redacted anyway so that a future
  # forwarding change cannot turn WAF logging into a value-disclosure path.
  # Names are lowercase, as the logging configuration requires.
  redacted_fields {
    single_header {
      name = "x-origin-verify"
    }
  }

  depends_on = [aws_cloudwatch_log_resource_policy.waf]
}
