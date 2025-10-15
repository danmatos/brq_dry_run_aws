# 🚀 Script de Preparação: Demonstração AWS Architecture

param(
    [Parameter(Mandatory=$true)]
    [string]$Environment = "demo",
    
    [Parameter(Mandatory=$false)]
    [string]$ProjectName = "etl-demo",
    
    [Parameter(Mandatory=$false)]
    [string]$AwsRegion = "us-east-1",
    
    [Parameter(Mandatory=$false)]
    [switch]$SkipInfrastructure,
    
    [Parameter(Mandatory=$false)]
    [switch]$SkipApplications,
    
    [Parameter(Mandatory=$false)]
    [switch]$DestroyAfterDemo,
    
    [Parameter(Mandatory=$false)]
    [int]$DemoDataSize = 1000
)

# Configurações
$ErrorActionPreference = "Stop"
$ProgressPreference = "Continue"

# Cores para output
$InfoColor = "Cyan"
$SuccessColor = "Green" 
$WarningColor = "Yellow"
$ErrorColor = "Red"

function Write-Info($Message) {
    Write-Host "ℹ️  $Message" -ForegroundColor $InfoColor
}

function Write-Success($Message) {
    Write-Host "✅ $Message" -ForegroundColor $SuccessColor
}

function Write-Warning($Message) {
    Write-Host "⚠️  $Message" -ForegroundColor $WarningColor
}

function Write-Error($Message) {
    Write-Host "❌ $Message" -ForegroundColor $ErrorColor
}

function Write-Banner($Title) {
    Write-Host ""
    Write-Host "=" * 60 -ForegroundColor Magenta
    Write-Host "  $Title" -ForegroundColor Magenta
    Write-Host "=" * 60 -ForegroundColor Magenta
    Write-Host ""
}

