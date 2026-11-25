variable "name_prefix" {
  description = "Prefix for resource names."
  type        = string
}

variable "aws_region" {
  type = string
}

variable "aws_account_id" {
  type = string
}

variable "ws_fqdn" {
  description = "Public WebSocket FQDN. Deployment input."
  type        = string
}

variable "certificate_arn" {
  description = "Regional ACM certificate ARN (us-east-2) for ws.<domain>."
  type        = string
}

variable "ws_api_audience" {
  description = "Audience the socket token must carry."
  type        = string
}

variable "demo_subject" {
  description = "The single demo subject accepted at $connect."
  type        = string
}

variable "allowed_origin" {
  description = "Exact Origin header value accepted at $connect (https://console.<domain>)."
  type        = string
}

variable "subprotocol_name" {
  description = "Fixed subprotocol identifier that must accompany the bearer token."
  type        = string
  default     = "bearer"
}

variable "authorizer_artifact" {
  description = "WebSocket $connect authorizer artifact identity in S3."
  type = object({
    bucket     = string
    key        = string
    version    = string
    sha256_b64 = string
  })
}

variable "authorizer_handler" {
  type    = string
  default = "com.acquiring.simulator.adapter.aws.WsConnectAuthorizerHandler::handleRequest"
}

variable "notifier_artifact" {
  description = "Notifier Lambda artifact identity in S3 (server push via @connections)."
  type = object({
    bucket     = string
    key        = string
    version    = string
    sha256_b64 = string
  })
}

variable "notifier_handler" {
  type    = string
  default = "com.acquiring.simulator.adapter.aws.WsNotifierHandler::handleRequest"
}

variable "connections_table_name" {
  type = string
}

variable "connections_table_arn" {
  type = string
}

variable "guardrail_policy_arn" {
  type = string
}

variable "stage_name" {
  description = "WebSocket stage name."
  type        = string
  default     = "ws"
}

variable "throttle_burst_limit" {
  description = "WebSocket stage burst limit (proposal; connection attempts are bounded in the authorizer too)."
  type        = number
  default     = 20
}

variable "throttle_rate_limit" {
  description = "WebSocket stage steady-state rate limit per second (proposal)."
  type        = number
  default     = 10
}

variable "log_retention_days" {
  type    = number
  default = 7
}

variable "tags" {
  type    = map(string)
  default = {}
}
