output "state_bucket_name" {
  description = "Bucket to pass as TF_STATE_BUCKET / -backend-config=bucket."
  value       = aws_s3_bucket.state.id
}

output "state_bucket_arn" {
  description = "ARN of the state bucket, for the deploy role policy."
  value       = aws_s3_bucket.state.arn
}

output "state_kms_key_arn" {
  description = "ARN of the state CMK (SSE-KMS for the S3 backend)."
  value       = aws_kms_key.state.arn
}

output "state_kms_alias" {
  description = "Alias of the state CMK."
  value       = aws_kms_alias.state.name
}

output "aws_region" {
  description = "Region of the state bucket."
  value       = var.aws_region
}

output "backend_config_hint" {
  description = "Non-secret backend configuration to pass to terraform init. Key and environment come from the environment input set."
  value = {
    bucket       = aws_s3_bucket.state.id
    region       = var.aws_region
    encrypt      = true
    use_lockfile = true
    kms_key_id   = aws_kms_key.state.arn
  }
}
