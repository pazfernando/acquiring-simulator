locals {
  realm_url = "https://${var.auth_fqdn}/realms/${var.realm_name}"
  db_host   = aws_rds_cluster.keycloak.endpoint
}

############################################################
# Aurora PostgreSQL Serverless v2 (single writer, disposable)
############################################################

resource "aws_db_subnet_group" "keycloak" {
  name       = "${var.name_prefix}-keycloak"
  subnet_ids = var.private_subnet_ids

  tags = merge(var.tags, { "Name" = "${var.name_prefix}-keycloak" })
}

resource "aws_rds_cluster" "keycloak" {
  cluster_identifier = "${var.name_prefix}-keycloak"
  engine             = "aurora-postgresql"
  engine_version     = var.aurora_engine_version
  database_name      = var.db_name
  master_username    = "keycloak"

  # The password is generated and held by Secrets Manager; no secret value is
  # authored, planned or stored in Terraform state.
  manage_master_user_password   = true
  master_user_secret_kms_key_id = null

  db_subnet_group_name   = aws_db_subnet_group.keycloak.name
  vpc_security_group_ids = [var.db_security_group_id]

  storage_encrypted = true

  serverlessv2_scaling_configuration {
    min_capacity = var.db_min_acu
    max_capacity = var.db_max_acu
  }

  backup_retention_period = var.db_backup_retention_days
  copy_tags_to_snapshot   = true
  deletion_protection     = false
  skip_final_snapshot     = true

  tags = merge(var.tags, { "Name" = "${var.name_prefix}-keycloak" })
}

resource "aws_rds_cluster_instance" "keycloak" {
  identifier         = "${var.name_prefix}-keycloak-1"
  cluster_identifier = aws_rds_cluster.keycloak.id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.keycloak.engine
  engine_version     = aws_rds_cluster.keycloak.engine_version

  tags = merge(var.tags, { "Name" = "${var.name_prefix}-keycloak-1" })
}

############################################################
# ECS cluster, roles and task definition
############################################################

resource "aws_ecs_cluster" "keycloak" {
  name = "${var.name_prefix}-keycloak"

  setting {
    name  = "containerInsights"
    value = var.enable_container_insights ? "enabled" : "disabled"
  }

  tags = var.tags
}

data "aws_iam_policy_document" "ecs_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_cloudwatch_log_group" "keycloak" {
  name              = "/ecs/${var.name_prefix}-keycloak"
  retention_in_days = var.log_retention_days

  tags = var.tags
}

resource "aws_iam_role" "execution" {
  name               = "${var.name_prefix}-keycloak-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution_secrets" {
  statement {
    sid       = "ReadKeycloakDatabaseSecret"
    effect    = "Allow"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_rds_cluster.keycloak.master_user_secret[0].secret_arn]
  }

  dynamic "statement" {
    for_each = var.bootstrap_admin_secret_arn == null ? [] : [var.bootstrap_admin_secret_arn]

    content {
      sid       = "ReadBootstrapAdminSecret"
      effect    = "Allow"
      actions   = ["secretsmanager:GetSecretValue"]
      resources = [statement.value]
    }
  }

  statement {
    sid       = "DecryptSecretsViaSecretsManager"
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["secretsmanager.${var.aws_region}.amazonaws.com"]
    }
  }

  statement {
    sid       = "WriteOwnLogs"
    effect    = "Allow"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.keycloak.arn}:*"]
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "keycloak-secrets-and-logs"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

resource "aws_iam_role" "task" {
  name               = "${var.name_prefix}-keycloak-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json

  tags = var.tags
}

# ECS Exec operator path: no inbound SSH, no public admin endpoint. Access is
# an audited SSM session performed by an authorized operator.
data "aws_iam_policy_document" "task_exec" {
  statement {
    sid    = "EcsExec"
    effect = "Allow"
    actions = [
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "task_exec" {
  name   = "keycloak-ecs-exec"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task_exec.json
}

resource "aws_ecs_task_definition" "keycloak" {
  family                   = "${var.name_prefix}-keycloak"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.keycloak_cpu
  memory                   = var.keycloak_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.task_cpu_architecture
  }

  container_definitions = jsonencode([
    {
      name      = "keycloak"
      image     = var.keycloak_image_digest
      essential = true

      portMappings = [
        { containerPort = 8080, protocol = "tcp" },
        { containerPort = 9000, protocol = "tcp" },
      ]

      environment = [
        { name = "KC_DB", value = "postgres" },
        { name = "KC_DB_URL", value = "jdbc:postgresql://${local.db_host}:5432/${var.db_name}" },
        { name = "KC_HOSTNAME", value = "https://${var.auth_fqdn}" },
        { name = "KC_HOSTNAME_STRICT", value = "true" },
        { name = "KC_PROXY_HEADERS", value = "xforwarded" },
        { name = "KC_HTTP_ENABLED", value = "true" },
        { name = "KC_HEALTH_ENABLED", value = "true" },
        { name = "KC_METRICS_ENABLED", value = "false" },
        { name = "KC_LOG_LEVEL", value = "info" },
      ]

      secrets = concat(
        [
          { name = "KC_DB_USERNAME", valueFrom = "${aws_rds_cluster.keycloak.master_user_secret[0].secret_arn}:username::" },
          { name = "KC_DB_PASSWORD", valueFrom = "${aws_rds_cluster.keycloak.master_user_secret[0].secret_arn}:password::" },
        ],
        var.bootstrap_admin_secret_arn == null ? [] : [
          { name = "KC_BOOTSTRAP_ADMIN_USERNAME", valueFrom = "${var.bootstrap_admin_secret_arn}:username::" },
          { name = "KC_BOOTSTRAP_ADMIN_PASSWORD", valueFrom = "${var.bootstrap_admin_secret_arn}:password::" },
        ]
      )

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.keycloak.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "keycloak"
        }
      }
    }
  ])

  tags = var.tags
}

