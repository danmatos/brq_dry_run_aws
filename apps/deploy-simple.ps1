# Script simplificado de deploy das aplicações ETL

$ErrorActionPreference = "Stop"

Write-Host "====== Deploy das Aplicações ETL ======" -ForegroundColor Green
Write-Host ""

# Obter informações do Terraform
Write-Host "Step 1: Obtendo informações da infraestrutura..." -ForegroundColor Yellow
Push-Location ..\terraform

$producerEcrRepo = terraform output -raw producer_ecr_repository_url
$consumerEcrRepo = terraform output -raw consumer_ecr_repository_url
$eksClusterName = terraform output -raw eks_cluster_name

Write-Host "ECR Producer: $producerEcrRepo" -ForegroundColor Green
Write-Host "ECR Consumer: $consumerEcrRepo" -ForegroundColor Green
Write-Host "EKS Cluster: $eksClusterName" -ForegroundColor Green

Pop-Location

# Login no ECR
Write-Host ""
Write-Host "Step 2: Fazendo login no ECR..." -ForegroundColor Yellow

$ecrRegistry = $producerEcrRepo.Split('/')[0]
aws ecr get-login-password --region sa-east-1 | docker login --username AWS --password-stdin $ecrRegistry

Write-Host "Login no ECR realizado!" -ForegroundColor Green

# Build e push do Producer
Write-Host ""
Write-Host "Step 3: Building Producer..." -ForegroundColor Yellow
Push-Location producer
docker build -t "${producerEcrRepo}:latest" .
docker push "${producerEcrRepo}:latest"
Write-Host "Producer deployed!" -ForegroundColor Green
Pop-Location

# Build e push do Consumer
Write-Host ""
Write-Host "Step 4: Building Consumer..." -ForegroundColor Yellow
Push-Location consumer
docker build -t "${consumerEcrRepo}:latest" .
docker push "${consumerEcrRepo}:latest"
Write-Host "Consumer deployed!" -ForegroundColor Green
Pop-Location

Write-Host ""
Write-Host "====== DEPLOY COMPLETADO! ======" -ForegroundColor Green
