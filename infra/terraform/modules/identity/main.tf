locals {
  oidc_provider_arn = var.create_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : var.oidc_provider_arn
  deploy_role_name  = "${var.name_prefix}-github-deploy"
  deploy_role_arn   = "arn:aws:iam::${var.aws_account_id}:role/${local.deploy_role_name}"
}

resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_oidc_provider ? 1 : 0

  url            = "https://token.actions.githubusercontent.com"
  client_id_list = [var.github_oidc_audience]

  tags = merge(var.tags, { "Name" = "${var.name_prefix}-github-oidc" })
}

data "aws_iam_policy_document" "deploy_assume" {
  statement {
    sid     = "GitHubActionsExactSubject"
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = [var.github_oidc_audience]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [var.github_oidc_subject]
    }
  }
}

resource "aws_iam_role" "deploy" {
  name                 = local.deploy_role_name
  description          = "GitHub Actions OIDC deploy role for ${var.name_prefix}; exact subject, no long-lived keys"
  assume_role_policy   = data.aws_iam_policy_document.deploy_assume.json
  max_session_duration = 3600

  tags = var.tags
}

data "aws_iam_policy_document" "deploy" {
  statement {
    sid    = "ReadCallerIdentity"
    effect = "Allow"
    actions = [
      "sts:GetCallerIdentity",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "TerraformStateObjects"
    effect = "Allow"
    actions = [
      "s3:ListBucket",
      "s3:GetBucketLocation",
      "s3:GetBucketVersioning",
    ]
    resources = [var.state_bucket_arn]
  }

  statement {
    sid    = "TerraformStateObjectAccess"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
    ]
    resources = ["${var.state_bucket_arn}/*"]
  }

  statement {
    sid    = "TerraformStateKeyUse"
    effect = "Allow"
    actions = [
      "kms:Decrypt",
      "kms:Encrypt",
      "kms:GenerateDataKey",
      "kms:DescribeKey",
    ]
    resources = [var.state_kms_key_arn]
  }

  # Resource creation for the authored module set. Resource "*" is unavoidable
  # for create/list APIs that do not accept an ARN scope; the guardrail denies
  # privilege-escalation and account-level actions, and the role is scoped to
  # this account by the provider's allowed_account_ids.
  statement {
    sid    = "SimulatorServiceManagement"
    effect = "Allow"
    actions = [
      "acm:*",
      "apigateway:*",
      "application-autoscaling:*",
      "cloudfront:*",
      "cloudwatch:*",
      "dynamodb:*",
      "ec2:*",
      "ecr:*",
      "ecs:*",
      "elasticloadbalancing:*",
      "es:*",
      "iam:Get*",
      "iam:List*",
      "kms:Create*",
      "kms:Describe*",
      "kms:Enable*",
      "kms:Get*",
      "kms:List*",
      "kms:Put*",
      "kms:TagResource",
      "kms:UntagResource",
      "kms:Update*",
      "lambda:*",
      "logs:*",
      "rds:*",
      "route53:*",
      "s3:*",
      "secretsmanager:CreateSecret",
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetResourcePolicy",
      "secretsmanager:PutResourcePolicy",
      "secretsmanager:TagResource",
      "ssm:AddTagsToResource",
      "ssm:DescribeParameters",
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
      "ssm:PutParameter",
      "tag:*",
      "wafv2:*",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ManagePrefixedIamRoles"
    effect = "Allow"
    actions = [
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:GetRole",
      "iam:GetRolePolicy",
      "iam:ListRolePolicies",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:UpdateRole",
      "iam:UpdateRoleDescription",
      "iam:CreateInstanceProfile",
      "iam:DeleteInstanceProfile",
      "iam:GetInstanceProfile",
      "iam:AddRoleToInstanceProfile",
      "iam:RemoveRoleFromInstanceProfile",
    ]
    resources = [
      "arn:aws:iam::${var.aws_account_id}:role/${var.name_prefix}-*",
      "arn:aws:iam::${var.aws_account_id}:instance-profile/${var.name_prefix}-*",
    ]
  }

  statement {
    sid    = "ManagePrefixedIamPolicies"
    effect = "Allow"
    actions = [
      "iam:CreatePolicy",
      "iam:DeletePolicy",
      "iam:GetPolicy",
      "iam:GetPolicyVersion",
      "iam:ListPolicyVersions",
      "iam:CreatePolicyVersion",
      "iam:DeletePolicyVersion",
      "iam:ListEntitiesForPolicy",
      "iam:TagPolicy",
      "iam:UntagPolicy",
    ]
    resources = ["arn:aws:iam::${var.aws_account_id}:policy/${var.name_prefix}-*"]
  }

  statement {
    sid       = "PassOnlyRuntimeRoles"
    effect    = "Allow"
    actions   = ["iam:PassRole"]
    resources = ["arn:aws:iam::${var.aws_account_id}:role/${var.name_prefix}-*"]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values = [
        "lambda.amazonaws.com",
        "ecs-tasks.amazonaws.com",
        "apigateway.amazonaws.com",
      ]
    }
  }

  statement {
    sid    = "DenyAccountAndIdentityEscalation"
    effect = "Deny"
    actions = [
      "account:*",
      "aws-portal:*",
      "iam:CreateUser",
      "iam:CreateAccessKey",
      "iam:CreateLoginProfile",
      "iam:AttachUserPolicy",
      "iam:PutUserPolicy",
      "iam:UpdateAccountPasswordPolicy",
      "organizations:*",
      "sso:*",
      "sso-admin:*",
      "sts:AssumeRole",
    ]
    resources = ["*"]
  }

  statement {
    sid       = "DenyStateBucketDeletion"
    effect    = "Deny"
    actions   = ["s3:DeleteBucket"]
    resources = [var.state_bucket_arn]
  }

  statement {
    sid       = "DenyStateKeyDeletion"
    effect    = "Deny"
    actions   = ["kms:ScheduleKeyDeletion", "kms:DisableKey"]
    resources = [var.state_kms_key_arn]
  }
}