############################################################
# ALB with TLS and a deny-by-default path allowlist
############################################################

resource "aws_lb" "keycloak" {
  name                       = substr("${var.name_prefix}-kc", 0, 32)
  internal                   = false
  load_balancer_type         = "application"
  security_groups            = [var.alb_security_group_id]
  subnets                    = var.public_subnet_ids
  drop_invalid_header_fields = true
  enable_deletion_protection = false

  tags = merge(var.tags, { "Name" = "${var.name_prefix}-keycloak" })
}

resource "aws_lb_target_group" "keycloak" {
  name        = substr("${var.name_prefix}-kc", 0, 32)
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    enabled             = true
    path                = "/"
    matcher             = "200-399"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  deregistration_delay = 30

  tags = var.tags
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.keycloak.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "Not found"
      status_code  = "403"
    }
  }

  tags = var.tags
}

locals {
  allowed_paths = [
    "/realms/*/protocol/openid-connect/*",
    "/realms/*/login-actions/*",
    "/realms/*/.well-known/*",
    "/resources/*",
    "/.well-known/*",
  ]
}

resource "aws_lb_listener_rule" "allowlisted_paths" {
  count = length(local.allowed_paths)

  listener_arn = aws_lb_listener.https.arn
  priority     = 10 + count.index

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.keycloak.arn
  }

  condition {
    host_header {
      values = [var.auth_fqdn]
    }
  }

  condition {
    path_pattern {
      values = [local.allowed_paths[count.index]]
    }
  }

  tags = var.tags
}

resource "aws_ecs_service" "keycloak" {
  name            = "${var.name_prefix}-keycloak"
  cluster         = aws_ecs_cluster.keycloak.id
  task_definition = aws_ecs_task_definition.keycloak.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  platform_version                  = "LATEST"
  enable_execute_command            = true
  health_check_grace_period_seconds = 60

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.app_security_group_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.keycloak.arn
    container_name   = "keycloak"
    container_port   = 8080
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100

  tags = var.tags
}

############################################################
# Regional WAF: login/token rate bound and bad-input filtering
############################################################

resource "aws_cloudwatch_log_group" "waf" {
  count = var.create_waf ? 1 : 0

  name              = "aws-waf-logs-${var.name_prefix}-auth"
  retention_in_days = var.log_retention_days

  tags = var.tags
}

data "aws_iam_policy_document" "waf_logs" {
  count = var.create_waf ? 1 : 0

  statement {
    sid    = "AWSWAFLogDelivery"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["delivery.logs.amazonaws.com"]
    }

    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.waf[0].arn}:*"]

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [var.aws_account_id]
    }
  }
}

resource "aws_cloudwatch_log_resource_policy" "waf" {
  count = var.create_waf ? 1 : 0

  policy_name     = "${var.name_prefix}-auth-waf-logs"
  policy_document = data.aws_iam_policy_document.waf_logs[0].json
}

resource "aws_wafv2_web_acl" "keycloak" {
  count = var.create_waf ? 1 : 0

  name        = "${var.name_prefix}-auth"
  description = "Login/token abuse bounds for the disposable issuer"
  scope       = "REGIONAL"

  # N-05: rules here must stay header/URI based. Inspecting the body of a
  # Keycloak login POST would persist credentials in WAF logs; the assertion is
  # encoded in tools/policy_checks.py.

  default_action {
    allow {}
  }

  rule {
    name     = "known-bad-inputs"
    priority = 10

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
      metric_name                = "${var.name_prefix}-auth-bad-inputs"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "realm-path-rate-limit"
    priority = 20

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit                 = var.waf_rate_limit
        aggregate_key_type    = "IP"
        evaluation_window_sec = 300

        scope_down_statement {
          byte_match_statement {
            search_string         = "/realms/"
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
      metric_name                = "${var.name_prefix}-auth-realm-rate"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.name_prefix}-auth"
    sampled_requests_enabled   = true
  }

  tags = var.tags
}

resource "aws_wafv2_web_acl_association" "keycloak" {
  count = var.create_waf ? 1 : 0

  resource_arn = aws_lb.keycloak.arn
  web_acl_arn  = aws_wafv2_web_acl.keycloak[0].arn
}

resource "aws_wafv2_web_acl_logging_configuration" "keycloak" {
  count = var.create_waf ? 1 : 0

  log_destination_configs = [aws_cloudwatch_log_group.waf[0].arn]
  resource_arn            = aws_wafv2_web_acl.keycloak[0].arn

  # N-05 (rev2 Security SEC-I4): Keycloak submits credentials as a form body.
  # No rule in this web ACL inspects the request body, and none may be added
  # without independent review + log redaction (enforced by
  # tools/policy_checks.py). Headers and OAuth/flow query parameters are
  # redacted so a future body-inspecting rule cannot silently leak secrets
  # through sampled or full log records.
  redacted_fields {
    single_header {
      name = "authorization"
    }
  }

  redacted_fields {
    single_header {
      name = "cookie"
    }
  }

  # The provider supports whole-query-string redaction (not individual
  # arguments); this covers the OAuth code/session_code values.
  redacted_fields {
    query_string {}
  }

  depends_on = [aws_cloudwatch_log_resource_policy.waf]
}
