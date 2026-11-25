locals {
  name_prefix = "acq-sim-${var.environment}"
  realm_name  = var.realm_name == "" ? "acq-${var.environment}" : var.realm_name
  issuer_url  = "https://${var.auth_fqdn}/realms/${local.realm_name}"

  common_tags = merge(
    {
      "app"         = "acquiring-simulator"
      "environment" = var.environment
      "managed-by"  = "terraform"
    },
    var.tags,
  )
}

##########################################################
# N-02 closure: CloudFront origin-verification value
#
# Owner Option B ("adelante con lo recomendado", 2026-11-23): the console API
# keeps its native JWT authorizer and gains one fail-closed origin-verification
# header. The value is Terraform-generated here so that no literal exists in
# any file, no tfvars or CI variable carries it, and it lives only in encrypted
# remote state (and in the CloudFront origin configuration it must populate).
# `random_password.result` is sensitive in the provider schema and the value is
# passed through a sensitive variable, so plan/apply output redacts it.
##########################################################

resource "random_password" "origin_verify" {
  length      = 48
  upper       = true
  lower       = true
  numeric     = true
  special     = false # alphanumeric keeps it an RFC 7230 token: safe in a header
  min_upper   = 8
  min_lower   = 8
  min_numeric = 8
}

module "network" {
  source = "../../modules/network"

  name_prefix             = local.name_prefix
  availability_zones      = var.availability_zones
  create_nat_gateways     = var.create_nat_gateways
  flow_log_retention_days = var.log_retention_days
  tags                    = local.common_tags
}

module "data" {
  source = "../../modules/data"

  name_prefix                   = local.name_prefix
  enable_point_in_time_recovery = var.enable_point_in_time_recovery
  journal_ttl_attribute         = var.journal_ttl_attribute
  event_ttl_attribute           = var.event_ttl_attribute
  connections_ttl_attribute     = var.connections_ttl_attribute
  tags                          = local.common_tags
}

module "identity" {
  source = "../../modules/identity"

  name_prefix          = local.name_prefix
  aws_account_id       = var.aws_account_id
  state_bucket_arn     = var.state_bucket_arn
  state_kms_key_arn    = var.state_kms_key_arn
  github_oidc_subject  = var.github_oidc_subject
  create_oidc_provider = var.create_oidc_provider
  oidc_provider_arn    = var.existing_oidc_provider_arn
  tags                 = local.common_tags
}

module "api" {
  source = "../../modules/api"

  name_prefix          = local.name_prefix
  environment          = var.environment
  api_audience         = var.console_audience
  issuer_url           = local.issuer_url
  api_fqdn             = var.api_fqdn
  certificate_arn      = var.api_certificate_arn
  demo_subject         = var.demo_subject
  artifact             = var.console_api_artifact
  edge_origin_verify   = random_password.origin_verify.result
  journal_table_name   = module.data.journal_table
  journal_table_arn    = module.data.table_arns["journal"]
  event_table_name     = module.data.event_table
  event_table_arn      = module.data.table_arns["events"]
  registry_table_name  = module.data.registry_table
  registry_table_arn   = module.data.table_arns["registry"]
  guardrail_policy_arn = module.identity.runtime_guardrail_policy_arn
  log_retention_days   = var.log_retention_days
  tags                 = local.common_tags
}

module "console" {
  source = "../../modules/console"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  name_prefix          = local.name_prefix
  aws_account_id       = var.aws_account_id
  console_fqdn         = var.console_fqdn
  console_bucket_name  = var.console_bucket_name
  certificate_arn      = var.console_certificate_arn
  http_api_domain_name = module.api.origin_domain_name
  origin_verify_secret = random_password.origin_verify.result
  auth_fqdn            = var.auth_fqdn
  ws_fqdn              = var.ws_fqdn
  issuer_url           = local.issuer_url
  console_audience     = var.console_audience
  waf_api_rate_limit   = var.waf_api_rate_limit
  log_retention_days   = var.log_retention_days
  tags                 = local.common_tags
}

module "ws" {
  source = "../../modules/ws"

