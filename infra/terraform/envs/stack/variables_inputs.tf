variable "console_audience" {
  description = "Console API audience (resource-server identifier). Deployment input."
  type        = string
}

variable "acl_audience" {
  description = "ACL API audience."
  type        = string
}

variable "simulator_audience" {
  description = "Simulator/processor ISO API audience."
  type        = string
}

variable "ws_audience" {
  description = "WebSocket API audience."
  type        = string
}

variable "demo_subject" {
  description = "The single demo subject shared by the HTTP and WebSocket authorizers."
  type        = string
}

variable "console_api_artifact" {
  description = "Console API Lambda artifact identity (bucket, key, S3 version, base64 SHA-256). Supplied by CI; nothing is fabricated."
  type = object({
    bucket     = string
    key        = string
    version    = string
    sha256_b64 = string
  })

  validation {
    condition     = can(regex("^[A-Za-z0-9+/]{43}=$", var.console_api_artifact.sha256_b64))
    error_message = "console_api_artifact.sha256_b64 must be the base64-encoded SHA-256 of the artifact zip."
  }
}

variable "mtls_authorizer_artifact" {
  description = "Shared mTLS/OAuth authorizer artifact identity. Two deployments (ACL and processor boundaries) use it."
  type = object({
    bucket     = string
    key        = string
    version    = string
    sha256_b64 = string
  })

  validation {
    condition     = can(regex("^[A-Za-z0-9+/]{43}=$", var.mtls_authorizer_artifact.sha256_b64))
    error_message = "mtls_authorizer_artifact.sha256_b64 must be base64 SHA-256."
  }
}

variable "ws_authorizer_artifact" {
  description = "WebSocket $connect authorizer artifact identity."
  type = object({
    bucket     = string
    key        = string
    version    = string
    sha256_b64 = string
  })

  validation {
    condition     = can(regex("^[A-Za-z0-9+/]{43}=$", var.ws_authorizer_artifact.sha256_b64))
    error_message = "ws_authorizer_artifact.sha256_b64 must be base64 SHA-256."
  }
}

variable "ws_notifier_artifact" {
  description = "WebSocket notifier artifact identity."
  type = object({
    bucket     = string
    key        = string
    version    = string
    sha256_b64 = string
  })

  validation {
    condition     = can(regex("^[A-Za-z0-9+/]{43}=$", var.ws_notifier_artifact.sha256_b64))
    error_message = "ws_notifier_artifact.sha256_b64 must be base64 SHA-256."
  }
}

variable "acl_backend_lambda_invoke_arn" {
  description = "Invoke ARN of the ACL backend Lambda."
  type        = string
}

variable "acl_backend_lambda_name" {
  description = "Name of the ACL backend Lambda."
  type        = string
}

variable "processor_backend_lambda_invoke_arn" {
  description = "Invoke ARN of the processor backend Lambda (ISO C2 boundary)."
  type        = string
}

variable "processor_backend_lambda_name" {
  description = "Name of the processor backend Lambda."
  type        = string
}

variable "acl_truststore_uri" {
  description = "S3 URI of the console-client truststore bundle for the ACL boundary."
  type        = string
}

variable "acl_truststore_version" {
  description = "Exact S3 object version of the ACL truststore bundle."
  type        = string
}

variable "processor_truststore_uri" {
  description = "S3 URI of the ACL-client truststore bundle for the processor boundary."
  type        = string
}

variable "processor_truststore_version" {
  description = "Exact S3 object version of the processor truststore bundle."
  type        = string
}

variable "keycloak_image_digest" {
  description = "Keycloak image pinned by digest (@sha256:...). Deployment input."
  type        = string
}

variable "bootstrap_admin_secret_arn" {
  description = "Optional Secrets Manager ARN with one-time bootstrap admin credentials (JSON username/password)."
  type        = string
  default     = null
}

variable "keycloak_cpu" {
  description = "Keycloak Fargate CPU units (technical proposal)."
  type        = number
  default     = 1024
}

variable "keycloak_memory" {
  description = "Keycloak Fargate memory MiB (technical proposal)."
  type        = number
  default     = 2048
}

variable "keycloak_task_cpu_architecture" {
  description = "Keycloak image architecture."
  type        = string
  default     = "X86_64"
}

variable "aurora_engine_version" {
  description = "Aurora PostgreSQL engine version (major pin by default)."
  type        = string
  default     = "17"
}

variable "db_min_acu" {
  description = "Aurora Serverless v2 minimum ACU (technical proposal)."
  type        = number
  default     = 0.5
}

variable "db_max_acu" {
  description = "Aurora Serverless v2 maximum ACU (technical proposal)."
  type        = number
  default     = 2
}

variable "create_nat_gateways" {
  description = "NAT gateways for private egress. Only needed when the Keycloak image is pulled from a public registry instead of mirrored ECR."
  type        = bool
  default     = false
}

variable "enable_point_in_time_recovery" {
  description = "DynamoDB PITR on journal/event/registry. Technical choice; not an owner durability promise."
  type        = bool
  default     = false
}

variable "enable_container_insights" {
  description = "ECS Container Insights (additional cost)."
  type        = bool
  default     = false
}

variable "create_keycloak_waf" {
  description = "Create the regional login/token rate-limit WAF."
  type        = bool
  default     = true
}

variable "waf_api_rate_limit" {
  description = "Console /api/* rate limit per IP per 5 minutes."
  type        = number
  default     = 100
}

variable "waf_login_rate_limit" {
  description = "Issuer /realms/* rate limit per IP per 5 minutes."
  type        = number
  default     = 20
}

variable "journal_ttl_attribute" {
  description = "Optional DynamoDB TTL attribute for the journal (NV-05 mapping unresolved; null disables TTL)."
  type        = string
  default     = null
}

variable "event_ttl_attribute" {
  description = "Optional DynamoDB TTL attribute for the event table (NV-05; null disables TTL)."
  type        = string
  default     = null
}

variable "connections_ttl_attribute" {
  description = "Optional DynamoDB TTL attribute for connection records (null disables TTL)."
  type        = string
  default     = null
}

variable "log_retention_days" {
  description = "Log retention (owner direction: one week)."
  type        = number
  default     = 7

  validation {
    condition     = var.log_retention_days == 7
    error_message = "The approved functional/log retention window is seven days."
  }
}

variable "alarm_topic_arn" {
  description = "Optional SNS topic for alarm actions. No contact is invented."
  type        = string
  default     = null
}
