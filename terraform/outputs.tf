output "aws_region" {
  value = var.aws_region
}

output "eks_cluster_name" {
  value = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "eks_oidc_provider" {
  value = module.eks.oidc_provider
}

output "msk_bootstrap_brokers_sasl_iam" {
  value       = try(aws_msk_cluster.this[0].bootstrap_brokers_sasl_iam, null)
  description = "Bootstrap brokers para MSK (SASL/IAM)"
}

output "sa_producer_role_arn" {
  value = aws_iam_role.sa_producer.arn
}

output "sa_consumer_role_arn" {
  value = aws_iam_role.sa_consumer.arn
}

output "sa_aggregator_role_arn" {
  value = aws_iam_role.sa_aggregator.arn
}

output "s3_input_bucket" {
  value = aws_s3_bucket.input.bucket
}

output "s3_rejected_bucket" {
  value = aws_s3_bucket.rejected.bucket
}

output "s3_reports_bucket" {
  value = aws_s3_bucket.reports.bucket
}

output "producer_ecr_repository_url" {
  value       = try(aws_ecr_repository.producer[0].repository_url, null)
  description = "Producer ECR repository URL"
}

output "consumer_ecr_repository_url" {
  value       = try(aws_ecr_repository.consumer[0].repository_url, null)
  description = "Consumer ECR repository URL"
}

output "aggregator_ecr_repository_url" {
  value       = try(aws_ecr_repository.aggregator[0].repository_url, null)
  description = "Aggregator ECR repository URL"
}

output "cloudwatch_dashboard_url" {
  value       = "https://console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=${local.name}-etl-dashboard"
  description = "CloudWatch Dashboard URL"
}

output "cloudwatch_log_groups" {
  value = {
    producer   = aws_cloudwatch_log_group.producer.name
    consumer   = aws_cloudwatch_log_group.consumer.name
    aggregator = aws_cloudwatch_log_group.aggregator.name
  }
  description = "CloudWatch Log Groups"
}

output "sns_alerts_topic_arn" {
  value       = aws_sns_topic.alerts.arn
  description = "SNS Topic ARN for alerts"
}
