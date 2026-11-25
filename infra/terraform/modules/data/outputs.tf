output "table_names" {
  description = "Map of logical table key to physical table name."
  value       = { for k, t in aws_dynamodb_table.this : k => t.name }
}

output "table_arns" {
  description = "Map of logical table key to table ARN."
  value       = { for k, t in aws_dynamodb_table.this : k => t.arn }
}

output "journal_table" {
  value = aws_dynamodb_table.this["journal"].name
}

output "event_table" {
  value = aws_dynamodb_table.this["events"].name
}

output "registry_table" {
  value = aws_dynamodb_table.this["registry"].name
}

output "connections_table" {
  value = aws_dynamodb_table.this["connections"].name
}
