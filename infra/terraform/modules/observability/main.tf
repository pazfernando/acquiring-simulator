locals {
  actions = var.alarm_topic_arn == null ? [] : [var.alarm_topic_arn]
}

resource "aws_cloudwatch_metric_alarm" "api_5xx" {
  for_each = var.api_ids

  alarm_name          = "${var.name_prefix}-${each.key}-5xx"
  alarm_description   = "5XX responses on ${each.key}"
  namespace           = "AWS/ApiGateway"
  metric_name         = "5XXError"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 5
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    ApiId = each.value
  }

  alarm_actions = local.actions
  ok_actions    = local.actions

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "api_429" {
  for_each = var.api_ids

  alarm_name          = "${var.name_prefix}-${each.key}-429"
  alarm_description   = "Throttled responses on ${each.key} (abuse or capacity signal)"
  namespace           = "AWS/ApiGateway"
  metric_name         = "4XXError"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 50
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    ApiId = each.value
  }

  alarm_actions = local.actions

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  for_each = var.lambda_function_names

  alarm_name          = "${var.name_prefix}-${each.key}-errors"
  alarm_description   = "Errors in ${each.key}"
  namespace           = "AWS/Lambda"
  metric_name         = "Errors"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 5
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = each.value
  }

  alarm_actions = local.actions

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "dynamodb_throttles" {
  for_each = var.dynamodb_table_names

  alarm_name          = "${var.name_prefix}-${each.key}-throttles"
  alarm_description   = "Throttled requests on table ${each.key}"
  namespace           = "AWS/DynamoDB"
  metric_name         = "ThrottledRequests"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    TableName = each.value
  }

  alarm_actions = local.actions

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  count = var.alb_arn_suffix == null ? 0 : 1

  alarm_name          = "${var.name_prefix}-alb-target-5xx"
  alarm_description   = "Target 5XX on the issuer ALB"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_Target_5XX_Count"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 5
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = var.alb_arn_suffix
  }

  alarm_actions = local.actions

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "alb_unhealthy" {
  count = var.alb_arn_suffix == null ? 0 : 1

  alarm_name          = "${var.name_prefix}-alb-unhealthy-hosts"
  alarm_description   = "Unhealthy hosts behind the issuer ALB"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = var.alb_arn_suffix
  }

  alarm_actions = local.actions

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "ecs_running_tasks" {
  count = var.ecs_cluster_name == null || var.ecs_service_name == null ? 0 : 1

  alarm_name          = "${var.name_prefix}-keycloak-running-tasks"
  alarm_description   = "Issuer task count below one: new logins and token issuance fail"
  namespace           = "AWS/ECS"
  metric_name         = "RunningTaskCount"
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 3
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    ClusterName = var.ecs_cluster_name
    ServiceName = var.ecs_service_name
  }

  alarm_actions = local.actions

  tags = var.tags
}
