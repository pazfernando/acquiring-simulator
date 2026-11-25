variable "name_prefix" {
  type = string
}

variable "api_ids" {
  description = "Map of human label to API Gateway API id (HTTP and WebSocket)."
  type        = map(string)
  default     = {}
}

variable "lambda_function_names" {
  description = "Lambda functions to alarm on errors and throttles."
  type        = set(string)
  default     = []
}

variable "dynamodb_table_names" {
  description = "DynamoDB tables to alarm on throttles."
  type        = set(string)
  default     = []
}

variable "alb_arn_suffix" {
  description = "ALB ARN suffix for target 5xx and unhealthy-host alarms. null skips these alarms."
  type        = string
  default     = null
}

variable "ecs_cluster_name" {
  description = "ECS cluster for the running-task alarm. null skips it."
  type        = string
  default     = null
}

variable "ecs_service_name" {
  description = "ECS service for the running-task alarm."
  type        = string
  default     = null
}

variable "alarm_topic_arn" {
  description = "Optional SNS topic ARN for alarm actions. No contact is invented; without it alarms are visible but silent."
  type        = string
  default     = null
}

variable "tags" {
  type    = map(string)
  default = {}
}