# Verificar pré-requisitos
function Test-Prerequisites {
    Write-Banner "VERIFICANDO PRÉ-REQUISITOS"
    
    # AWS CLI
    try {
        $awsVersion = aws --version 2>&1
        Write-Success "AWS CLI: $($awsVersion -split ' ' | Select-Object -First 1)"
    } catch {
        Write-Error "AWS CLI não encontrado. Instale: https://aws.amazon.com/cli/"
        exit 1
    }
    
    # kubectl
    try {
        $kubectlVersion = kubectl version --client --short 2>$null
        Write-Success "kubectl: $kubectlVersion"
    } catch {
        Write-Error "kubectl não encontrado. Instale: https://kubernetes.io/docs/tasks/tools/"
        exit 1
    }
    
    # Terraform
    try {
        $terraformVersion = terraform version
        Write-Success "Terraform: $($terraformVersion -split "`n" | Select-Object -First 1)"
    } catch {
        Write-Error "Terraform não encontrado. Instale: https://terraform.io/downloads"
        exit 1
    }
    
    # Docker
    try {
        $dockerVersion = docker --version
        Write-Success "Docker: $dockerVersion"
    } catch {
        Write-Error "Docker não encontrado. Instale: https://docker.com/get-started"
        exit 1
    }
    
    # AWS Credentials
    try {
        $awsIdentity = aws sts get-caller-identity --query 'Account' --output text 2>$null
        Write-Success "AWS Account: $awsIdentity"
        
        $awsRegionConfigured = aws configure get region
        if ($awsRegionConfigured -ne $AwsRegion) {
            Write-Warning "AWS Region configurada: $awsRegionConfigured (usando $AwsRegion)"
        }
    } catch {
        Write-Error "AWS credentials não configuradas. Execute: aws configure"
        exit 1
    }
    
    Write-Success "Todos os pré-requisitos verificados! ✨"
}

# Deploy da infraestrutura
function Deploy-Infrastructure {
    Write-Banner "DEPLOYING INFRASTRUCTURE"
    
    if ($SkipInfrastructure) {
        Write-Warning "Pulando deploy da infraestrutura (--SkipInfrastructure)"
        return
    }
    
    Write-Info "Navigating to terraform directory..."
    Push-Location "terraform"
    
    try {
        Write-Info "Initializing Terraform..."
        terraform init
        
        Write-Info "Planning infrastructure..."
        terraform plan `
            -var="environment=$Environment" `
            -var="project_name=$ProjectName" `
            -var="aws_region=$AwsRegion" `
            -out="tfplan"
        
        Write-Info "Applying infrastructure..."
        terraform apply "tfplan"
        
        # Capturar outputs importantes
        Write-Info "Capturando outputs da infraestrutura..."
        
        $eksClusterName = terraform output -raw eks_cluster_name
        $mskBootstrapServers = terraform output -raw msk_bootstrap_servers
        $inputBucket = terraform output -raw s3_input_bucket
        $outputBucket = terraform output -raw s3_output_bucket
        
        # Salvar outputs para uso posterior
        @{
            eks_cluster_name = $eksClusterName
            msk_bootstrap_servers = $mskBootstrapServers
            s3_input_bucket = $inputBucket
            s3_output_bucket = $outputBucket
        } | ConvertTo-Json | Out-File "../demo-outputs.json"
        
        Write-Success "Infraestrutura deployed com sucesso!"
        Write-Info "EKS Cluster: $eksClusterName"
        Write-Info "MSK Bootstrap: $mskBootstrapServers"
        Write-Info "S3 Input Bucket: $inputBucket"
        Write-Info "S3 Output Bucket: $outputBucket"
        
    } catch {
        Write-Error "Erro no deploy da infraestrutura: $_"
        exit 1
    } finally {
        Pop-Location
    }
}

# Configure kubectl
function Configure-Kubectl {
    Write-Banner "CONFIGURANDO KUBECTL"
    
    if (-not (Test-Path "demo-outputs.json")) {
        Write-Error "Arquivo demo-outputs.json não encontrado. Execute o deploy da infraestrutura primeiro."
        exit 1
    }
    
    $outputs = Get-Content "demo-outputs.json" | ConvertFrom-Json
    $eksClusterName = $outputs.eks_cluster_name
    
    Write-Info "Configurando kubectl para cluster: $eksClusterName"
    aws eks update-kubeconfig --region $AwsRegion --name $eksClusterName
    
    Write-Info "Testando conectividade com cluster..."
    kubectl get nodes
    
    Write-Success "kubectl configurado com sucesso!"
}

# Build e deploy das aplicações
function Deploy-Applications {
    Write-Banner "BUILDING & DEPLOYING APPLICATIONS"
    
    if ($SkipApplications) {
        Write-Warning "Pulando deploy das aplicações (--SkipApplications)"
        return
    }
    
    # Build applications
    Write-Info "Building aplicações..."
    Push-Location "apps"
    
    try {
        .\gradlew build -x test
        Write-Success "Build concluído!"
    } catch {
        Write-Error "Erro no build das aplicações: $_"
        exit 1
    } finally {
        Pop-Location
    }
    
    # Deploy Kubernetes manifests
    Write-Info "Deploying aplicações no Kubernetes..."
    kubectl apply -f k8s/
    
    Write-Info "Aguardando pods ficarem ready..."
    kubectl wait --for=condition=ready pod -l app=etl-producer -n etl --timeout=300s
    kubectl wait --for=condition=ready pod -l app=etl-consumer -n etl --timeout=300s
    kubectl wait --for=condition=ready pod -l app=etl-aggregator -n etl --timeout=300s
    
    # Verificar status
    Write-Info "Status dos deployments:"
    kubectl get pods -n etl -o wide
    kubectl get services -n etl
    
    Write-Success "Aplicações deployed com sucesso!"
}

# Preparar dados de demonstração
function Prepare-DemoData {
    Write-Banner "PREPARANDO DADOS DE DEMONSTRAÇÃO"
    
    Write-Info "Criando datasets de demonstração..."
    
    if (-not (Test-Path "sample-data")) {
        New-Item -ItemType Directory -Path "sample-data"
    }
    
    Push-Location "sample-data"
    
    try {
        # Dataset pequeno para demo rápida
        Write-Info "Gerando dataset pequeno ($DemoDataSize transações)..."
        .\generate-sample-data.ps1 -TransactionCount $DemoDataSize -OutputFile "demo-small.csv"
        
        # Dataset médio para performance
        Write-Info "Gerando dataset médio (50K transações)..."
        .\generate-sample-data.ps1 -TransactionCount 50000 -OutputFile "demo-medium.csv"
        
        # Dataset com erros para validação
        Write-Info "Gerando dataset com erros (500 transações, 20% erro)..."
        .\generate-sample-data.ps1 -TransactionCount 500 -ErrorRate 0.2 -OutputFile "demo-errors.csv"
        
        # Criar script de upload automático
        @"
# Script para upload automático durante demo
param([string]`$DatasetType = "small")

`$outputs = Get-Content "../demo-outputs.json" | ConvertFrom-Json
`$inputBucket = `$outputs.s3_input_bucket

switch (`$DatasetType) {
    "small" { 
        Write-Host "Uploading demo-small.csv..."
        aws s3 cp demo-small.csv s3://`${inputBucket}/pending/demo-small-`$(Get-Date -Format "yyyyMMdd-HHmmss").csv
    }
    "medium" {
        Write-Host "Uploading demo-medium.csv..."  
        aws s3 cp demo-medium.csv s3://`${inputBucket}/pending/demo-medium-`$(Get-Date -Format "yyyyMMdd-HHmmss").csv
    }
    "errors" {
        Write-Host "Uploading demo-errors.csv..."
        aws s3 cp demo-errors.csv s3://`${inputBucket}/pending/demo-errors-`$(Get-Date -Format "yyyyMMdd-HHmmss").csv  
    }
}

