# Script de Limpeza e Preparacao para Versionamento
# Execute este script antes de fazer commit/push do projeto

param(
    [switch]$Deep,           # Limpeza profunda (remove caches do sistema)
    [switch]$KeepLogs,       # Manter arquivos de log
    [switch]$DryRun          # Apenas mostrar o que seria removido
)

Write-Host "Iniciando limpeza do projeto AWS EKS MSK ETL..." -ForegroundColor Green

$rootPath = $PSScriptRoot
if (-not $rootPath) {
    $rootPath = Get-Location
}

Write-Host "Diretorio raiz: $rootPath" -ForegroundColor Cyan

# Lista de diretorios e arquivos para limpeza
$cleanupTargets = @(
    # Build artifacts
    "apps\build",
    "apps\producer\build", 
    "apps\consumer\build",
    "apps\aggregator\build",
    
    # Gradle cache
    "apps\.gradle",
    
    # IDE files
    ".idea",
    "*.iml",
    ".vscode\settings.json",
    
    # OS files
    "Thumbs.db",
    ".DS_Store",
    "*.tmp",
    "*.temp",
    
    # Logs (se nao for manter)
    "*.log",
    "logs\*",
    
    # Terraform state backups (manter apenas o atual)
    "terraform\terraform.tfstate.backup.*",
    
    # Cache e temporarios
    "node_modules",
    ".terraform\.terraform",
    
    # Test outputs
    "test-output",
    "coverage"
)

# Arquivos/pastas sensiveis que NAO devem ser versionados
$sensitiveTargets = @(
    "terraform\terraform.tfvars",
    "terraform\*.tfplan",
    ".env",
    ".env.local",
    ".aws\credentials",
    "kubeconfig",
    "*.pem",
    "*.key"
)

function Remove-ItemSafely {
    param(
        [string]$Path,
        [string]$Description,
        [switch]$Force
    )
    
    $fullPath = Join-Path $rootPath $Path
    
    if (Test-Path $fullPath) {
        if ($DryRun) {
            Write-Host "  [DRY RUN] Removeria: $Description" -ForegroundColor Yellow
            Write-Host "            Path: $fullPath" -ForegroundColor Gray
        } else {
            try {
                if ($Force) {
                    Remove-Item $fullPath -Recurse -Force
                } else {
                    Remove-Item $fullPath -Recurse
                }
                Write-Host "  Removido: $Description" -ForegroundColor Green
            } catch {
                Write-Host "  Erro ao remover: $Description" -ForegroundColor Red
                Write-Host "     Erro: $($_.Exception.Message)" -ForegroundColor Gray
            }
        }
    }
}

function Get-DirectorySize {
    param([string]$Path)
    
    if (-not (Test-Path $Path)) { return 0 }
    
    try {
        $size = (Get-ChildItem $Path -Recurse -File -ErrorAction SilentlyContinue | 
                Measure-Object -Property Length -Sum).Sum
        return [math]::Round($size / 1MB, 2)
    } catch {
        return 0
    }
}

# Calcular tamanho inicial
$initialSize = Get-DirectorySize $rootPath
Write-Host "Tamanho inicial do projeto: $initialSize MB" -ForegroundColor Cyan

Write-Host "`nLimpando arquivos de build e cache..." -ForegroundColor Yellow

# Limpeza de build artifacts
foreach ($target in $cleanupTargets) {
    $targetPath = Join-Path $rootPath $target
    
    if ($target -like "*.log" -and $KeepLogs) {
        continue
    }
    
    if (Test-Path $targetPath) {
        $sizeStr = "$(Get-DirectorySize $targetPath) MB"
        Remove-ItemSafely -Path $target -Description "$target ($sizeStr)" -Force
    }
}

Write-Host "`nVerificando arquivos sensiveis..." -ForegroundColor Yellow

# Verificar arquivos sensiveis
$foundSensitive = @()
foreach ($sensitive in $sensitiveTargets) {
    $sensitivePath = Join-Path $rootPath $sensitive
    if (Test-Path $sensitivePath) {
        $foundSensitive += $sensitive
    }
}

if ($foundSensitive.Count -gt 0) {
    Write-Host "  ATENCAO: Arquivos sensiveis encontrados!" -ForegroundColor Red
    foreach ($file in $foundSensitive) {
        Write-Host "     - $file" -ForegroundColor Red
    }
    Write-Host "  Estes arquivos nao devem ser versionados!" -ForegroundColor Yellow
    
    if (-not $DryRun) {
        $response = Read-Host "Deseja remover os arquivos sensiveis? (y/N)"
        if ($response -eq 'y' -or $response -eq 'Y') {
            foreach ($file in $foundSensitive) {
                Remove-ItemSafely -Path $file -Description "Arquivo sensivel: $file" -Force
            }
        }
    }
} else {
    Write-Host "  Nenhum arquivo sensivel encontrado" -ForegroundColor Green
}

# Limpeza profunda (caches do sistema)
if ($Deep) {
    Write-Host "`nExecutando limpeza profunda..." -ForegroundColor Yellow
    
    $deepTargets = @(
        "$env:USERPROFILE\.gradle\caches",
        "$env:USERPROFILE\.m2\repository",
        "$env:TEMP\kotlin-daemon*"
    )
    
    foreach ($target in $deepTargets) {
        if (Test-Path $target) {
            $sizeStr = "$(Get-DirectorySize $target) MB"
            Write-Host "  Limpando cache: $target ($sizeStr)" -ForegroundColor Cyan
            if (-not $DryRun) {
                try {
                    Remove-Item $target -Recurse -Force -ErrorAction SilentlyContinue
                    Write-Host "  Cache limpo" -ForegroundColor Green
                } catch {
                    Write-Host "  Erro na limpeza: $($_.Exception.Message)" -ForegroundColor Yellow
                }
            }
        }
    }
}

# Calcular tamanho final
$finalSize = Get-DirectorySize $rootPath
$savedSpace = $initialSize - $finalSize

Write-Host "`nResumo da limpeza:" -ForegroundColor Cyan
Write-Host "  Tamanho inicial: $initialSize MB" -ForegroundColor Gray
Write-Host "  Tamanho final: $finalSize MB" -ForegroundColor Gray
Write-Host "  Espaco liberado: $savedSpace MB" -ForegroundColor Green

if ($DryRun) {
    Write-Host "`nModo DRY RUN ativo - nenhum arquivo foi removido" -ForegroundColor Yellow
    Write-Host "Execute sem -DryRun para aplicar as mudancas" -ForegroundColor Gray
}

Write-Host "`nProjeto limpo e pronto para versionamento!" -ForegroundColor Green
Write-Host "Proximos passos:" -ForegroundColor Cyan
Write-Host "1. git add ." -ForegroundColor Gray  
Write-Host "2. git commit -m 'chore: projeto estruturado e limpo'" -ForegroundColor Gray
Write-Host "3. git push" -ForegroundColor Gray
