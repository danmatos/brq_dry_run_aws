# Script de Verificação de Qualidade e Estrutura do Projeto
# Verifica se o projeto está seguindo as boas práticas

param(
    [switch]$RunTests,       # Executar testes unitários
    [switch]$CheckCoverage,  # Verificar cobertura de testes  
    [switch]$Detailed        # Relatório detalhado
)

Write-Host "🔍 Verificando qualidade e estrutura do projeto..." -ForegroundColor Green

$rootPath = $PSScriptRoot
if (-not $rootPath) {
    $rootPath = Get-Location
}

$results = @{
    Structure = @()
    CodeQuality = @()
    Tests = @()
    Documentation = @()
    Security = @()
    Performance = @()
}

$score = 0
$maxScore = 100

function Add-Result {
    param(
        [string]$Category,
        [string]$Item,
        [string]$Status,
        [int]$Points,
        [string]$Description = ""
    )
    
    $results[$Category] += @{
        Item = $Item
        Status = $Status
        Points = $Points
        Description = $Description
    }
    
    if ($Status -eq "✅") {
        $script:score += $Points
    }
}

Write-Host "📁 Verificando estrutura de arquivos..." -ForegroundColor Yellow

# Verificar estrutura obrigatória
$requiredStructure = @{
    "apps/build.gradle.kts" = "Build configuration raiz"
    "apps/producer/src/main/kotlin" = "Código fonte do Producer"
    "apps/consumer/src/main/kotlin" = "Código fonte do Consumer"  
    "apps/aggregator/src/main/kotlin" = "Código fonte do Aggregator"
    "terraform/main.tf" = "Infraestrutura Terraform"
    "k8s/namespace.yaml" = "Manifests Kubernetes"
    "docs/ARCHITECTURE.md" = "Documentação de arquitetura"
    ".gitignore" = "Controle de versionamento"
    "README.md" = "Documentação principal"
}

foreach ($path in $requiredStructure.Keys) {
    $fullPath = Join-Path $rootPath $path
    if (Test-Path $fullPath) {
        Add-Result "Structure" $path "✅" 3 $requiredStructure[$path]
    } else {
        Add-Result "Structure" $path "❌" 0 "$($requiredStructure[$path]) - AUSENTE"
    }
}

Write-Host "🧪 Verificando estrutura de testes..." -ForegroundColor Yellow

# Verificar testes
$modules = @("producer", "consumer", "aggregator")
foreach ($module in $modules) {
    $testPath = "apps/$module/src/test/kotlin"
    $fullTestPath = Join-Path $rootPath $testPath
    
    if (Test-Path $fullTestPath) {
        $testFiles = (Get-ChildItem $fullTestPath -Recurse -Filter "*Test.kt").Count
        $mainFiles = (Get-ChildItem (Join-Path $rootPath "apps/$module/src/main/kotlin") -Recurse -Filter "*.kt").Count
        
        $testRatio = if ($mainFiles -gt 0) { [math]::Round(($testFiles / $mainFiles) * 100, 1) } else { 0 }
        
        if ($testFiles -gt 0) {
            Add-Result "Tests" "$module tests" "✅" 5 "$testFiles arquivos de teste ($testRatio% ratio)"
        } else {
            Add-Result "Tests" "$module tests" "❌" 0 "Nenhum teste encontrado"
        }
    } else {
        Add-Result "Tests" "$module tests" "❌" 0 "Diretório de testes não existe"
    }
}

Write-Host "📄 Verificando documentação..." -ForegroundColor Yellow

# Verificar documentação
$docRequirements = @{
    "README.md" = @{ MinLines = 50; Description = "Documentação principal" }
    "docs/ARCHITECTURE.md" = @{ MinLines = 20; Description = "Documentação de arquitetura" }
    "docs/DEPLOY_GUIDE.md" = @{ MinLines = 10; Description = "Guia de deployment" }
    ".gitignore" = @{ MinLines = 10; Description = "Controle de versionamento" }
}