Write-Host "Upload concluído! Acompanhe os logs:"
Write-Host "kubectl logs -f deployment/etl-producer -n etl"
"@ | Out-File "upload-demo-data.ps1"
        
        Write-Success "Dados de demonstração preparados!"
        Write-Info "Use .\upload-demo-data.ps1 durante a demo"
        
    } catch {
        Write-Error "Erro preparando dados de demo: $_"
        exit 1
    } finally {
        Pop-Location
    }
}

# Setup monitoring
function Setup-Monitoring {
    Write-Banner "CONFIGURANDO MONITORING"
    
    Write-Info "Instalando Prometheus e Grafana..."
    
    # Adicionar Helm repo
    helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
    helm repo add grafana https://grafana.github.io/helm-charts
    helm repo update
    
    # Install Prometheus
    Write-Info "Installing Prometheus..."
    helm upgrade --install prometheus prometheus-community/kube-prometheus-stack `
        --namespace monitoring --create-namespace `
        --set grafana.adminPassword=admin123 `
        --wait
    
    # Configure port-forward para Grafana
    Write-Info "Configurando acesso ao Grafana..."
    Start-Process -FilePath "kubectl" -ArgumentList "port-forward -n monitoring svc/prometheus-grafana 3000:80" -WindowStyle Hidden
    
    Write-Success "Monitoring configurado!"
    Write-Info "Grafana disponível em: http://localhost:3000 (admin/admin123)"
}

# Criar script de demonstração
function Create-DemoScript {
    Write-Banner "CRIANDO SCRIPT DE DEMONSTRAÇÃO"
    
    @"
# 🎯 Demo Script - AWS ETL Architecture
# Execute este script durante a apresentação

# ============================================
# 1. VERIFICAR STATUS DA INFRAESTRUTURA  
# ============================================

Write-Host "🏗️ INFRAESTRUTURA AWS" -ForegroundColor Cyan
aws eks describe-cluster --name $ProjectName-cluster --query 'cluster.status'
aws kafka list-clusters --query 'ClusterInfoList[0].State'
aws s3 ls | findstr $ProjectName

# ============================================
# 2. VERIFICAR APLICAÇÕES KUBERNETES
# ============================================

Write-Host "`n🚀 APLICAÇÕES KUBERNETES" -ForegroundColor Cyan
kubectl get pods -n etl -o wide
kubectl get services -n etl

# ============================================
# 3. DEMO PIPELINE END-TO-END
# ============================================

Write-Host "`n📊 INICIANDO DEMO PIPELINE" -ForegroundColor Yellow
Write-Host "Fazendo upload de 1.000 transações..." -ForegroundColor Green

# Upload dados (execute em terminal separado)
cd sample-data
.\upload-demo-data.ps1 -DatasetType "small"

Write-Host "`nAcompanhe os logs em terminais separados:" -ForegroundColor Yellow
Write-Host "Terminal 1: kubectl logs -f deployment/etl-producer -n etl"
Write-Host "Terminal 2: kubectl logs -f deployment/etl-consumer -n etl"  
Write-Host "Terminal 3: kubectl logs -f deployment/etl-aggregator -n etl"

# ============================================
# 4. VERIFICAR RESULTADOS
# ============================================

Write-Host "`n📈 VERIFICANDO RESULTADOS" -ForegroundColor Cyan

# DynamoDB
Write-Host "Verificando dados no DynamoDB..."
aws dynamodb scan --table-name $ProjectName-transactions --max-items 5

# S3 Reports
Write-Host "Verificando relatórios gerados..."
aws s3 ls s3://$ProjectName-reports-bucket/reports/ --recursive

# ============================================
# 5. DEMO RESILIÊNCIA  
# ============================================

Write-Host "`n🛡️ DEMO RESILIÊNCIA" -ForegroundColor Red
Write-Host "Simulando falha do Consumer..."
kubectl delete pod -l app=etl-consumer -n etl

Write-Host "Acompanhando recovery automático..."
kubectl get pods -n etl -w

# ============================================
# 6. DEMO COM DADOS INVÁLIDOS
# ============================================

Write-Host "`n❌ DEMO VALIDAÇÃO DE DADOS" -ForegroundColor Yellow  
cd sample-data
.\upload-demo-data.ps1 -DatasetType "errors"

Write-Host "Verificando rejeição de dados inválidos..."
aws s3 ls s3://$ProjectName-input-bucket/rejected/

# ============================================
# 7. MONITORAMENTO
# ============================================

Write-Host "`n📊 MONITORAMENTO" -ForegroundColor Magenta
Write-Host "Grafana: http://localhost:3000 (admin/admin123)"
Write-Host "Dashboards disponíveis:"
Write-Host "- ETL Pipeline Overview"
Write-Host "- Infrastructure Health" 
Write-Host "- Business Metrics"

Write-Host "`n✨ DEMO CONCLUÍDA! ✨" -ForegroundColor Green
"@ | Out-File "demo-script.ps1"
    
    Write-Success "Script de demonstração criado: demo-script.ps1"
}

