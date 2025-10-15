locals {
  name = lower(replace(var.project_name, "_", "-"))
}

data "aws_caller_identity" "current" {}
data "aws_availability_zones" "available" {}

######################
# VPC (módulo oficial)
######################
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "6.4.0"

  name = "${local.name}-vpc"
  cidr = "10.0.0.0/16"

  azs             = slice(data.aws_availability_zones.available.names, 0, var.az_count)
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24"]
  public_subnets  = ["10.0.11.0/24", "10.0.12.0/24"]

  enable_nat_gateway   = true
  single_nat_gateway   = true
  enable_dns_hostnames = true
  enable_dns_support   = true
}

#########################
# EKS (Fargate-only/IRSA)
#########################
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "21.4.0"

  cluster_name    = "${local.name}-eks"
  cluster_version = var.eks_version

  vpc_id                   = module.vpc.vpc_id
  subnet_ids               = module.vpc.private_subnets
  control_plane_subnet_ids = module.vpc.private_subnets

  # Permitir acesso público ao API server
  cluster_endpoint_public_access  = true
  cluster_endpoint_private_access = true

  enable_irsa = true

  # EKS Managed Node Groups
  eks_managed_node_groups = {
    workers = {
      name           = "workers"
      use_name_prefix = false

      subnet_ids = module.vpc.private_subnets

      min_size     = 1
      max_size     = 3
      desired_size = 2

      ami_type                   = "AL2_x86_64"
      instance_types             = ["t3.small"]
      capacity_type              = "ON_DEMAND"

      force_update_version = false
      update_config = {
        max_unavailable_percentage = 33
      }

      labels = {
        Environment = "production"
        NodeType    = "worker"
      }

      taints = {}

      tags = {
        ExtraTag = "EKS managed node group"
      }
    }
  }

  fargate_profiles = {
    etl = {
      name = "fp-etl"
      selectors = [
        { namespace = "etl" }
      ]
      subnet_ids = module.vpc.private_subnets
    }
  }
}

###################
# MSK Serverless
###################
resource "aws_security_group" "msk" {
  name        = "${local.name}-msk-sg"
  description = "SG for MSK Serverless"
  vpc_id      = module.vpc.vpc_id

  ingress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["10.0.0.0/16"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_msk_cluster" "this" {
  count               = var.msk_enable ? 1 : 0
  cluster_name        = "${local.name}-msk"
  kafka_version       = "3.5.1"
  number_of_broker_nodes = 2

  broker_node_group_info {
    instance_type   = "kafka.t3.small"
    client_subnets  = module.vpc.private_subnets
    security_groups = [aws_security_group.msk.id]
    
    storage_info {
      ebs_storage_info {
        volume_size = 20
      }
    }
  }

  client_authentication {
    sasl {
      iam = true
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.this[0].arn
    revision = aws_msk_configuration.this[0].latest_revision
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk[0].name
      }
    }
  }

  tags = {
    Name    = "${local.name}-msk"
    Project = local.name
  }
}

resource "aws_msk_configuration" "this" {
  count          = var.msk_enable ? 1 : 0
  kafka_versions = ["3.5.1"]
  name           = "${local.name}-msk-config"

  server_properties = <<PROPERTIES
auto.create.topics.enable=true
default.replication.factor=2
min.insync.replicas=1
num.partitions=3
log.retention.hours=168
PROPERTIES
}

resource "aws_cloudwatch_log_group" "msk" {
  count             = var.msk_enable ? 1 : 0
  name              = "/aws/msk/${local.name}-msk"
  retention_in_days = 7
}

###################
# S3 Buckets
###################
resource "aws_s3_bucket" "input" {
  bucket        = "${local.name}-input-${data.aws_caller_identity.current.account_id}"
  force_destroy = true
}

resource "aws_s3_bucket" "rejected" {
  bucket        = "${local.name}-rejected-${data.aws_caller_identity.current.account_id}"
  force_destroy = true
}

resource "aws_s3_bucket" "reports" {
  bucket        = "${local.name}-reports-${data.aws_caller_identity.current.account_id}"
  force_destroy = true
}

###################
# DynamoDB
###################
resource "aws_dynamodb_table" "processed_files" {
  name         = "${local.name}-processed-files"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "file_key"

  attribute {
    name = "file_key"
    type = "S"
  }
}

resource "aws_dynamodb_table" "transactions" {
  name         = "${local.name}-transactions"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "transaction_id"

  attribute {
    name = "transaction_id"
    type = "S"
  }
}

###################
# SSM Parameters
###################
resource "aws_ssm_parameter" "feature_accept_pix" {
  name  = "/${local.name}/feature/acceptPIX"
  type  = "String"
  value = "true"
}

resource "aws_ssm_parameter" "feature_circuit_breaker_threshold" {
  name  = "/${local.name}/feature/circuitBreakerThreshold"
  type  = "String"
  value = "0.15"
}