foreach ($doc in $docRequirements.Keys) {
    $fullDocPath = Join-Path $rootPath $doc
    if (Test-Path $fullDocPath) {
        $lineCount = (Get-Content $fullDocPath).Count
        $minLines = $docRequirements[$doc].MinLines
        
        if ($lineCount -ge $minLines) {
            Add-Result "Documentation" $doc "✅" 4 "$lineCount linhas - $($docRequirements[$doc].Description)"
        } else {
            Add-Result "Documentation" $doc "⚠️" 2 "$lineCount linhas (mínimo: $minLines) - $($docRequirements[$doc].Description)"
        }
    } else {
        Add-Result "Documentation" $doc "❌" 0 "Arquivo ausente - $($docRequirements[$doc].Description)"
    }
}

Write-Host "🔧 Verificando configurações de build..." -ForegroundColor Yellow

# Verificar configurações Gradle
$gradleFiles = @("apps/build.gradle.kts", "apps/producer/build.gradle.kts", "apps/consumer/build.gradle.kts", "apps/aggregator/build.gradle.kts")
foreach ($gradleFile in $gradleFiles) {
    $fullGradlePath = Join-Path $rootPath $gradleFile
    if (Test-Path $fullGradlePath) {
        $gradleContent = Get-Content $fullGradlePath -Raw
        
        # Verificar JaCoCo
        if ($gradleContent -like "*jacoco*") {
            Add-Result "CodeQuality" "$gradleFile - JaCoCo" "✅" 3 "Cobertura de testes configurada"
        } else {
            Add-Result "CodeQuality" "$gradleFile - JaCoCo" "❌" 0 "Cobertura de testes não configurada"
        }
        
        # Verificar dependências de teste
        if ($gradleContent -like "*kotest*" -or $gradleContent -like "*mockk*") {
            Add-Result "CodeQuality" "$gradleFile - Test deps" "✅" 2 "Dependências de teste modernas"
        } else {
            Add-Result "CodeQuality" "$gradleFile - Test deps" "⚠️" 1 "Dependências de teste básicas"
        }
    }
}

Write-Host "🔒 Verificando segurança..." -ForegroundColor Yellow

# Verificar segurança
$sensitivePatterns = @(
    @{ Pattern = "password\s*="; Description = "Hardcoded passwords" }
    @{ Pattern = "secret\s*="; Description = "Hardcoded secrets" }  
    @{ Pattern = "api_key\s*="; Description = "Hardcoded API keys" }
    @{ Pattern = "aws_access_key"; Description = "AWS credentials" }
    @{ Pattern = "AKIA[0-9A-Z]{16}"; Description = "AWS Access Key ID" }
)

$sensitiveFiles = Get-ChildItem $rootPath -Recurse -Include "*.kt", "*.yml", "*.yaml", "*.properties" -ErrorAction SilentlyContinue

$securityIssues = 0
foreach ($file in $sensitiveFiles) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if ($content) {
        foreach ($pattern in $sensitivePatterns) {
            if ($content -match $pattern.Pattern) {
                Add-Result "Security" "Sensitive data" "❌" 0 "$($pattern.Description) em $($file.Name)"
                $securityIssues++
            }
        }
    }
}

if ($securityIssues -eq 0) {
    Add-Result "Security" "Sensitive data scan" "✅" 5 "Nenhum dado sensível encontrado no código"
}

Write-Host "⚡ Verificando performance..." -ForegroundColor Yellow

# Verificar configurações de performance
$performanceChecks = @(
    @{ File = "apps/*/src/main/kotlin/**/config/KafkaProducerConfig.kt"; Pattern = "ACKS_CONFIG.*all"; Description = "Kafka producer reliability" }
    @{ File = "apps/*/src/main/kotlin/**/config/KafkaConsumerConfig.kt"; Pattern = "ENABLE_AUTO_COMMIT_CONFIG.*false"; Description = "Kafka consumer manual commit" }
)

