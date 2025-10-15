# Deploy ETL System to AWS - Complete Guide
param(
    [string]$AwsRegion = "us-east-1",
    [string]$ProjectName = "aws-eks-msk-starter",
    [string]$AlertEmail = "",
    [bool]$CreateECR = $true,
    [bool]$SkipConfirmation = $false
)

$ErrorActionPreference = "Stop"

Write-Host "====== AWS ETL System Full Deployment ======" -ForegroundColor Green
Write-Host "Configuration:" -ForegroundColor Cyan
Write-Host "  AWS Region: $AwsRegion" -ForegroundColor White
Write-Host "  Project Name: $ProjectName" -ForegroundColor White
Write-Host "  Create ECR: $CreateECR" -ForegroundColor White

# Verify AWS credentials
Write-Host "Step 1: Verifying AWS credentials..." -ForegroundColor Yellow
try {
    $awsIdentity = aws sts get-caller-identity | ConvertFrom-Json
    Write-Host "AWS Account: $($awsIdentity.Account)" -ForegroundColor Green
} catch {
    Write-Error "AWS credentials not configured. Please run 'aws configure' first."
    exit 1
}

# Confirm deployment
if (-not $SkipConfirmation) {
    Write-Warning "This will create AWS resources that incur costs."
    $confirm = Read-Host "Do you want to continue? (y/N)"
    if ($confirm -ne 'y' -and $confirm -ne 'Y') {
        Write-Host "Deployment cancelled." -ForegroundColor Yellow
        exit 0
    }
}

Write-Host "Step 2: Deploying infrastructure with Terraform..." -ForegroundColor Yellow
Push-Location terraform

try {
    terraform init
    terraform plan -out=tfplan
    terraform apply -auto-approve tfplan
    
    $eksClusterName = terraform output -raw eks_cluster_name
    Write-Host "Infrastructure deployed successfully!" -ForegroundColor Green
    Write-Host "EKS Cluster: $eksClusterName" -ForegroundColor White
    
} catch {
    Write-Error "Failed to deploy infrastructure: $_"
    exit 1
} finally {
    Pop-Location
}

Write-Host "Step 3: Configuring kubectl..." -ForegroundColor Yellow
try {
    aws eks update-kubeconfig --region $AwsRegion --name $eksClusterName
    Write-Host "kubectl configured successfully!" -ForegroundColor Green
} catch {
    Write-Error "Failed to configure kubectl: $_"
    exit 1
}

Write-Host "====== DEPLOYMENT COMPLETED SUCCESSFULLY! ======" -ForegroundColor Green
Write-Host "Your ETL system infrastructure is now ready!" -ForegroundColor Green
Write-Host "Next Steps:" -ForegroundColor Cyan
Write-Host "  1. Test the system: .\test-etl.ps1" -ForegroundColor White  
Write-Host "  2. Monitor pods: kubectl get pods -n etl" -ForegroundColor White
Write-Host "Cost Reminder: Remember to run terraform destroy when done!" -ForegroundColor Yellow