###################
# CloudWatch Logs
###################
resource "aws_cloudwatch_log_group" "apps" {
  name              = "/${local.name}/apps"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "producer" {
  name              = "/${local.name}/producer"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "consumer" {
  name              = "/${local.name}/consumer"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "aggregator" {
  name              = "/${local.name}/aggregator"
  retention_in_days = 14
}

###################
# CloudWatch Alarms
###################
resource "aws_cloudwatch_metric_alarm" "producer_high_error_rate" {
  alarm_name          = "${local.name}-producer-high-error-rate"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "Errors"
  namespace           = "AWS/EKS/Container"
  period              = "300"
  statistic           = "Sum"
  threshold           = "10"
  alarm_description   = "This metric monitors producer error rate"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    ContainerName = "producer"
    Namespace     = "etl"
  }
}

resource "aws_cloudwatch_metric_alarm" "consumer_lag" {
  alarm_name          = "${local.name}-consumer-lag"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "ConsumerLag"
  namespace           = "AWS/MSK"
  period              = "300"
  statistic           = "Average"
  threshold           = "1000"
  alarm_description   = "This metric monitors consumer lag"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "dynamodb_throttling" {
  alarm_name          = "${local.name}-dynamodb-throttling"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "UserErrors"
  namespace           = "AWS/DynamoDB"
  period              = "300"
  statistic           = "Sum"
  threshold           = "0"
  alarm_description   = "This metric monitors DynamoDB throttling"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    TableName = aws_dynamodb_table.transactions.name
  }
}

###################
# SNS for Alerts
###################
resource "aws_sns_topic" "alerts" {
  name = "${local.name}-alerts"
}

resource "aws_sns_topic_subscription" "email_alerts" {
  count     = var.alert_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

###################
# CloudWatch Dashboard
###################
resource "aws_cloudwatch_dashboard" "etl_dashboard" {
  dashboard_name = "${local.name}-etl-dashboard"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6

        properties = {
          metrics = [
            ["AWS/EKS/Container", "CPUUtilization", "ContainerName", "producer", "Namespace", "etl"],
            ["AWS/EKS/Container", "CPUUtilization", "ContainerName", "consumer", "Namespace", "etl"],
            ["AWS/EKS/Container", "CPUUtilization", "ContainerName", "aggregator", "Namespace", "etl"]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Application CPU Utilization"
          period  = 300
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6

        properties = {
          metrics = [
            ["AWS/EKS/Container", "MemoryUtilization", "ContainerName", "producer", "Namespace", "etl"],
            ["AWS/EKS/Container", "MemoryUtilization", "ContainerName", "consumer", "Namespace", "etl"],
            ["AWS/EKS/Container", "MemoryUtilization", "ContainerName", "aggregator", "Namespace", "etl"]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Application Memory Utilization"
          period  = 300
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 12
        width  = 12
        height = 6

        properties = {
          metrics = [
            ["AWS/DynamoDB", "ConsumedReadCapacityUnits", "TableName", aws_dynamodb_table.transactions.name],
            ["AWS/DynamoDB", "ConsumedWriteCapacityUnits", "TableName", aws_dynamodb_table.transactions.name]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "DynamoDB Capacity Usage"
          period  = 300
        }
      },
      {
        type   = "log"
        x      = 0
        y      = 18
        width  = 24
        height = 6

        properties = {
          query   = "SOURCE '/${local.name}/producer' | fields @timestamp, @message | filter @message like /ERROR/ | sort @timestamp desc | limit 20"
          region  = var.aws_region
          title   = "Recent Errors"
          view    = "table"
        }
      }
    ]
  })
}

###################
# ECR (opcional)
###################
resource "aws_ecr_repository" "producer" {
  count = var.create_ecr ? 1 : 0
  name  = "${local.name}-producer"
  image_scanning_configuration { scan_on_push = true }
}

resource "aws_ecr_repository" "consumer" {
  count = var.create_ecr ? 1 : 0
  name  = "${local.name}-consumer"
  image_scanning_configuration { scan_on_push = true }
}

resource "aws_ecr_repository" "aggregator" {
  count = var.create_ecr ? 1 : 0
  name  = "${local.name}-aggregator"
  image_scanning_configuration { scan_on_push = true }
}

#############################
# IRSA - IAM roles/Policies
#############################
data "aws_iam_policy_document" "assume_role_irsa" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    effect  = "Allow"
    principals {
      type        = "Federated"
      identifiers = [module.eks.oidc_provider_arn]
    }
    condition {
      test     = "StringEquals"
      variable = "${module.eks.oidc_provider}:sub"
      values = [
        "system:serviceaccount:etl:sa-producer",
        "system:serviceaccount:etl:sa-consumer",
        "system:serviceaccount:etl:sa-aggregator",
      ]
    }
    condition {
      test     = "StringEquals"
      variable = "${module.eks.oidc_provider}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "sa_producer" {
  name               = "${local.name}-sa-producer"
  assume_role_policy = data.aws_iam_policy_document.assume_role_irsa.json
}

resource "aws_iam_role" "sa_consumer" {
  name               = "${local.name}-sa-consumer"
  assume_role_policy = data.aws_iam_policy_document.assume_role_irsa.json
}

resource "aws_iam_role" "sa_aggregator" {
  name               = "${local.name}-sa-aggregator"
  assume_role_policy = data.aws_iam_policy_document.assume_role_irsa.json
}

# Políticas mínimas
data "aws_iam_policy_document" "producer_policy" {
  statement {
    actions = ["s3:GetObject", "s3:ListBucket"]
    resources = [
      aws_s3_bucket.input.arn,
      "${aws_s3_bucket.input.arn}/*"
    ]
  }
  statement {
    actions = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.rejected.arn}/*"]
  }
  statement {
    actions   = ["ssm:GetParameter", "ssm:GetParametersByPath"]
    resources = ["*"]
  }
  statement {
    actions   = ["kafka-cluster:Connect", "kafka-cluster:DescribeCluster", "kafka-cluster:AlterCluster", "kafka-cluster:DescribeClusterDynamicConfiguration", "kafka-cluster:WriteData", "kafka-cluster:DescribeTopic", "kafka-cluster:CreateTopic"]
    resources = ["*"]
  }
  statement {
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["*"]
  }
  statement {
    actions = ["dynamodb:GetItem", "dynamodb:PutItem"]
    resources = [aws_dynamodb_table.processed_files.arn]
  }
}

resource "aws_iam_policy" "producer_policy" {
  name   = "${local.name}-producer-policy"
  policy = data.aws_iam_policy_document.producer_policy.json
}

resource "aws_iam_role_policy_attachment" "producer_attach" {
  role       = aws_iam_role.sa_producer.name
  policy_arn = aws_iam_policy.producer_policy.arn
}

# Consumer
data "aws_iam_policy_document" "consumer_policy" {
  statement {
    actions   = ["kafka-cluster:Connect", "kafka-cluster:DescribeCluster", "kafka-cluster:DescribeTopic", "kafka-cluster:ReadData"]
    resources = ["*"]
  }
  statement {
    actions = ["dynamodb:PutItem", "dynamodb:GetItem", "dynamodb:UpdateItem"]
    resources = [aws_dynamodb_table.transactions.arn]
  }
  statement {
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "consumer_policy" {
  name   = "${local.name}-consumer-policy"
  policy = data.aws_iam_policy_document.consumer_policy.json
}

resource "aws_iam_role_policy_attachment" "consumer_attach" {
  role       = aws_iam_role.sa_consumer.name
  policy_arn = aws_iam_policy.consumer_policy.arn
}

# Aggregator
data "aws_iam_policy_document" "aggregator_policy" {
  statement {
    actions   = ["kafka-cluster:Connect", "kafka-cluster:DescribeTopic", "kafka-cluster:ReadData"]
    resources = ["*"]
  }
  statement {
    actions = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.reports.arn}/*"]
  }
  statement {
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "aggregator_policy" {
  name   = "${local.name}-aggregator-policy"
  policy = data.aws_iam_policy_document.aggregator_policy.json
}

resource "aws_iam_role_policy_attachment" "aggregator_attach" {
  role       = aws_iam_role.sa_aggregator.name
  policy_arn = aws_iam_policy.aggregator_policy.arn
}

###################
# VPC Endpoints
###################

# S3 Gateway Endpoint
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = module.vpc.vpc_id
  service_name      = "com.amazonaws.sa-east-1.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = module.vpc.private_route_table_ids

  tags = {
    Name = "${local.name}-s3-endpoint"
    Project = local.name
  }
}

# DynamoDB Gateway Endpoint
resource "aws_vpc_endpoint" "dynamodb" {
  vpc_id            = module.vpc.vpc_id
  service_name      = "com.amazonaws.sa-east-1.dynamodb"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = module.vpc.private_route_table_ids

  tags = {
    Name = "${local.name}-dynamodb-endpoint"
    Project = local.name
  }
}

# Security group for VPC endpoints
resource "aws_security_group" "vpc_endpoints" {
  name_prefix = "${local.name}-vpc-endpoints-"
  vpc_id      = module.vpc.vpc_id

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [module.vpc.vpc_cidr_block]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name}-vpc-endpoints-sg"
    Project = local.name
  }
}

# CloudWatch Logs Interface Endpoint
resource "aws_vpc_endpoint" "logs" {
  vpc_id              = module.vpc.vpc_id
  service_name        = "com.amazonaws.sa-east-1.logs"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = module.vpc.private_subnets
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  
  private_dns_enabled = true

  tags = {
    Name = "${local.name}-logs-endpoint"
    Project = local.name
  }
}

# EC2 Interface Endpoint (for IRSA token exchange)
resource "aws_vpc_endpoint" "ec2" {
  vpc_id              = module.vpc.vpc_id
  service_name        = "com.amazonaws.sa-east-1.ec2"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = module.vpc.private_subnets
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  
  private_dns_enabled = true

  tags = {
    Name = "${local.name}-ec2-endpoint"
    Project = local.name
  }
}

# STS Interface Endpoint (for IRSA token exchange)
resource "aws_vpc_endpoint" "sts" {
  vpc_id              = module.vpc.vpc_id
  service_name        = "com.amazonaws.sa-east-1.sts"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = module.vpc.private_subnets
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  
  private_dns_enabled = true

  tags = {
    Name = "${local.name}-sts-endpoint"
    Project = local.name
  }
}
