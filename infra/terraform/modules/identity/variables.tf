variable "name_prefix" {
  description = "Prefix for role and policy names."
  type        = string
}

variable "aws_account_id" {
  description = "Account that owns the resources."
  type        = string
}

variable "state_bucket_arn" {
  description = "ARN of the Terraform state bucket created by the bootstrap root."
  type        = string
}

variable "state_kms_key_arn" {
  description = "ARN of the state CMK created by the bootstrap root."
  type        = string
}

variable "github_oidc_subject" {
  description = "Exact OIDC subject GitHub emits for this repository and environment (classic or immutable form). No wildcard is accepted."
  type        = string

  validation {
    condition     = length(var.github_oidc_subject) > 0 && !strcontains(var.github_oidc_subject, "*")
    error_message = "github_oidc_subject must be the exact subject string; wildcards are rejected."
  }
}

variable "github_oidc_audience" {
  description = "OIDC audience for STS."
  type        = string
  default     = "sts.amazonaws.com"
}

variable "create_oidc_provider" {
  description = "Create the GitHub OIDC provider. Set false only when it already exists in the account and its ARN is supplied."
  type        = bool
  default     = true
}

variable "oidc_provider_arn" {
  description = "Existing GitHub OIDC provider ARN. Required when create_oidc_provider is false."
  type        = string
  default     = null
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
  default     = {}
}
