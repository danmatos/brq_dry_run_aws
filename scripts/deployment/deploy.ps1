# Build and Deploy ETL Applications to EKS
# PowerShell version of the deployment script

param(
    [string]$AwsRegion = "us-east-1",
    [string]$ProjectName = "aws-eks-msk-starter"
)

Write-Host "===== ETL Applications Build and Deploy Script =====" -ForegroundColor Green

# Get Terraform outputs
Write-Host "Getting Terraform outputs..." -ForegroundColor Yellow
Push-Location terraform

try {
    $EksClusterName = terraform output -raw eks_cluster_name
    $KafkaBootstrapServers = terraform output -raw msk_bootstrap_brokers_sasl_iam
    $S3InputBucket = terraform output -raw s3_input_bucket
    $S3RejectedBucket = terraform output -raw s3_rejected_bucket
    $S3ReportsBucket = terraform output -raw s3_reports_bucket
    
    # Try to get ECR repository URLs (they might not exist if create_ecr is false)
    try {
        $ProducerEcrRepo = terraform output -raw producer_ecr_repository_url
        $ConsumerEcrRepo = terraform output -raw consumer_ecr_repository_url
        $AggregatorEcrRepo = terraform output -raw aggregator_ecr_repository_url
        $EcrAvailable = $true
    }
    catch {
        Write-Host "ECR repositories not found. Make sure create_ecr = true in terraform variables." -ForegroundColor Yellow
        $EcrAvailable = $false
    }
}
catch {
    Write-Error "Failed to get Terraform outputs. Make sure you're in the right directory and terraform has been applied."
    exit 1
}
finally {
    Pop-Location
}

# DynamoDB table names
$ProcessedFilesTable = "$ProjectName-processed-files"
$TransactionsTable = "$ProjectName-transactions"

Write-Host "Cluster: $EksClusterName" -ForegroundColor Cyan
Write-Host "Kafka Brokers: $KafkaBootstrapServers" -ForegroundColor Cyan

# Update kubeconfig
Write-Host "Updating kubeconfig..." -ForegroundColor Yellow
aws eks update-kubeconfig --region $AwsRegion --name $EksClusterName

if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to update kubeconfig"
    exit 1
}

# Build applications
Write-Host "Building applications..." -ForegroundColor Yellow
Push-Location apps

if ($IsWindows -or $env:OS -eq "Windows_NT") {
    .\gradlew.bat clean build
} else {
    ./gradlew clean build
}

if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to build applications"
    Pop-Location
    exit 1
}

# Build and push Docker images if ECR repositories exist
if ($EcrAvailable) {
    Write-Host "Building and pushing Docker images..." -ForegroundColor Yellow
    
    # Get ECR login token
    $ecrToken = aws ecr get-login-password --region $AwsRegion
    $ecrToken | docker login --username AWS --password-stdin $ProducerEcrRepo.Split('/')[0]
    
    # Producer
    Write-Host "Building Producer image..." -ForegroundColor Cyan
    Push-Location producer
    docker build -t "${ProducerEcrRepo}:latest" .
    docker push "${ProducerEcrRepo}:latest"
    Pop-Location
    
    # Consumer
    Write-Host "Building Consumer image..." -ForegroundColor Cyan
    Push-Location consumer
    docker build -t "${ConsumerEcrRepo}:latest" .
    docker push "${ConsumerEcrRepo}:latest"
    Pop-Location
    
    # Aggregator
    Write-Host "Building Aggregator image..." -ForegroundColor Cyan
    Push-Location aggregator
    docker build -t "${AggregatorEcrRepo}:latest" .
    docker push "${AggregatorEcrRepo}:latest"
    Pop-Location
    
    Write-Host "Docker images pushed successfully!" -ForegroundColor Green
} else {
    Write-Host "ECR repositories not found. Skipping Docker build and push." -ForegroundColor Yellow
    Write-Host "Make sure to set create_ecr = true in terraform variables and run terraform apply" -ForegroundColor Yellow
}

Pop-Location

# Deploy to Kubernetes
Write-Host "Deploying to Kubernetes..." -ForegroundColor Yellow

# Apply namespace and service accounts
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/sa-producer.yaml
kubectl apply -f k8s/sa-consumer.yaml
kubectl apply -f k8s/sa-aggregator.yaml

if ($EcrAvailable) {
    Write-Host "Updating deployment configurations..." -ForegroundColor Yellow
    
    # Read and update deployment files
    $producerDeployment = Get-Content k8s/producer-deployment.yaml -Raw
    $producerDeployment = $producerDeployment -replace "PRODUCER_ECR_IMAGE", "${ProducerEcrRepo}:latest"
    $producerDeployment = $producerDeployment -replace "KAFKA_BOOTSTRAP_SERVERS_PLACEHOLDER", $KafkaBootstrapServers
    $producerDeployment = $producerDeployment -replace "S3_INPUT_BUCKET_PLACEHOLDER", $S3InputBucket
    $producerDeployment = $producerDeployment -replace "S3_REJECTED_BUCKET_PLACEHOLDER", $S3RejectedBucket
    $producerDeployment = $producerDeployment -replace "DYNAMODB_PROCESSED_FILES_TABLE_PLACEHOLDER", $ProcessedFilesTable
    $producerDeployment | kubectl apply -f -
    
    $consumerDeployment = Get-Content k8s/consumer-deployment.yaml -Raw
    $consumerDeployment = $consumerDeployment -replace "CONSUMER_ECR_IMAGE", "${ConsumerEcrRepo}:latest"
    $consumerDeployment = $consumerDeployment -replace "KAFKA_BOOTSTRAP_SERVERS_PLACEHOLDER", $KafkaBootstrapServers
    $consumerDeployment = $consumerDeployment -replace "DYNAMODB_TRANSACTIONS_TABLE_PLACEHOLDER", $TransactionsTable
    $consumerDeployment | kubectl apply -f -
    
    $aggregatorDeployment = Get-Content k8s/aggregator-deployment.yaml -Raw
    $aggregatorDeployment = $aggregatorDeployment -replace "AGGREGATOR_ECR_IMAGE", "${AggregatorEcrRepo}:latest"
    $aggregatorDeployment = $aggregatorDeployment -replace "KAFKA_BOOTSTRAP_SERVERS_PLACEHOLDER", $KafkaBootstrapServers
    $aggregatorDeployment = $aggregatorDeployment -replace "S3_REPORTS_BUCKET_PLACEHOLDER", $S3ReportsBucket
    $aggregatorDeployment | kubectl apply -f -
    
    Write-Host "Deployment complete!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Setting up CloudWatch monitoring..." -ForegroundColor Yellow
    .\setup-monitoring.ps1 -AwsRegion $AwsRegion -ProjectName $ProjectName
    Write-Host ""
    Write-Host "Check deployment status:" -ForegroundColor Cyan
    Write-Host "kubectl get pods -n etl" -ForegroundColor White
    Write-Host ""
    Write-Host "View logs:" -ForegroundColor Cyan
    Write-Host "kubectl logs -f deployment/etl-producer -n etl" -ForegroundColor White
    Write-Host "kubectl logs -f deployment/etl-consumer -n etl" -ForegroundColor White
    Write-Host "kubectl logs -f deployment/etl-aggregator -n etl" -ForegroundColor White
} else {
    Write-Host "Skipping Kubernetes deployment - ECR images not available" -ForegroundColor Yellow
}
