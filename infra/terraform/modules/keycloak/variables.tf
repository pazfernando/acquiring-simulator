variable "name_prefix" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "aws_account_id" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "alb_security_group_id" {
  type = string
}

variable "app_security_group_id" {
  type = string
}

variable "db_security_group_id" {
  type = string
}

variable "auth_fqdn" {
  description = "Issuer FQDN (auth.<domain>). Deployment input."
  type        = string
}

variable "certificate_arn" {
  description = "Regional ACM certificate ARN for auth.<domain>."
  type        = string
}

variable "realm_name" {
  description = "Realm name for this environment (acq-<env>)."
  type        = string
}

variable "keycloak_image_digest" {
  description = "Immutable Keycloak image reference including @sha256:<64 hex>. Deployment input; a tag alone is rejected."
  type        = string

  validation {
    condition     = can(regex("@sha256:[0-9a-f]{64}$", var.keycloak_image_digest))
    error_message = "keycloak_image_digest must be pinned by digest (@sha256:<64 hex>)."
  }
}

variable "keycloak_cpu" {
  description = "Fargate task CPU units (technical default for a single simulator task)."
  type        = number
  default     = 1024
}

variable "keycloak_memory" {
  description = "Fargate task memory MiB (technical default)."
  type        = number
  default     = 2048
}

variable "task_cpu_architecture" {
  description = "Fargate CPU architecture for the Keycloak image."
  type        = string
  default     = "X86_64"

  validation {
    condition     = contains(["X86_64", "ARM64"], var.task_cpu_architecture)
    error_message = "task_cpu_architecture must be X86_64 or ARM64."
  }
}

variable "aurora_engine_version" {
  description = "Aurora PostgreSQL engine version. Major pin by default; a minor pin is a deployment input for reproducibility."
  type        = string
  default     = "17"
}

variable "db_name" {
  description = "Keycloak database name."
  type        = string
  default     = "keycloak"
}

variable "db_min_acu" {
  description = "Aurora Serverless v2 minimum ACU (technical proposal; confirm in the cost statement)."
  type        = number
  default     = 0.5
}

variable "db_max_acu" {
  description = "Aurora Serverless v2 maximum ACU (technical proposal; confirm in the cost statement)."
  type        = number
  default     = 2
}

variable "db_backup_retention_days" {
  description = "Automated backup retention. This is NOT a recovery promise (K-2: rebuild from configuration); it is the platform minimum."
  type        = number
  default     = 1
}

variable "bootstrap_admin_secret_arn" {
  description = "Optional Secrets Manager ARN holding bootstrap admin credentials as JSON keys username/password. Used once and discarded with the task generation (K-2)."
  type        = string
  default     = null
}

variable "enable_container_insights" {
  description = "Enable ECS Container Insights (additional cost)."
  type        = bool
  default     = false
}

variable "waf_rate_limit" {
  description = "WAF rate limit per IP per 5 minutes on the login/token paths (proposal; abuse bound, not a business quota)."
  type        = number
  default     = 20
}

variable "create_waf" {
  description = "Create the regional WAF web ACL with login/token rate rules."
  type        = bool
  default     = true
}

variable "log_retention_days" {
  type    = number
  default = 7
}

variable "tags" {
  type    = map(string)
  default = {}
}
