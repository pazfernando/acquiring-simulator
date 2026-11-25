variable "name_prefix" {
  description = "Prefix for resource names."
  type        = string
}

variable "environment" {
  description = "Deployment environment name (also an application environment variable)."
  type        = string
}

variable "api_audience" {
  description = "Audience value the console token must carry."
  type        = string
}

variable "issuer_url" {
  description = "Realm issuer URL for the JWT authorizer."
  type        = string
}

variable "api_fqdn" {
  description = "Dedicated regional custom-domain FQDN for the console HTTP API. Deployment input; the CloudFront /api/* origin is this domain, not execute-api."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$", var.api_fqdn))
    error_message = "api_fqdn must be a lowercase FQDN."
  }

  validation {
    condition     = !strcontains(var.api_fqdn, "execute-api")
    error_message = "api_fqdn must not be an execute-api hostname; it is the dedicated origin domain."
  }
}

variable "certificate_arn" {
  description = "ACM certificate ARN in the estate region covering api_fqdn (regional custom-domain requirement). Deployment input."
  type        = string
}

variable "edge_origin_verify" {
  description = "Expected value of the CloudFront-injected X-Origin-Verify header (N-02 closure, owner Option B). Supplied from the stack root's random_password so the boundary check and the CloudFront origin compare the same value; no default, never a tfvars or CI value. Read by the console Lambda as EDGE_ORIGIN_VERIFY and compared byte-exactly, fail-closed."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.edge_origin_verify) >= 32 && can(regex("^[A-Za-z0-9._~-]+$", var.edge_origin_verify))
    error_message = "edge_origin_verify must be at least 32 characters of RFC 7230 token-safe ASCII."
  }
}

variable "demo_subject" {
  description = "The single demo subject accepted by the API layer."
  type        = string
}

variable "artifact" {
  description = "Console Lambda artifact identity in S3 (bucket, key, version, base64 SHA-256). Built and recorded by CI; never fabricated here."
  type = object({
    bucket     = string
    key        = string
    version    = string
    sha256_b64 = string
  })
}

variable "handler" {
  description = "Lambda handler class/method for the console API."
  type        = string
  default     = "com.acquiring.simulator.adapter.aws.ConsoleHandler::handleRequest"
}

variable "runtime" {
  description = "Java runtime for the console Lambda."
  type        = string
  default     = "java25"
}

variable "memory_mb" {
  description = "Lambda memory (technical default)."
  type        = number
  default     = 1024
}

variable "timeout_seconds" {
  description = "Lambda timeout (technical default; C2/HTTP bounds are enforced in code)."
  type        = number
  default     = 15
}

variable "journal_table_name" {
  type = string
}

variable "journal_table_arn" {
  type = string
}

variable "event_table_name" {
  type = string
}

variable "event_table_arn" {
  type = string
}

variable "registry_table_name" {
  type = string
}

variable "registry_table_arn" {
  type = string
}

variable "guardrail_policy_arn" {
  description = "Explicit-deny guardrail policy from the identity module."
  type        = string
}

variable "throttle_burst_limit" {
  description = "API Gateway stage burst limit (proposal; independent technical bound)."
  type        = number
  default     = 10
}

variable "throttle_rate_limit" {
  description = "API Gateway stage steady-state rate limit per second (proposal)."
  type        = number
  default     = 5
}

variable "log_retention_days" {
  description = "Retention for Lambda and API access logs."
  type        = number
  default     = 7
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
  default     = {}
}
