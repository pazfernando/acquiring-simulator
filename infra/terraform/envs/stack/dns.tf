locals {
  # Fixed, documented CloudFront alias hosted-zone id.
  cloudfront_hosted_zone_id = "Z2FDTNDATAQYW2"
}

resource "aws_route53_record" "console_a" {
  zone_id = var.dns_zone_id
  name    = var.console_fqdn
  type    = "A"

  alias {
    name                   = module.console.distribution_domain_name
    zone_id                = local.cloudfront_hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "console_aaaa" {
  zone_id = var.dns_zone_id
  name    = var.console_fqdn
  type    = "AAAA"

  alias {
    name                   = module.console.distribution_domain_name
    zone_id                = local.cloudfront_hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "auth_a" {
  zone_id = var.dns_zone_id
  name    = var.auth_fqdn
  type    = "A"

  alias {
    name                   = module.keycloak.alb_dns_name
    zone_id                = module.keycloak.alb_zone_id
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "ws_a" {
  zone_id = var.dns_zone_id
  name    = var.ws_fqdn
  type    = "A"

  alias {
    name                   = module.ws.target_domain_name
    zone_id                = module.ws.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "api_a" {
  zone_id = var.dns_zone_id
  name    = var.api_fqdn
  type    = "A"

  alias {
    name                   = module.api.regional_domain_name
    zone_id                = module.api.regional_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "acl_a" {
  zone_id = var.dns_zone_id
  name    = var.acl_fqdn
  type    = "A"

  alias {
    name                   = module.mtls_acl.regional_domain_name
    zone_id                = module.mtls_acl.regional_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "processor_a" {
  zone_id = var.dns_zone_id
  name    = var.processor_fqdn
  type    = "A"

  alias {
    name                   = module.mtls_processor.regional_domain_name
    zone_id                = module.mtls_processor.regional_zone_id
    evaluate_target_health = false
  }
}
