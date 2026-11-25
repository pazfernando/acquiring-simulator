variable "aws_account_id" {
  description = "12-digit account that owns the deployment. Deployment input; never defaulted."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be a 12-digit AWS account number."
  }
}

variable "aws_region" {
  description = "Estate region. Owner-recorded: us-east-2."
  type        = string
  default     = "us-east-2"

  validation {
    condition     = var.aws_region == "us-east-2"
    error_message = "The approved estate region is us-east-2; CloudFront certificates use the us-east-1 alias."
  }
}

variable "environment" {
  description = "Environment name. Appears in resource names, the realm and the OIDC subject. Deployment input."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9-]{2,16}$", var.environment))
    error_message = "environment must match [a-z0-9-]{2,16}."
  }
}

variable "realm_name" {
  description = "Optional realm override. Empty means acq-<environment>."
  type        = string
  default     = ""
}

variable "availability_zones" {
  description = "Two AZs in aws_region. Deployment input."
  type        = list(string)
}

variable "console_fqdn" {
  description = "Public console FQDN."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$", var.console_fqdn))
    error_message = "console_fqdn must be a lowercase FQDN."
  }
}

variable "auth_fqdn" {
  description = "Keycloak issuer FQDN."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$", var.auth_fqdn))
    error_message = "auth_fqdn must be a lowercase FQDN."
  }
}

variable "ws_fqdn" {
  description = "WebSocket FQDN."
  type        = string
}

variable "api_fqdn" {
  description = "Dedicated regional custom-domain FQDN for the console HTTP API. Deployment input; it is the CloudFront /api/* origin and is separate from every other hostname because the default execute-api endpoint is disabled (N-02)."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$", var.api_fqdn))
    error_message = "api_fqdn must be a lowercase FQDN."
  }
}

variable "acl_fqdn" {
  description = "mTLS FQDN for the console-to-ACL boundary."
  type        = string
}

variable "processor_fqdn" {
  description = "mTLS FQDN for the ACL-to-processor ISO boundary."
  type        = string
}

variable "console_certificate_arn" {
  description = "ACM certificate ARN for console_fqdn in us-east-1."
  type        = string
}

variable "auth_certificate_arn" {
  description = "ACM certificate ARN for auth_fqdn in us-east-2."
  type        = string
}

variable "ws_certificate_arn" {
  description = "ACM certificate ARN for ws_fqdn in us-east-2."
  type        = string
}

variable "api_certificate_arn" {
  description = "ACM certificate ARN for api_fqdn in us-east-2 (regional custom domain)."
  type        = string
}

variable "acl_certificate_arn" {
  description = "ACM certificate ARN for acl_fqdn in us-east-2."
  type        = string
}

variable "processor_certificate_arn" {
  description = "ACM certificate ARN for processor_fqdn in us-east-2."
  type        = string
}

variable "dns_zone_id" {
  description = "Route 53 hosted zone that contains every FQDN above. Deployment input."
  type        = string

  validation {
    condition     = can(regex("^Z[A-Z0-9]{1,32}$", var.dns_zone_id))
    error_message = "dns_zone_id must look like a Route 53 hosted zone id."
  }
}

variable "console_bucket_name" {
  description = "Globally unique private S3 bucket name for the SPA."
  type        = string
}

variable "state_bucket_arn" {
  description = "ARN of the Terraform state bucket from the bootstrap root."
  type        = string
}

variable "state_kms_key_arn" {
  description = "ARN of the state CMK from the bootstrap root."
  type        = string
}

variable "github_oidc_subject" {
  description = "Exact OIDC subject GitHub emits for this repository/environment. No wildcard."
  type        = string

  validation {
    condition     = length(var.github_oidc_subject) > 0 && !strcontains(var.github_oidc_subject, "*")
    error_message = "github_oidc_subject must be the exact subject string; wildcards are rejected."
  }
}

variable "create_oidc_provider" {
  description = "Create the GitHub OIDC provider in this account."
  type        = bool
  default     = true
}

variable "existing_oidc_provider_arn" {
  description = "Existing GitHub OIDC provider ARN when create_oidc_provider is false."
  type        = string
  default     = null
}

variable "tags" {
  description = "Additional tags."
  type        = map(string)
  default     = {}
}
