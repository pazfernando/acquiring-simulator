output "distribution_id" {
  value = aws_cloudfront_distribution.console.id
}

output "distribution_domain_name" {
  value = aws_cloudfront_distribution.console.domain_name
}

output "console_bucket_name" {
  value = aws_s3_bucket.console.id
}

output "config_object_key" {
  value = aws_s3_object.config.key
}

output "web_acl_arn" {
  value = aws_wafv2_web_acl.console.arn
}
