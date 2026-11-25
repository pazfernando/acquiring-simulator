# Partial S3 backend. Bucket, key and region are supplied per environment with
# -backend-config (or TF_CLI_ARGS_init), never committed here.
#
# The bucket and CMK are created by infra/terraform/bootstrap before the first
# init of this root. use_lockfile requires Terraform >= 1.10; this root pins
# 1.17.2.
terraform {
  backend "s3" {
    encrypt      = true
    use_lockfile = true
  }
}
