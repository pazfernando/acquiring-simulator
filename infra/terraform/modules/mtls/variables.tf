variable "name_prefix" {
  description = "Prefix for resource names (for example acl or processor)."
  type        = string
}

variable "api_fqdn" {
  description = "mTLS custom-domain FQDN for this boundary. Deployment input."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$", var.api_fqdn))
    error_message = "api_fqdn must be a lowercase FQDN."
  }
}

variable "certificate_arn" {
  description = "Regional ACM certificate ARN (us-east-2) for the mTLS custom domain."
  type        = string
}

variable "truststore_uri" {
  description = "S3 URI of the truststore bundle (public certificates only). Deployment input."
  type        = string

  validation {
    condition     = can(regex("^s3://[a-z0-9][a-z0-9.-]+/.+$", var.truststore_uri))
    error_message = "truststore_uri must be an s3://bucket/key URI."
  }
}

variable "truststore_version" {
  description = "Exact S3 object version of the truststore bundle. 'latest' is rejected: the pin is the control."
  type        = string

  validation {
    condition     = length(var.truststore_version) > 0 && lower(var.truststore_version) != "latest"
    error_message = "truststore_version must be an exact S3 object version id, never 'latest'."
  }
}

variable "backend_lambda_invoke_arn" {
  description = "Invoke ARN of the backend Lambda that implements this service boundary."
  type        = string
}

variable "backend_lambda_name" {
  description = "Name of the backend Lambda, for the invoke permission."
  type        = string
}

variable "authorizer_artifact" {
  description = "Lambda authorizer artifact identity in S3 (bucket, key, version, base64 SHA-256)."
  type = object({
    bucket     = string
    key        = string
    version    = string
    sha256_b64 = string
  })
}

variable "authorizer_handler" {
  description = "Handler for the mTLS/OAuth authorizer Lambda."
  type        = string
  default     = "com.acquiring.simulator.adapter.aws.MtlsAuthorizerHandler::handleRequest"
}

variable "expected_audience" {
  description = "Audience this boundary requires on the OAuth access token."
  type        = string
}

variable "required_scope" {
  description = "Scope this boundary requires on the OAuth access token."
  type        = string
}

variable "stage_name" {
  description = "API stage name."
  type        = string
  default     = "v1"
}

variable "throttle_burst_limit" {
  description = "REST stage burst limit (proposal)."
  type        = number
  default     = 20
}

variable "throttle_rate_limit" {
  description = "REST stage steady-state rate limit per second (proposal)."
  type        = number
  default     = 10
}

variable "guardrail_policy_arn" {
  description = "Explicit-deny guardrail policy from the identity module."
  type        = string
}

variable "log_retention_days" {
  description = "Retention for authorizer and access logs."
  type        = number
  default     = 7
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
  default     = {}
}
