variable "name_prefix" {
  description = "Prefix for table names."
  type        = string
}

variable "enable_point_in_time_recovery" {
  description = "Enable DynamoDB PITR on the journal, event and registry tables. No owner durability promise exists (K-2); this is a technical choice for the synthetic journal, not a recovery SLA. Rev2 Security N-07 requires the PITR decision to be recorded explicitly against NV-05: it stays a deployment/cost input (default false) while table deletion protection is unconditional."
  type        = bool
  default     = false
}

variable "journal_ttl_attribute" {
  description = "DynamoDB TTL attribute for the journal table. null disables TTL because the per-record mapping is an open design item (NV-05)."
  type        = string
  default     = null
}

variable "event_ttl_attribute" {
  description = "DynamoDB TTL attribute for the event table. null disables TTL (NV-05 open)."
  type        = string
  default     = null
}

variable "connections_ttl_attribute" {
  description = "DynamoDB TTL attribute for WebSocket connection records. null disables TTL; the application owns the record shape."
  type        = string
  default     = null
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
  default     = {}
}
