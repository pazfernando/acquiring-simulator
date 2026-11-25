variable "aws_account_id" {
  description = "12-digit AWS account that owns the Terraform state bucket. Deployment input; never defaulted."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be a 12-digit AWS account number."
  }
}

variable "aws_region" {
  description = "Region for the state bucket and its CMK. Owner-recorded default is us-east-2."
  type        = string
  default     = "us-east-2"
}

variable "state_bucket_name" {
  description = "Globally unique S3 bucket name for Terraform state. Deployment input; never defaulted."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", var.state_bucket_name))
    error_message = "state_bucket_name must be a valid S3 bucket name."
  }
}

variable "kms_alias_name" {
  description = "Alias for the state CMK. Must start with alias/."
  type        = string
  default     = "alias/acq-sim-terraform-state"

  validation {
    condition     = can(regex("^alias/", var.kms_alias_name))
    error_message = "kms_alias_name must start with alias/."
  }
}

variable "trusted_principal_arns" {
  description = "IAM principals (for example the GitHub OIDC deploy roles) allowed to use the state bucket and CMK. Deployment input; at least one is required."
  type        = list(string)

  validation {
    condition     = length(var.trusted_principal_arns) > 0 && alltrue([for a in var.trusted_principal_arns : can(regex("^arn:aws:iam::[0-9]{12}:(role|user)/.+$", a))])
    error_message = "trusted_principal_arns must contain at least one IAM role or user ARN."
  }
}

variable "noncurrent_version_expiration_days" {
  description = "Retention for noncurrent state object versions. Current state is never expired."
  type        = number
  default     = 30
}

variable "enable_access_logging" {
  description = "Create a separate access-log bucket for the state bucket. If false, no logging bucket is created and the gap is reported."
  type        = bool
  default     = false
}

variable "access_log_bucket_name" {
  description = "Bucket name for S3 access logs when enable_access_logging is true. Must differ from the state bucket."
  type        = string
  default     = null
}

variable "tags" {
  description = "Tags applied to every bootstrap resource."
  type        = map(string)
  default = {
    "app"        = "acquiring-simulator"
    "managed-by" = "terraform"
    "component"  = "terraform-state-bootstrap"
  }
}
