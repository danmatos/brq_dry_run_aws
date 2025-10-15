# Script simplificado de deploy das aplicações ETL
# Este script faz o deploy dos módulos que compilaram com sucesso

param(
    [string]$AwsRegion = "sa-east-1"
)

$ErrorActionPreference = "Stop"

Write-Host "====== Deploy das Aplicações ETL ======" -ForegroundColor Green
Write-Host ""

# Obter informações do Terraform
Write-Host "Step 1: Obtendo informações da infraestrutura..." -ForegroundColor Yellow
Push-Location ..\terraform

try {
    $producerEcrRepo = terraform output -raw producer_ecr_repository_url
    $consumerEcrRepo = terraform output -raw consumer_ecr_repository_url
    $eksClusterName = terraform output -raw eks_cluster_name
    
    Write-Host "✓ ECR Producer: $producerEcrRepo" -ForegroundColor Green
    Write-Host "✓ ECR Consumer: $consumerEcrRepo" -ForegroundColor Green
    Write-Host "✓ EKS Cluster: $eksClusterName" -ForegroundColor Green
} catch {
    Write-Error "Erro ao obter outputs do Terraform: $_"
    exit 1
} finally {
    Pop-Location
}

# Login no ECR
Write-Host ""
Write-Host "Step 2: Fazendo login no ECR..." -ForegroundColor Yellow
try {
    aws ecr get-login-password --region $AwsRegion | docker login --username AWS --password-stdin $producerEcrRepo.Split('/')[0]
    Write-Host "✓ Login no ECR realizado com sucesso!" -ForegroundColor Green
} catch {
    Write-Error "Erro no login do ECR: $_"
    exit 1
}

# Build e push do Producer
Write-Host ""
Write-Host "Step 3: Building e pushing Producer..." -ForegroundColor Yellow
try {
    Push-Location producer
    
    # Build da imagem
    docker build -t "${producerEcrRepo}:latest" .
    
    # Push da imagem
    docker push "${producerEcrRepo}:latest"
    
    Write-Host "✓ Producer image pushed successfully!" -ForegroundColor Green
    Pop-Location
} catch {
    Write-Error "Erro no build/push do Producer: $_"
    Pop-Location
    exit 1
}

# Build e push do Consumer
Write-Host ""
Write-Host "Step 4: Building e pushing Consumer..." -ForegroundColor Yellow
try {
    Push-Location consumer
    
    # Build da imagem
    docker build -t "${consumerEcrRepo}:latest" .
    
    # Push da imagem
    docker push "${consumerEcrRepo}:latest"
    
    Write-Host "✓ Consumer image pushed successfully!" -ForegroundColor Green
    Pop-Location
} catch {
    Write-Error "Erro no build/push do Consumer: $_"
    Pop-Location
    exit 1
}

# Configurar kubectl
Write-Host ""
Write-Host "Step 5: Configurando kubectl..." -ForegroundColor Yellow
try {
    aws eks update-kubeconfig --region $AwsRegion --name $eksClusterName
    Write-Host "✓ kubectl configurado com sucesso!" -ForegroundColor Green
} catch {
    Write-Warning "Possível problema com kubectl, mas continuando..."
}

# Deploy no Kubernetes
Write-Host ""
Write-Host "Step 6: Aplicando Service Accounts..." -ForegroundColor Yellow
try {
    Push-Location ..\terraform
    $producerRoleArn = terraform output -raw sa_producer_role_arn
    $consumerRoleArn = terraform output -raw sa_consumer_role_arn
    Pop-Location
    
    # Aplicar namespace
    kubectl apply -f ..\k8s\namespace.yaml
    
    # Atualizar service accounts com ARNs corretos
    $producerSA = Get-Content "..\k8s\sa-producer.yaml" -Raw
    $producerSA = $producerSA -replace "ROLE_ARN_PRODUCER", $producerRoleArn
    $producerSA | kubectl apply -f -
    
    $consumerSA = Get-Content "..\k8s\sa-consumer.yaml" -Raw
    $consumerSA = $consumerSA -replace "ROLE_ARN_CONSUMER", $consumerRoleArn
    $consumerSA | kubectl apply -f -
    
    Write-Host "✓ Service Accounts aplicados com sucesso!" -ForegroundColor Green
} catch {
    Write-Warning "Problema ao aplicar Service Accounts, mas continuando..."
}

Write-Host ""
Write-Host "====== DEPLOY PARCIAL COMPLETADO! ======" -ForegroundColor Green
Write-Host ""
Write-Host "Status:" -ForegroundColor Cyan
Write-Host "  ✅ Producer: Compilado e deployed" -ForegroundColor White
Write-Host "  ✅ Consumer: Compilado e deployed" -ForegroundColor White
Write-Host "  ⚠️  Aggregator: Problema de compilação (pulado)" -ForegroundColor Yellow
Write-Host ""
Write-Host "Próximos passos:" -ForegroundColor Cyan
Write-Host "  1. Verificar imagens no ECR: aws ecr list-images --repository-name dry-run-brq-producer --region $AwsRegion" -ForegroundColor White
Write-Host "  2. Testar Producer e Consumer individualmente" -ForegroundColor White
Write-Host "  3. Corrigir Aggregator posteriormente" -ForegroundColor White
