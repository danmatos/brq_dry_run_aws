# Test ETL System
# This script tests the complete ETL pipeline

param(
    [string]$AwsRegion = "us-east-1",
    [string]$ProjectName = "aws-eks-msk-starter"
)

Write-Host "===== ETL System Test Script =====" -ForegroundColor Green

# Get Terraform outputs
Write-Host "Getting AWS resources information..." -ForegroundColor Yellow
Push-Location terraform
try {
    $S3InputBucket = terraform output -raw s3_input_bucket
    $S3ReportsBucket = terraform output -raw s3_reports_bucket
    $EksClusterName = terraform output -raw eks_cluster_name
}
finally {
    Pop-Location
}

Write-Host "Input Bucket: $S3InputBucket" -ForegroundColor Cyan
Write-Host "Reports Bucket: $S3ReportsBucket" -ForegroundColor Cyan

# Step 1: Upload sample data to S3
Write-Host "Step 1: Uploading sample transaction data to S3..." -ForegroundColor Yellow
$sampleFile = "sample-data/transactions.jsonl"
$s3Key = "transactions/test-$(Get-Date -Format 'yyyyMMdd-HHmmss').jsonl"

aws s3 cp $sampleFile "s3://$S3InputBucket/$s3Key"

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Sample data uploaded successfully to s3://$S3InputBucket/$s3Key" -ForegroundColor Green
} else {
    Write-Error "Failed to upload sample data"
    exit 1
}

# Step 2: Check pod status
Write-Host "Step 2: Checking application pod status..." -ForegroundColor Yellow
kubectl get pods -n etl

# Step 3: Monitor logs
Write-Host "Step 3: Monitoring application logs for processing..." -ForegroundColor Yellow
Write-Host "You can monitor the logs with:" -ForegroundColor Cyan
Write-Host "kubectl logs -f deployment/etl-producer -n etl" -ForegroundColor White
Write-Host "kubectl logs -f deployment/etl-consumer -n etl" -ForegroundColor White
Write-Host "kubectl logs -f deployment/etl-aggregator -n etl" -ForegroundColor White

# Wait a bit for processing
Write-Host "Waiting 60 seconds for initial processing..." -ForegroundColor Yellow
Start-Sleep -Seconds 60

# Step 4: Check if processing started
Write-Host "Step 4: Checking recent producer logs..." -ForegroundColor Yellow
kubectl logs --tail=20 deployment/etl-producer -n etl

Write-Host ""
Write-Host "Step 5: Checking recent consumer logs..." -ForegroundColor Yellow
kubectl logs --tail=20 deployment/etl-consumer -n etl

# Step 6: Check application health
Write-Host "Step 6: Checking applications health..." -ForegroundColor Yellow
Write-Host "Producer pods:" -ForegroundColor Cyan
kubectl get pods -l app=etl-producer -n etl -o wide

Write-Host "Consumer pods:" -ForegroundColor Cyan
kubectl get pods -l app=etl-consumer -n etl -o wide

Write-Host "Aggregator pods:" -ForegroundColor Cyan
kubectl get pods -l app=etl-aggregator -n etl -o wide

# Step 7: Check DynamoDB for processed transactions
Write-Host "Step 7: Checking DynamoDB for processed transactions..." -ForegroundColor Yellow
$transactionsTable = "$ProjectName-transactions"
$processedFilesTable = "$ProjectName-processed-files"

Write-Host "Scanning transactions table (showing first 5 items)..." -ForegroundColor Cyan
try {
    $transactionCount = aws dynamodb scan --table-name $transactionsTable --select COUNT --output json | ConvertFrom-Json
    Write-Host "Total transactions in table: $($transactionCount.Count)" -ForegroundColor White
    
    if ($transactionCount.Count -gt 0) {
        aws dynamodb scan --table-name $transactionsTable --max-items 5 --output table
    }
} catch {
    Write-Warning "Could not scan transactions table: $_"
}

Write-Host "Scanning processed files table..." -ForegroundColor Cyan
try {
    aws dynamodb scan --table-name $processedFilesTable --output table
} catch {
    Write-Warning "Could not scan processed files table: $_"
}

# Step 8: Check CloudWatch metrics
Write-Host "Step 8: Checking CloudWatch metrics..." -ForegroundColor Yellow
try {
    Write-Host "ETL Producer metrics:" -ForegroundColor Cyan
    aws cloudwatch get-metric-statistics --namespace "ETL/Producer" --metric-name "etl.producer.files.processed" --start-time (Get-Date).AddHours(-1) --end-time (Get-Date) --period 300 --statistics Sum --output table 2>$null
    
    Write-Host "ETL Consumer metrics:" -ForegroundColor Cyan
    aws cloudwatch get-metric-statistics --namespace "ETL/Consumer" --metric-name "etl.consumer.transactions.processed" --start-time (Get-Date).AddHours(-1) --end-time (Get-Date) --period 300 --statistics Sum --output table 2>$null
} catch {
    Write-Host "Metrics may not be available yet (takes a few minutes)" -ForegroundColor Gray
}

# Step 9: Wait for report generation (reports are generated hourly)
Write-Host "Step 9: Note about reports..." -ForegroundColor Yellow
Write-Host "Reports are generated hourly at 5 minutes past the hour." -ForegroundColor White
Write-Host "To see reports, check S3 bucket: s3://$S3ReportsBucket/reports/" -ForegroundColor White
Write-Host "Or run: aws s3 ls s3://$S3ReportsBucket/reports/ --recursive" -ForegroundColor Cyan

Write-Host ""
Write-Host "===== Test Summary =====" -ForegroundColor Green
Write-Host "✓ Sample data uploaded to S3" -ForegroundColor Green
Write-Host "✓ Check application logs above to verify processing" -ForegroundColor Green
Write-Host "✓ Check DynamoDB tables for processed data" -ForegroundColor Green
Write-Host "• Reports will be generated at the next hour interval" -ForegroundColor Yellow

Write-Host ""
Write-Host "Useful commands for monitoring:" -ForegroundColor Cyan
Write-Host "kubectl get pods -n etl -w  # Watch pod status" -ForegroundColor White
Write-Host "kubectl top pods -n etl    # Check resource usage" -ForegroundColor White
Write-Host "aws s3 ls s3://$S3InputBucket/transactions/  # Check input files" -ForegroundColor White
Write-Host "aws s3 ls s3://$S3ReportsBucket/reports/ --recursive  # Check reports" -ForegroundColor White
