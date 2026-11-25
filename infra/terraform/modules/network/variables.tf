variable "name_prefix" {
  description = "Prefix for every resource name."
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR. Technical default for a dedicated simulator VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "availability_zones" {
  description = "Two availability zones in the selected region. Deployment input (no zone is guessed)."
  type        = list(string)

  validation {
    condition     = length(var.availability_zones) == 2
    error_message = "Exactly two availability zones are required for the single-task Keycloak and Aurora layout."
  }
}

variable "public_subnet_cidrs" {
  description = "Two public subnet CIDRs (ALB)."
  type        = list(string)
  default     = ["10.20.0.0/24", "10.20.1.0/24"]
}

variable "private_subnet_cidrs" {
  description = "Two private subnet CIDRs (tasks, Aurora, endpoints)."
  type        = list(string)
  default     = ["10.20.10.0/24", "10.20.11.0/24"]
}

variable "create_nat_gateways" {
  description = "Create NAT gateways for private-subnet egress. Only needed if the Keycloak image is pulled from a public registry instead of a mirrored private ECR repository. Default false: no invented recurring cost."
  type        = bool
  default     = false
}

variable "flow_log_retention_days" {
  description = "Retention for VPC flow logs."
  type        = number
  default     = 7
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
  default     = {}
}
