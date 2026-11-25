output "alarm_names" {
  value = concat(
    [for a in aws_cloudwatch_metric_alarm.api_5xx : a.alarm_name],
    [for a in aws_cloudwatch_metric_alarm.api_429 : a.alarm_name],
    [for a in aws_cloudwatch_metric_alarm.lambda_errors : a.alarm_name],
    [for a in aws_cloudwatch_metric_alarm.dynamodb_throttles : a.alarm_name],
    [for a in aws_cloudwatch_metric_alarm.alb_5xx : a.alarm_name],
    [for a in aws_cloudwatch_metric_alarm.alb_unhealthy : a.alarm_name],
    [for a in aws_cloudwatch_metric_alarm.ecs_running_tasks : a.alarm_name],
  )
}
