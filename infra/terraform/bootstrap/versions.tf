terraform {
  required_version = "= 1.17.2"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "= 6.74.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  # Fail closed: a run against any other account is refused before any API call
  # is made. No account identifier is defaulted or invented.
  allowed_account_ids = [var.aws_account_id]

  default_tags {
    tags = var.tags
  }
}
