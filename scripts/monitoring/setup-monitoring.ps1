# Configure CloudWatch Monitoring for ETL System
# This script sets up comprehensive CloudWatch monitoring

param(
    [string]$AwsRegion = "us-east-1",
    [string]$ProjectName = "aws-eks-msk-starter",
    [string]$AlertEmail = ""
)

Write-Host "===== CloudWatch Monitoring Setup =====" -ForegroundColor Green

# Get Terraform outputs
Write-Host "Getting Terraform outputs..." -ForegroundColor Yellow
Push-Location terraform

try {
    $EksClusterName = terraform output -raw eks_cluster_name
    $DashboardUrl = terraform output -raw cloudwatch_dashboard_url
    $LogGroups = terraform output -json cloudwatch_log_groups | ConvertFrom-Json
    $SnsTopicArn = terraform output -raw sns_alerts_topic_arn
}
catch {
    Write-Error "Failed to get Terraform outputs. Make sure terraform has been applied with CloudWatch resources."
    exit 1
}
finally {
    Pop-Location
}

Write-Host "EKS Cluster: $EksClusterName" -ForegroundColor Cyan
Write-Host "Dashboard URL: $DashboardUrl" -ForegroundColor Cyan

# Update kubeconfig
Write-Host "Updating kubeconfig..." -ForegroundColor Yellow
aws eks update-kubeconfig --region $AwsRegion --name $EksClusterName

# Update Fluent Bit configuration with actual cluster name
Write-Host "Updating Fluent Bit configuration..." -ForegroundColor Yellow
$fluentBitConfig = Get-Content k8s/cloudwatch-logging.yaml -Raw
$fluentBitConfig = $fluentBitConfig -replace "cluster.name: aws-eks-msk-starter-eks", "cluster.name: $EksClusterName"
$fluentBitConfig = $fluentBitConfig -replace "logs.region: us-east-1", "logs.region: $AwsRegion"
$fluentBitConfig = $fluentBitConfig -replace "/aws-eks-msk-starter/apps", "/$ProjectName/apps"

# Apply Fluent Bit for log shipping
Write-Host "Deploying Fluent Bit for log shipping..." -ForegroundColor Yellow
$fluentBitConfig | kubectl apply -f -

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Fluent Bit deployed successfully" -ForegroundColor Green
} else {
    Write-Error "Failed to deploy Fluent Bit"
}

# Wait for Fluent Bit to be ready
Write-Host "Waiting for Fluent Bit pods to be ready..." -ForegroundColor Yellow
kubectl wait --for=condition=ready pod -l name=fluent-bit -n amazon-cloudwatch --timeout=300s

# Setup SNS subscription if email provided
if ($AlertEmail -ne "") {
    Write-Host "Setting up email alerts..." -ForegroundColor Yellow
    try {
        aws sns subscribe --topic-arn $SnsTopicArn --protocol email --notification-endpoint $AlertEmail
        Write-Host "✓ Email subscription created. Please check your email and confirm the subscription." -ForegroundColor Green
    }
    catch {
        Write-Warning "Failed to create email subscription. You can do this manually in the AWS Console."
    }
}

# Create custom metrics for applications
Write-Host "Creating custom metric filters..." -ForegroundColor Yellow

# Producer error rate metric
aws logs put-metric-filter `
    --log-group-name $LogGroups.producer `
    --filter-name "ProducerErrors" `
    --filter-pattern "ERROR" `
    --metric-transformations `
        metricName=ProducerErrorRate,metricNamespace=ETL/Producer,metricValue=1,defaultValue=0

# Consumer processing rate metric
aws logs put-metric-filter `
    --log-group-name $LogGroups.consumer `
    --filter-name "ConsumerProcessedTransactions" `
    --filter-pattern "[timestamp, level=INFO, logger, message=\"Successfully processed and acknowledged transaction*\"]" `
    --metric-transformations `
        metricName=ProcessedTransactions,metricNamespace=ETL/Consumer,metricValue=1,defaultValue=0

# Aggregator report generation metric
aws logs put-metric-filter `
    --log-group-name $LogGroups.aggregator `
    --filter-name "ReportGeneration" `
    --filter-pattern "[timestamp, level=INFO, logger, message=\"Successfully generated and saved reports*\"]" `
    --metric-transformations `
        metricName=ReportsGenerated,metricNamespace=ETL/Aggregator,metricValue=1,defaultValue=0

Write-Host "✓ Custom metric filters created" -ForegroundColor Green

# Display monitoring information
Write-Host ""
Write-Host "===== Monitoring Setup Complete =====" -ForegroundColor Green
Write-Host ""
Write-Host "📊 CloudWatch Dashboard:" -ForegroundColor Cyan
Write-Host $DashboardUrl -ForegroundColor White
Write-Host ""
Write-Host "📋 Log Groups Created:" -ForegroundColor Cyan
Write-Host "  Producer:   $($LogGroups.producer)" -ForegroundColor White
Write-Host "  Consumer:   $($LogGroups.consumer)" -ForegroundColor White
Write-Host "  Aggregator: $($LogGroups.aggregator)" -ForegroundColor White
Write-Host ""
Write-Host "🔔 SNS Topic for Alerts:" -ForegroundColor Cyan
Write-Host $SnsTopicArn -ForegroundColor White
Write-Host ""
Write-Host "📈 Custom Metrics Created:" -ForegroundColor Cyan
Write-Host "  ETL/Producer/ProducerErrorRate" -ForegroundColor White
Write-Host "  ETL/Consumer/ProcessedTransactions" -ForegroundColor White  
Write-Host "  ETL/Aggregator/ReportsGenerated" -ForegroundColor White
Write-Host ""
Write-Host "🔍 Useful CloudWatch Commands:" -ForegroundColor Cyan
Write-Host "# View recent logs" -ForegroundColor Gray
Write-Host "  aws logs tail $($LogGroups.producer) --follow" -ForegroundColor White
Write-Host "  aws logs tail $($LogGroups.consumer) --follow" -ForegroundColor White
Write-Host "  aws logs tail $($LogGroups.aggregator) --follow" -ForegroundColor White
Write-Host ""
Write-Host "# Query logs for errors" -ForegroundColor Gray
Write-Host "  aws logs start-query --log-group-name $($LogGroups.producer) --start-time 1640995200 --end-time 1640998800 --query-string 'fields @timestamp, @message | filter @message like /ERROR/ | sort @timestamp desc | limit 20'" -ForegroundColor White
Write-Host ""
Write-Host "# Check Fluent Bit status" -ForegroundColor Gray
Write-Host "  kubectl get pods -n amazon-cloudwatch" -ForegroundColor White
Write-Host "  kubectl logs -f daemonset/fluent-bit -n amazon-cloudwatch" -ForegroundColor White
Write-Host ""

if ($AlertEmail -ne "") {
    Write-Host "📧 Please check your email ($AlertEmail) and confirm the SNS subscription to receive alerts." -ForegroundColor Yellow
}

Write-Host "Monitoring setup completed successfully! 🎉" -ForegroundColor Green