  name_prefix            = local.name_prefix
  aws_region             = var.aws_region
  aws_account_id         = var.aws_account_id
  ws_fqdn                = var.ws_fqdn
  certificate_arn        = var.ws_certificate_arn
  ws_api_audience        = var.ws_audience
  demo_subject           = var.demo_subject
  allowed_origin         = "https://${var.console_fqdn}"
  authorizer_artifact    = var.ws_authorizer_artifact
  notifier_artifact      = var.ws_notifier_artifact
  connections_table_name = module.data.connections_table
  connections_table_arn  = module.data.table_arns["connections"]
  guardrail_policy_arn   = module.identity.runtime_guardrail_policy_arn
  log_retention_days     = var.log_retention_days
  tags                   = local.common_tags
}

module "keycloak" {
  source = "../../modules/keycloak"

  name_prefix                = local.name_prefix
  aws_account_id             = var.aws_account_id
  aws_region                 = var.aws_region
  vpc_id                     = module.network.vpc_id
  public_subnet_ids          = module.network.public_subnet_ids
  private_subnet_ids         = module.network.private_subnet_ids
  alb_security_group_id      = module.network.alb_security_group_id
  app_security_group_id      = module.network.app_security_group_id
  db_security_group_id       = module.network.db_security_group_id
  auth_fqdn                  = var.auth_fqdn
  certificate_arn            = var.auth_certificate_arn
  realm_name                 = local.realm_name
  keycloak_image_digest      = var.keycloak_image_digest
  keycloak_cpu               = var.keycloak_cpu
  keycloak_memory            = var.keycloak_memory
  task_cpu_architecture      = var.keycloak_task_cpu_architecture
  aurora_engine_version      = var.aurora_engine_version
  db_min_acu                 = var.db_min_acu
  db_max_acu                 = var.db_max_acu
  bootstrap_admin_secret_arn = var.bootstrap_admin_secret_arn
  enable_container_insights  = var.enable_container_insights
  waf_rate_limit             = var.waf_login_rate_limit
  create_waf                 = var.create_keycloak_waf
  log_retention_days         = var.log_retention_days
  tags                       = local.common_tags
}

module "mtls_acl" {
  source = "../../modules/mtls"

  name_prefix               = "${local.name_prefix}-acl"
  api_fqdn                  = var.acl_fqdn
  certificate_arn           = var.acl_certificate_arn
  truststore_uri            = var.acl_truststore_uri
  truststore_version        = var.acl_truststore_version
  backend_lambda_invoke_arn = var.acl_backend_lambda_invoke_arn
  backend_lambda_name       = var.acl_backend_lambda_name
  authorizer_artifact       = var.mtls_authorizer_artifact
  expected_audience         = var.acl_audience
  required_scope            = "console.dispatch"
  guardrail_policy_arn      = module.identity.runtime_guardrail_policy_arn
  log_retention_days        = var.log_retention_days
  tags                      = local.common_tags
}

module "mtls_processor" {
  source = "../../modules/mtls"

  name_prefix               = "${local.name_prefix}-processor"
  api_fqdn                  = var.processor_fqdn
  certificate_arn           = var.processor_certificate_arn
  truststore_uri            = var.processor_truststore_uri
  truststore_version        = var.processor_truststore_version
  backend_lambda_invoke_arn = var.processor_backend_lambda_invoke_arn
  backend_lambda_name       = var.processor_backend_lambda_name
  authorizer_artifact       = var.mtls_authorizer_artifact
  expected_audience         = var.simulator_audience
  required_scope            = "sim.iso87a.authorize"
  guardrail_policy_arn      = module.identity.runtime_guardrail_policy_arn
  log_retention_days        = var.log_retention_days
  tags                      = local.common_tags
}

module "observability" {
  source = "../../modules/observability"

  name_prefix    = local.name_prefix
  alb_arn_suffix = module.keycloak.alb_arn_suffix

  api_ids = {
    console = module.api.api_id
    ws      = module.ws.api_id
  }

  lambda_function_names = toset([
    module.api.lambda_function_name,
    module.ws.notifier_function_name,
    module.mtls_acl.authorizer_function_name,
    module.mtls_processor.authorizer_function_name,
    module.ws.authorizer_function_name,
  ])

  dynamodb_table_names = toset(values(module.data.table_names))

  ecs_cluster_name = module.keycloak.cluster_name
  ecs_service_name = module.keycloak.service_name

  alarm_topic_arn = var.alarm_topic_arn
  tags            = local.common_tags
}