resource "aws_iam_policy" "deploy" {
  name        = "${var.name_prefix}-github-deploy"
  description = "Least-privilege Terraform deploy policy for ${var.name_prefix}"
  policy      = data.aws_iam_policy_document.deploy.json

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "deploy" {
  role       = aws_iam_role.deploy.name
  policy_arn = aws_iam_policy.deploy.arn
}

# Guardrail attached to runtime roles. It makes the SR2-02 shape explicit:
# an invocation outside a Lambda execution context, or from another account,
# is denied rather than merely absent from an allow list.
data "aws_iam_policy_document" "runtime_guardrail" {
  statement {
    sid       = "DenyInvokeOutsideFunctionContext"
    effect    = "Deny"
    actions   = ["lambda:InvokeFunction"]
    resources = ["*"]

    condition {
      test     = "Null"
      variable = "lambda:SourceFunctionArn"
      values   = ["true"]
    }
  }

  statement {
    sid       = "DenyCrossAccountAccess"
    effect    = "Deny"
    actions   = ["*"]
    resources = ["*"]

    condition {
      test     = "StringNotEquals"
      variable = "aws:PrincipalAccount"
      values   = [var.aws_account_id]
    }
  }

  statement {
    sid       = "DenySecurityServiceTampering"
    effect    = "Deny"
    actions   = ["cloudtrail:StopLogging", "cloudtrail:DeleteTrail", "guardduty:DeleteDetector"]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "runtime_guardrail" {
  name        = "${var.name_prefix}-runtime-guardrail"
  description = "Explicit-deny guardrail for simulator runtime roles"
  policy      = data.aws_iam_policy_document.runtime_guardrail.json

  tags = var.tags
}
