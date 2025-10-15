# Script de Limpeza e Preparação para Versionamento
# Execute este script antes de fazer commit/push do projeto

param(
    [switch]$Deep,           # Limpeza profunda (remove caches do sistema)
    [switch]$KeepLogs,       # Manter arquivos de log
    [switch]$DryRun          # Apenas mostrar o que seria removido
)

Write-Host "🧹 Iniciando limpeza do projeto AWS EKS MSK ETL..." -ForegroundColor Green

$rootPath = $PSScriptRoot
if (-not $rootPath) {
    $rootPath = Get-Location
}

Write-Host "📂 Diretório raiz: $rootPath" -ForegroundColor Cyan

# Lista de diretórios e arquivos para limpeza
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
    
    # Logs (se não for manter)
    "*.log",
    "logs\*",
    
    # Terraform state backups (manter apenas o atual)
    "terraform\terraform.tfstate.backup.*",
    
    # Cache e temporários
    "node_modules",
    ".terraform\.terraform",
    
    # Test outputs
    "test-output",
    "coverage"
)

# Arquivos/pastas sensíveis que NÃO devem ser versionados
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
                Write-Host "  ✅ Removido: $Description" -ForegroundColor Green
            } catch {
                Write-Host "  ⚠️  Erro ao remover: $Description" -ForegroundColor Red
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
Write-Host "📊 Tamanho inicial do projeto: $initialSize MB" -ForegroundColor Cyan

Write-Host "`n🗑️  Limpando arquivos de build e cache..." -ForegroundColor Yellow

# Limpeza de build artifacts
foreach ($target in $cleanupTargets) {
    $targetPath = Join-Path $rootPath $target
    
    if ($target -like "*.log" -and $KeepLogs) {
        continue
    }
    
    if (Test-Path $targetPath) {
        $size = Get-DirectorySize $targetPath
        Remove-ItemSafely -Path $target -Description "$target ($size MB)" -Force
    }
}

Write-Host "`n🔒 Verificando arquivos sensíveis..." -ForegroundColor Yellow

# Verificar arquivos sensíveis
$foundSensitive = @()
foreach ($sensitive in $sensitiveTargets) {
    $sensitivePath = Join-Path $rootPath $sensitive
    if (Test-Path $sensitivePath) {
        $foundSensitive += $sensitive
    }
}

if ($foundSensitive.Count -gt 0) {
    Write-Host "  ⚠️  ATENÇÃO: Arquivos sensíveis encontrados!" -ForegroundColor Red
    foreach ($file in $foundSensitive) {
        Write-Host "     - $file" -ForegroundColor Red
    }
    Write-Host "  💡 Estes arquivos não devem ser versionados!" -ForegroundColor Yellow
    
    if (-not $DryRun) {
        $response = Read-Host "Deseja remover os arquivos sensíveis? (y/N)"
        if ($response -eq 'y' -or $response -eq 'Y') {
            foreach ($file in $foundSensitive) {
                Remove-ItemSafely -Path $file -Description "Arquivo sensível: $file" -Force
            }
        }
    }
} else {
    Write-Host "  ✅ Nenhum arquivo sensível encontrado" -ForegroundColor Green
}

# Limpeza profunda (caches do sistema)
if ($Deep) {
    Write-Host "`n🔧 Executando limpeza profunda..." -ForegroundColor Yellow
    
    $deepTargets = @(
        "$env:USERPROFILE\.gradle\caches",
        "$env:USERPROFILE\.m2\repository",
        "$env:TEMP\kotlin-daemon*"
    )
    
    foreach ($target in $deepTargets) {
        if (Test-Path $target) {
            $size = Get-DirectorySize $target
            Write-Host "  🧹 Limpando cache: $target ($size MB)" -ForegroundColor Cyan
            if (-not $DryRun) {
                try {
                    Remove-Item $target -Recurse -Force -ErrorAction SilentlyContinue
                    Write-Host "  ✅ Cache limpo" -ForegroundColor Green
                } catch {
                    Write-Host "  ⚠️  Erro na limpeza: $($_.Exception.Message)" -ForegroundColor Yellow
                }
            }
        }
    }
}

Write-Host "`n📝 Verificando estrutura do .gitignore..." -ForegroundColor Yellow

$gitignorePath = Join-Path $rootPath ".gitignore"
if (-not (Test-Path $gitignorePath)) {
    Write-Host "  ⚠️  Arquivo .gitignore não encontrado!" -ForegroundColor Red
} else {
    Write-Host "  ✅ Arquivo .gitignore presente" -ForegroundColor Green
    
    # Verificar se contém regras essenciais
    $gitignoreContent = Get-Content $gitignorePath -Raw
    $essentialRules = @("build/", "*.log", ".gradle/", "terraform.tfstate", ".env")
    $missingRules = @()
    
    foreach ($rule in $essentialRules) {
        if ($gitignoreContent -notlike "*$rule*") {
            $missingRules += $rule
        }
    }
    
    if ($missingRules.Count -gt 0) {
        Write-Host "  ⚠️  Regras .gitignore ausentes:" -ForegroundColor Yellow
        foreach ($rule in $missingRules) {
            Write-Host "     - $rule" -ForegroundColor Gray
        }
    }
}

Write-Host "`n🧪 Verificando testes..." -ForegroundColor Yellow

# Verificar se testes existem
$testPaths = @(
    "apps\producer\src\test\kotlin",
    "apps\consumer\src\test\kotlin", 
    "apps\aggregator\src\test\kotlin"
)

foreach ($testPath in $testPaths) {
    $fullTestPath = Join-Path $rootPath $testPath
    if (Test-Path $fullTestPath) {
        $testFiles = (Get-ChildItem $fullTestPath -Recurse -Filter "*.kt").Count
        Write-Host "  ✅ $testPath - $testFiles arquivos de teste" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️  $testPath - Sem testes!" -ForegroundColor Yellow
    }
}

Write-Host "`n📋 Verificando documentação..." -ForegroundColor Yellow

$docFiles = @(
    "README.md",
    "docs\ARCHITECTURE.md",
    "docs\DEPLOY_GUIDE.md",
    "docs\MONITORING_GUIDE.md"
)

foreach ($docFile in $docFiles) {
    $fullDocPath = Join-Path $rootPath $docFile
    if (Test-Path $fullDocPath) {
        Write-Host "  ✅ $docFile" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️  $docFile - Ausente!" -ForegroundColor Yellow
    }
}

# Calcular tamanho final
$finalSize = Get-DirectorySize $rootPath
$savedSpace = $initialSize - $finalSize

Write-Host "`n📊 Resumo da limpeza:" -ForegroundColor Cyan
Write-Host "  • Tamanho inicial: $initialSize MB" -ForegroundColor Gray
Write-Host "  • Tamanho final: $finalSize MB" -ForegroundColor Gray
Write-Host "  • Espaço liberado: $savedSpace MB" -ForegroundColor Green

if ($DryRun) {
    Write-Host "`n💡 Modo DRY RUN ativo - nenhum arquivo foi removido" -ForegroundColor Yellow
    Write-Host "   Execute sem -DryRun para aplicar as mudanças" -ForegroundColor Gray
}

Write-Host "`n✨ Projeto limpo e pronto para versionamento!" -ForegroundColor Green
Write-Host "   Próximos passos:" -ForegroundColor Cyan
Write-Host "   1. git add ." -ForegroundColor Gray  
Write-Host "   2. git commit -m 'chore: projeto estruturado e limpo'" -ForegroundColor Gray
Write-Host "   3. git push" -ForegroundColor Gray

# Sugestões de melhorias
Write-Host "`n💡 Sugestões para otimização:" -ForegroundColor Blue
Write-Host "   • Configure pre-commit hooks para limpeza automática" -ForegroundColor Gray
Write-Host "   • Configure CI/CD pipeline com verificações de qualidade" -ForegroundColor Gray
Write-Host "   • Considere usar Docker para builds consistentes" -ForegroundColor Gray