foreach ($check in $performanceChecks) {
    $files = Get-ChildItem $rootPath -Recurse -Filter "*.kt" | Where-Object { $_.FullName -like "*config*" }
    $found = $false
    
    foreach ($file in $files) {
        $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
        if ($content -and $content -match $check.Pattern) {
            Add-Result "Performance" $check.Description "✅" 2 "Configuração otimizada encontrada"
            $found = $true
            break
        }
    }
    
    if (-not $found) {
        Add-Result "Performance" $check.Description "⚠️" 1 "Configuração pode ser otimizada"
    }
}

# Executar testes se solicitado
if ($RunTests) {
    Write-Host "🧪 Executando testes..." -ForegroundColor Yellow
    
    Push-Location (Join-Path $rootPath "apps")
    try {
        if (Test-Path "gradlew.bat") {
            & .\gradlew.bat test 2>&1 | Out-Null
        } else {
            Write-Host "  ⚠️  Gradle wrapper não encontrado" -ForegroundColor Yellow
        }
        
        # Verificar cobertura se solicitado
        if ($CheckCoverage) {
            Write-Host "📊 Gerando relatório de cobertura..." -ForegroundColor Yellow
            if (Test-Path "gradlew.bat") {
                & .\gradlew.bat jacocoTestReport 2>&1 | Out-Null
            }
        }
    } catch {
        Write-Host "  ❌ Erro ao executar testes: $($_.Exception.Message)" -ForegroundColor Red
    } finally {
        Pop-Location
    }
}

# Gerar relatório
Write-Host "`n📊 RELATÓRIO DE QUALIDADE" -ForegroundColor Cyan
Write-Host "=" * 50 -ForegroundColor Cyan

$percentage = [math]::Round(($score / $maxScore) * 100, 1)
$scoreColor = if ($percentage -ge 80) { "Green" } elseif ($percentage -ge 60) { "Yellow" } else { "Red" }

Write-Host "🎯 PONTUAÇÃO GERAL: $score/$maxScore ($percentage%)" -ForegroundColor $scoreColor

foreach ($category in $results.Keys) {
    if ($results[$category].Count -gt 0) {
        Write-Host "`n📋 $category" -ForegroundColor White
        Write-Host ("-" * ($category.Length + 4))
        
        foreach ($item in $results[$category]) {
            $statusColor = switch ($item.Status) {
                "✅" { "Green" }
                "⚠️" { "Yellow" }  
                "❌" { "Red" }
            }
            
            if ($Detailed -or $item.Status -ne "✅") {
                Write-Host "  $($item.Status) $($item.Item)" -ForegroundColor $statusColor
                if ($item.Description) {
                    Write-Host "      $($item.Description)" -ForegroundColor Gray
                }
            } else {
                Write-Host "  $($item.Status) $($item.Item)" -ForegroundColor $statusColor -NoNewline
                Write-Host " (+$($item.Points)pts)" -ForegroundColor Gray
            }
        }
    }
}

# Recomendações
Write-Host "`n💡 RECOMENDAÇÕES" -ForegroundColor Blue
Write-Host "=" * 16 -ForegroundColor Blue

if ($percentage -lt 80) {
    Write-Host "🔧 Para melhorar a qualidade do projeto:" -ForegroundColor Yellow
    
    foreach ($category in $results.Keys) {
        $failedItems = $results[$category] | Where-Object { $_.Status -eq "❌" }
        if ($failedItems.Count -gt 0) {
            Write-Host "`n${category}:" -ForegroundColor White
            foreach ($item in $failedItems) {
                Write-Host "  • $($item.Item): $($item.Description)" -ForegroundColor Gray
            }
        }
    }
}

Write-Host "`n✨ Verificação concluída!" -ForegroundColor Green

if ($percentage -ge 90) {
    Write-Host "🏆 Excelente! Projeto com alta qualidade." -ForegroundColor Green
} elseif ($percentage -ge 75) {
    Write-Host "👍 Bom projeto! Algumas melhorias podem ser feitas." -ForegroundColor Yellow
} elseif ($percentage -ge 50) {
    Write-Host "⚠️  Projeto funcional, mas precisa de melhorias significativas." -ForegroundColor Yellow
} else {
    Write-Host "❌ Projeto precisa de melhorias importantes antes do deployment." -ForegroundColor Red
}
