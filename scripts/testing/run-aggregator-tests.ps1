#!/usr/bin/env pwsh

# Test execution script for Aggregator service
# Este script executa todos os tipos de testes para o serviço Aggregator

param(
    [string]$TestType = "all",  # all, unit, integration, performance
    [switch]$Coverage = $false,
    [switch]$Parallel = $false,
    [switch]$Verbose = $false
)

Write-Host "🚀 Executando testes do Aggregator" -ForegroundColor Green
Write-Host "=================================" -ForegroundColor Green

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$aggregatorDir = Join-Path $projectDir "apps/aggregator"

if (-not (Test-Path $aggregatorDir)) {
    Write-Error "Diretório do aggregator não encontrado: $aggregatorDir"
    exit 1
}

Set-Location $aggregatorDir

# Configurar opções do Gradle
$gradleArgs = @()

if ($Verbose) {
    $gradleArgs += "--info"
}

if ($Parallel) {
    $gradleArgs += "--parallel"
}

if ($Coverage) {
    $gradleArgs += "jacocoTestReport"
}

# Executar testes baseado no tipo especificado
switch ($TestType.ToLower()) {
    "unit" {
        Write-Host "📋 Executando apenas testes unitários..." -ForegroundColor Yellow
        $gradleArgs += "test"
        $gradleArgs += "--tests"
        $gradleArgs += "*Test"
        $gradleArgs += "--exclude-task"
        $gradleArgs += "*Integration*"
    }
    "integration" {
        Write-Host "🔗 Executando apenas testes de integração..." -ForegroundColor Yellow
        $gradleArgs += "test"
        $gradleArgs += "--tests"
        $gradleArgs += "*Integration*"
    }
    "performance" {
        Write-Host "⚡ Executando apenas testes de performance..." -ForegroundColor Yellow
        $gradleArgs += "test"
        $gradleArgs += "--tests"
        $gradleArgs += "*Performance*"
    }
    "all" {
        Write-Host "🎯 Executando todos os testes..." -ForegroundColor Yellow
        $gradleArgs += "test"
    }
    default {
        Write-Error "Tipo de teste inválido: $TestType. Use: all, unit, integration, performance"
        exit 1
    }
}

# Verificar se Gradle Wrapper existe
if (-not (Test-Path "./gradlew.bat")) {
    Write-Error "Gradle Wrapper não encontrado. Execute este script do diretório do aggregator."
    exit 1
}

Write-Host "🔧 Executando: ./gradlew $($gradleArgs -join ' ')" -ForegroundColor Cyan

try {
    # Executar testes
    & ./gradlew @gradleArgs
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Todos os testes passaram com sucesso!" -ForegroundColor Green
        
        # Mostrar relatórios gerados
        $testReportDir = "./build/reports/tests/test"
        if (Test-Path $testReportDir) {
            Write-Host "📊 Relatório de testes disponível em: $testReportDir/index.html" -ForegroundColor Cyan
        }
        
        if ($Coverage) {
            $coverageReportDir = "./build/reports/jacoco/test/html"
            if (Test-Path $coverageReportDir) {
                Write-Host "📈 Relatório de cobertura disponível em: $coverageReportDir/index.html" -ForegroundColor Cyan
            }
        }
        
        # Resumo dos testes
        Write-Host ""
        Write-Host "📋 Resumo dos Testes Executados:" -ForegroundColor Yellow
        Write-Host "================================"
        
        # Contar arquivos de teste
        $unitTests = (Get-ChildItem -Path "./src/test" -Filter "*Test.kt" -Recurse | Where-Object { $_.Name -notmatch "Integration|Performance" }).Count
        $integrationTests = (Get-ChildItem -Path "./src/test" -Filter "*Integration*.kt" -Recurse).Count  
        $performanceTests = (Get-ChildItem -Path "./src/test" -Filter "*Performance*.kt" -Recurse).Count
        
        Write-Host "• Testes Unitários: $unitTests arquivos" -ForegroundColor Green
        Write-Host "• Testes Integração: $integrationTests arquivos" -ForegroundColor Green  
        Write-Host "• Testes Performance: $performanceTests arquivos" -ForegroundColor Green
        Write-Host "• Total: $($unitTests + $integrationTests + $performanceTests) arquivos de teste" -ForegroundColor Green
        
    } else {
        Write-Host "❌ Alguns testes falharam. Verifique o relatório para detalhes." -ForegroundColor Red
        exit 1
    }
    
} catch {
    Write-Error "Erro ao executar testes: $($_.Exception.Message)"
    exit 1
}

Write-Host ""
Write-Host "🎉 Execução de testes concluída!" -ForegroundColor Green
Write-Host ""
Write-Host "💡 Dicas:" -ForegroundColor Yellow
Write-Host "• Use -TestType unit para executar apenas testes unitários"
Write-Host "• Use -Coverage para gerar relatório de cobertura"
Write-Host "• Use -Parallel para execução paralela (mais rápido)"
Write-Host "• Use -Verbose para logs detalhados"
Write-Host ""
Write-Host "Exemplo: .\run-aggregator-tests.ps1 -TestType unit -Coverage -Verbose"
