# Cleanup AWS ETL System
# This script removes all AWS resources created by the ETL system

param(
    [string]$AwsRegion = "us-east-1",
    [string]$ProjectName = "aws-eks-msk-starter",
    [bool]$SkipConfirmation = $false,
    [bool]$ForceDestroy = $false
)

$ErrorActionPreference = "Stop"

Write-Host "====== AWS ETL System Cleanup ======" -ForegroundColor Red
Write-Host ""
Write-Host "⚠️  WARNING: This will DELETE all resources and data!" -ForegroundColor Yellow
Write-Host ""

# Confirm cleanup
if (-not $SkipConfirmation) {
    Write-Host "Resources that will be deleted:" -ForegroundColor Cyan
    Write-Host "  • EKS Cluster and all applications" -ForegroundColor White
    Write-Host "  • MSK Serverless cluster" -ForegroundColor White
    Write-Host "  • S3 buckets and all data" -ForegroundColor White
    Write-Host "  • DynamoDB tables and all data" -ForegroundColor White
    Write-Host "  • CloudWatch logs, metrics, and alarms" -ForegroundColor White
    Write-Host "  • ECR repositories and Docker images" -ForegroundColor White
    Write-Host "  • IAM roles and policies" -ForegroundColor White
    Write-Host ""
    
    $confirm = Read-Host "Are you ABSOLUTELY sure you want to proceed? Type 'DELETE' to confirm"
    if ($confirm -ne 'DELETE') {
        Write-Host "Cleanup cancelled." -ForegroundColor Yellow
        exit 0
    }
}

Write-Host ""
Write-Host "Step 1: Removing Kubernetes applications..." -ForegroundColor Yellow

try {
    # Check if kubectl is configured
    $eksCluster = kubectl config current-context 2>$null
    if ($eksCluster) {
        Write-Host "  Current cluster: $eksCluster" -ForegroundColor Cyan
        
        # Delete applications
        Write-Host "  Deleting ETL applications..." -ForegroundColor Cyan
        kubectl delete namespace etl --ignore-not-found=true
        
        # Delete CloudWatch logging
        Write-Host "  Deleting CloudWatch logging components..." -ForegroundColor Cyan
        kubectl delete namespace amazon-cloudwatch --ignore-not-found=true
        
        Write-Host "✓ Kubernetes applications removed!" -ForegroundColor Green
    } else {
        Write-Host "  No EKS cluster configured in kubectl" -ForegroundColor Gray
    }
} catch {
    Write-Warning "Failed to clean up Kubernetes resources: $_"
}

Write-Host ""
Write-Host "Step 2: Emptying S3 buckets..." -ForegroundColor Yellow

