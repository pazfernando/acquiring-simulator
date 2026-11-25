locals {
  # N-07 (rev2 Security SEC-I6): durable business/evidence tables carry
  # infrastructure-level deletion protection so an operator or automation error
  # cannot destroy the journal, event source, issuer truth or registry. The
  # disposable tables (connections, assertion replay) stay unprotected so the
  # issuer rebuild path never depends on them.
  tables = {
    journal          = { name = "${var.name_prefix}-journal", ttl = var.journal_ttl_attribute, pitr = true, protect = true }
    events           = { name = "${var.name_prefix}-events", ttl = var.event_ttl_attribute, pitr = true, protect = true }
    issuer_truth     = { name = "${var.name_prefix}-issuer-truth", ttl = null, pitr = false, protect = true }
    registry         = { name = "${var.name_prefix}-registry", ttl = null, pitr = true, protect = true }
    connections      = { name = "${var.name_prefix}-connections", ttl = var.connections_ttl_attribute, pitr = false, protect = false }
    assertion_replay = { name = "${var.name_prefix}-assertion-replay", ttl = null, pitr = false, protect = false }
  }
}

resource "aws_dynamodb_table" "this" {
  for_each = local.tables

  name         = each.value.name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "pk"
  range_key    = "sk"

  attribute {
    name = "pk"
    type = "S"
  }

  attribute {
    name = "sk"
    type = "S"
  }

  server_side_encryption {
    enabled = true
  }

  point_in_time_recovery {
    enabled = var.enable_point_in_time_recovery && each.value.pitr
  }

  dynamic "ttl" {
    for_each = each.value.ttl == null ? [] : [each.value.ttl]

    content {
      attribute_name = ttl.value
      enabled        = true
    }
  }

  deletion_protection_enabled = each.value.protect

  tags = merge(var.tags, {
    "Name"      = each.value.name
    "component" = "dynamodb-${each.key}"
  })
}