# Função principal
function Main {
    Write-Banner "🚀 PREPARAÇÃO DEMONSTRAÇÃO AWS ARCHITECT"
    
    Write-Info "Configurações:"
    Write-Info "  Environment: $Environment"
    Write-Info "  Project Name: $ProjectName"
    Write-Info "  AWS Region: $AwsRegion"
    Write-Info "  Demo Data Size: $DemoDataSize"
    
    if ($SkipInfrastructure) { Write-Warning "  Pulando infraestrutura" }
    if ($SkipApplications) { Write-Warning "  Pulando aplicações" }
    
    Write-Host ""
    Read-Host "Pressione ENTER para continuar ou CTRL+C para cancelar"
    
    # Executar passos
    Test-Prerequisites
    Deploy-Infrastructure
    Configure-Kubectl  
    Deploy-Applications
    Prepare-DemoData
    Setup-Monitoring
    Create-DemoScript
    
    Write-Banner "🎉 PREPARAÇÃO CONCLUÍDA COM SUCESSO!"
    
    Write-Success "Tudo pronto para a demonstração! 🚀"
    Write-Info ""
    Write-Info "PRÓXIMOS PASSOS:"
    Write-Info "1. Abra Grafana: http://localhost:3000 (admin/admin123)"
    Write-Info "2. Execute: .\demo-script.ps1 durante a apresentação"
    Write-Info "3. Tenha os seguintes terminais abertos:"
    Write-Info "   - AWS Console (CloudFormation, EKS, MSK, S3)"
    Write-Info "   - Grafana dashboards"
    Write-Info "   - 3 terminais para logs (producer, consumer, aggregator)"
    Write-Info ""
    
    if ($DestroyAfterDemo) {
        Write-Warning "⚠️  LEMBRE-SE: --DestroyAfterDemo está ativo!"
        Write-Warning "Execute 'terraform destroy' após a demo para evitar custos."
    }
    
    Write-Info "Duração estimada da demo: 45-60 minutos"
    Write-Info "Boa sorte com a apresentação! 🍀"
}

# Tratamento de erros global
trap {
    Write-Error "Erro inesperado: $_"
    Write-Warning "Verifique os logs acima e tente novamente."
    exit 1
}

# Executar
Main