try {
    Push-Location terraform
    
    # Get bucket names from Terraform state
    $buckets = @()
    try {
        $inputBucket = terraform output -raw s3_input_bucket 2>$null
        $rejectedBucket = terraform output -raw s3_rejected_bucket 2>$null
        $reportsBucket = terraform output -raw s3_reports_bucket 2>$null
        
        if ($inputBucket) { $buckets += $inputBucket }
        if ($rejectedBucket) { $buckets += $rejectedBucket }
        if ($reportsBucket) { $buckets += $reportsBucket }
    } catch {
        Write-Host "  Could not get bucket names from Terraform, trying naming convention..." -ForegroundColor Gray
        $accountId = (aws sts get-caller-identity --query Account --output text)
        $buckets = @(
            "$ProjectName-input-$accountId",
            "$ProjectName-rejected-$accountId", 
            "$ProjectName-reports-$accountId"
        )
    }
    
    foreach ($bucket in $buckets) {
        if ($bucket) {
            Write-Host "  Emptying bucket: $bucket" -ForegroundColor Cyan
            try {
                aws s3 rm "s3://$bucket" --recursive 2>$null
                Write-Host "    ✓ Bucket $bucket emptied" -ForegroundColor Green
            } catch {
                Write-Host "    ⚠ Could not empty bucket $bucket (may not exist)" -ForegroundColor Gray
            }
        }
    }
    
} catch {
    Write-Warning "Failed to empty S3 buckets: $_"
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "Step 3: Removing ECR images..." -ForegroundColor Yellow

try {
    Push-Location terraform
    
    # Get ECR repository names
    $repos = @()
    try {
        $producerRepo = terraform output -raw producer_ecr_repository_url 2>$null
        $consumerRepo = terraform output -raw consumer_ecr_repository_url 2>$null
        $aggregatorRepo = terraform output -raw aggregator_ecr_repository_url 2>$null
        
        if ($producerRepo) { $repos += $producerRepo.Split('/')[1] }
        if ($consumerRepo) { $repos += $consumerRepo.Split('/')[1] }
        if ($aggregatorRepo) { $repos += $aggregatorRepo.Split('/')[1] }
    } catch {
        Write-Host "  Could not get ECR repository names from Terraform" -ForegroundColor Gray
        $repos = @("$ProjectName-producer", "$ProjectName-consumer", "$ProjectName-aggregator")
    }
    
    foreach ($repo in $repos) {
        if ($repo) {
            Write-Host "  Removing images from repository: $repo" -ForegroundColor Cyan
            try {
                $images = aws ecr list-images --repository-name $repo --query 'imageIds[*]' --output json 2>$null | ConvertFrom-Json
                if ($images -and $images.Count -gt 0) {
                    aws ecr batch-delete-image --repository-name $repo --image-ids $images --output table
                    Write-Host "    ✓ Images removed from $repo" -ForegroundColor Green
                } else {
                    Write-Host "    No images found in $repo" -ForegroundColor Gray
                }
            } catch {
                Write-Host "    ⚠ Could not remove images from $repo (may not exist)" -ForegroundColor Gray
            }
        }
    }
    
} catch {
    Write-Warning "Failed to remove ECR images: $_"
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "Step 4: Destroying infrastructure with Terraform..." -ForegroundColor Yellow

try {
    Push-Location terraform
    
    if ($ForceDestroy) {
        Write-Host "  Force destroying infrastructure..." -ForegroundColor Cyan
        terraform destroy -auto-approve
    } else {
        Write-Host "  Planning destruction..." -ForegroundColor Cyan
        terraform plan -destroy -out=destroy.tfplan
        
        Write-Host "  Destroying infrastructure..." -ForegroundColor Cyan
        terraform apply -auto-approve destroy.tfplan
        
        Remove-Item destroy.tfplan -ErrorAction SilentlyContinue
    }
    
    Write-Host "✓ Infrastructure destroyed successfully!" -ForegroundColor Green
    
} catch {
    Write-Error "Failed to destroy infrastructure: $_"
    Write-Host ""
    Write-Host "You may need to manually clean up some resources:" -ForegroundColor Yellow
    Write-Host "  1. Check EKS cluster in AWS Console" -ForegroundColor White
    Write-Host "  2. Check S3 buckets" -ForegroundColor White
    Write-Host "  3. Check DynamoDB tables" -ForegroundColor White
    Write-Host "  4. Check CloudWatch logs and alarms" -ForegroundColor White
    exit 1
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "Step 5: Cleaning up local files..." -ForegroundColor Yellow

try {
    # Remove generated files
    $filesToRemove = @(
        "k8s\sa-producer-updated.yaml",
        "k8s\sa-consumer-updated.yaml", 
        "k8s\sa-aggregator-updated.yaml",
        "terraform\terraform.tfstate.backup",
        "terraform\.terraform.lock.hcl"
    )
    
    foreach ($file in $filesToRemove) {
        if (Test-Path $file) {
            Remove-Item $file -Force
            Write-Host "  Removed: $file" -ForegroundColor Cyan
        }
    }
    
    # Remove Terraform state directory
    if (Test-Path "terraform\.terraform") {
        Remove-Item "terraform\.terraform" -Recurse -Force
        Write-Host "  Removed: terraform\.terraform directory" -ForegroundColor Cyan
    }
    
    Write-Host "✓ Local files cleaned!" -ForegroundColor Green
    
} catch {
    Write-Warning "Failed to clean some local files: $_"
}

Write-Host ""
Write-Host "====== CLEANUP COMPLETED! ======" -ForegroundColor Green
Write-Host ""
Write-Host "✅ All AWS resources have been removed!" -ForegroundColor Green
Write-Host ""
Write-Host "Final verification steps:" -ForegroundColor Cyan
Write-Host "  1. Check AWS Console for any remaining resources" -ForegroundColor White
Write-Host "  2. Verify your AWS bill shows no ongoing charges" -ForegroundColor White
Write-Host "  3. kubectl config should no longer show the EKS cluster" -ForegroundColor White
Write-Host ""
Write-Host "Thank you for using the ETL system! 🙏" -ForegroundColor Green
