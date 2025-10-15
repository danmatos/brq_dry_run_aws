#!/usr/bin/env pwsh

# Comprehensive Test Runner for ETL Project
# Este script executa toda a suite de testes do projeto ETL

param(
    [ValidateSet("all", "unit", "integration", "e2e", "performance", "infrastructure")]
    [string]$TestType = "all",
    
    [ValidateSet("producer", "consumer", "aggregator", "global")]
    [string]$Component = "all",
    
    [switch]$Coverage = $false,
    [switch]$Parallel = $false,
    [switch]$Verbose = $false,
    [switch]$FailFast = $false,
    [switch]$DryRun = $false,
    [switch]$CleanFirst = $false,
    [string]$OutputDir = "./test-results"
)

# Colors for output
$Green = "Green"
$Yellow = "Yellow"  
$Red = "Red"
$Cyan = "Cyan"
$Magenta = "Magenta"

function Write-Header {
    param([string]$Message)
    Write-Host ""
    Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor $Cyan
    Write-Host $Message -ForegroundColor $Green
    Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor $Cyan
    Write-Host ""
}

function Write-Section {
    param([string]$Message)
    Write-Host ""
    Write-Host "📋 $Message" -ForegroundColor $Yellow
    Write-Host "────────────────────────────────────────────────────────────────" -ForegroundColor $Yellow
}

function Write-Success {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor $Green
}

function Write-Error {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor $Red
}

function Write-Warning {
    param([string]$Message)
    Write-Host "⚠️  $Message" -ForegroundColor $Yellow
}

function Write-Info {
    param([string]$Message)
    Write-Host "ℹ️  $Message" -ForegroundColor $Cyan
}

Write-Header "🚀 ETL Project Comprehensive Test Runner"

# Validate environment
if (-not (Test-Path "apps")) {
    Write-Error "Must be run from project root directory"
    exit 1
}

# Create output directory
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

# Initialize results tracking
$TestResults = @{
    StartTime = Get-Date
    TotalTests = 0
    PassedTests = 0 
    FailedTests = 0
    SkippedTests = 0
    Components = @{}
    TestTypes = @{}
}

function Run-ComponentTests {
    param(
        [string]$ComponentName,
        [string[]]$TestTypes
    )
    
    Write-Section "Running tests for $ComponentName"
    
    $componentDir = "apps/$ComponentName"
    if (-not (Test-Path $componentDir)) {
        Write-Warning "$ComponentName directory not found, skipping"
        return $false
    }
    
    Push-Location $componentDir
    
    $componentResults = @{
        StartTime = Get-Date
        TestsRun = 0
        Passed = 0
        Failed = 0
    }
    
    try {
        foreach ($testType in $TestTypes) {
            Write-Info "Running $testType tests for $ComponentName"
            
            $gradleArgs = @("test")
            
            # Configure test selection based on type
            switch ($testType) {
                "unit" { 
                    $gradleArgs += "--tests", "*Test"
                    $gradleArgs += "--exclude-task", "*Integration*", "*Performance*"
                }
                "integration" { 
                    $gradleArgs += "--tests", "*Integration*"
                }
                "performance" { 
                    $gradleArgs += "--tests", "*Performance*"
                }
            }
            
            # Add coverage if requested
            if ($Coverage) {
                $gradleArgs += "jacocoTestReport"
            }
            
            # Add parallel execution
            if ($Parallel) {
                $gradleArgs += "--parallel"
            }
            
            # Add verbose output
            if ($Verbose) {
                $gradleArgs += "--info"
            }
            
            # Add fail-fast
            if ($FailFast) {
                $gradleArgs += "--fail-fast"
            }
            
            if ($DryRun) {
                Write-Info "DRY RUN: Would execute ./gradlew $($gradleArgs -join ' ')"
                continue
            }
            
            Write-Info "Executing: ./gradlew $($gradleArgs -join ' ')"
            
            $startTime = Get-Date
            
            if ($IsWindows) {
                $result = & ./gradlew.bat @gradleArgs 2>&1
            } else {
                $result = & ./gradlew @gradleArgs 2>&1
            }
            
            $endTime = Get-Date
            $duration = $endTime - $startTime
            
            if ($LASTEXITCODE -eq 0) {
                Write-Success "$testType tests passed for $ComponentName (${duration.TotalSeconds:F1}s)"
                $componentResults.Passed++
                $TestResults.PassedTests++
            } else {
                Write-Error "$testType tests failed for $ComponentName"
                Write-Host $result -ForegroundColor $Red
                $componentResults.Failed++
                $TestResults.FailedTests++
                
                if ($FailFast) {
                    throw "Tests failed for $ComponentName - $testType"
                }
            }
            
            $componentResults.TestsRun++
            $TestResults.TotalTests++
        }
        
        return $true
        
    } catch {
        Write-Error "Exception running tests for $ComponentName : $_"
        return $false
    } finally {
        $TestResults.Components[$ComponentName] = $componentResults
        Pop-Location
    }
}

function Run-GlobalTests {
    param([string[]]$TestTypes)
    
    Write-Section "Running global integration tests"
    
    $testsDir = "tests"
    if (-not (Test-Path $testsDir)) {
        Write-Warning "Global tests directory not found, skipping"
        return $false
    }
    
    Push-Location $testsDir
    
    try {
        foreach ($testType in $TestTypes) {
            Write-Info "Running global $testType tests"
            
            $gradleArgs = @()
            
            switch ($testType) {
                "integration" { $gradleArgs += "integrationTest" }
                "e2e" { $gradleArgs += "e2eTest" }
                "performance" { $gradleArgs += "performanceTest" }
                "infrastructure" { $gradleArgs += "test", "--tests", "*Infrastructure*" }
                default { $gradleArgs += "test" }
            }
            
            if ($Coverage) {
                $gradleArgs += "jacocoTestReport"
            }
            
            if ($Parallel) {
                $gradleArgs += "--parallel"
            }
            
            if ($Verbose) {
                $gradleArgs += "--info"
            }
            
            if ($DryRun) {
                Write-Info "DRY RUN: Would execute gradle $($gradleArgs -join ' ')"
                continue
            }
            
            Write-Info "Executing: gradle $($gradleArgs -join ' ')"
            
            $startTime = Get-Date
            $result = & gradle @gradleArgs 2>&1
            $endTime = Get-Date
            $duration = $endTime - $startTime
            
            if ($LASTEXITCODE -eq 0) {
                Write-Success "Global $testType tests passed (${duration.TotalSeconds:F1}s)"
                $TestResults.PassedTests++
            } else {
                Write-Error "Global $testType tests failed"
                Write-Host $result -ForegroundColor $Red
                $TestResults.FailedTests++
                
                if ($FailFast) {
                    throw "Global tests failed - $testType"
                }
            }
            
            $TestResults.TotalTests++
        }
        
        return $true
        
    } catch {
        Write-Error "Exception running global tests: $_"
        return $false
    } finally {
        Pop-Location
    }
}

function Clean-BuildArtifacts {
    Write-Section "Cleaning build artifacts"
    
    $components = @("producer", "consumer", "aggregator")
    foreach ($component in $components) {
        $buildDir = "apps/$component/build"
        if (Test-Path $buildDir) {
            Write-Info "Cleaning $component build directory"
            Remove-Item -Recurse -Force $buildDir
        }
    }
    
    if (Test-Path "tests/build") {
        Write-Info "Cleaning tests build directory"
        Remove-Item -Recurse -Force "tests/build"
    }
}

function Generate-TestReport {
    Write-Section "Generating comprehensive test report"
    
    $endTime = Get-Date
    $totalDuration = $endTime - $TestResults.StartTime
    
    $reportContent = @"
# ETL Project Test Report
Generated: $endTime
Duration: $($totalDuration.ToString("c"))

## Summary
- **Total Tests**: $($TestResults.TotalTests)
- **Passed**: $($TestResults.PassedTests)
- **Failed**: $($TestResults.FailedTests)
- **Success Rate**: $(if($TestResults.TotalTests -gt 0){($TestResults.PassedTests * 100 / $TestResults.TotalTests).ToString("F1")}else{"N/A"})%

## Component Results
"@

    foreach ($component in $TestResults.Components.Keys) {
        $result = $TestResults.Components[$component]
        $reportContent += @"

### $component
- Tests Run: $($result.TestsRun)
- Passed: $($result.Passed)
- Failed: $($result.Failed)
- Success Rate: $(if($result.TestsRun -gt 0){($result.Passed * 100 / $result.TestsRun).ToString("F1")}else{"N/A"})%
"@
    }
    
    $reportContent += @"

## Test Artifacts
- JUnit Reports: Available in each component's build/reports/tests/
- JaCoCo Coverage: Available in each component's build/reports/jacoco/
- Logs: Available in build/reports/
"@

    $reportFile = Join-Path $OutputDir "test-report.md"
    $reportContent | Out-File -FilePath $reportFile -Encoding UTF8
    
    Write-Success "Test report generated: $reportFile"
    
    # Also generate JSON for CI/CD
    $jsonReport = @{
        summary = @{
            totalTests = $TestResults.TotalTests
            passed = $TestResults.PassedTests
            failed = $TestResults.FailedTests
            successRate = if($TestResults.TotalTests -gt 0){$TestResults.PassedTests * 100 / $TestResults.TotalTests}else{0}
            duration = $totalDuration.TotalSeconds
        }
        components = $TestResults.Components
        timestamp = $endTime.ToString("yyyy-MM-ddTHH:mm:ss")
    }
    
    $jsonFile = Join-Path $OutputDir "test-results.json"
    $jsonReport | ConvertTo-Json -Depth 10 | Out-File -FilePath $jsonFile -Encoding UTF8
    
    Write-Success "JSON report generated: $jsonFile"
}

# Main execution flow
try {
    Write-Info "Test Configuration:"
    Write-Host "  - Test Type: $TestType"
    Write-Host "  - Component: $Component" 
    Write-Host "  - Coverage: $Coverage"
    Write-Host "  - Parallel: $Parallel"
    Write-Host "  - Verbose: $Verbose"
    Write-Host "  - Fail Fast: $FailFast"
    Write-Host "  - Dry Run: $DryRun"
    Write-Host ""
    
    if ($CleanFirst) {
        Clean-BuildArtifacts
    }
    
    # Determine which test types to run
    $testTypesToRun = switch ($TestType) {
        "all" { @("unit", "integration", "e2e", "performance", "infrastructure") }
        default { @($TestType) }
    }
    
    # Determine which components to test
    $componentsToTest = switch ($Component) {
        "all" { @("producer", "consumer", "aggregator") }
        "global" { @() } # Only global tests
        default { @($Component) }
    }
    
    Write-Info "Will run test types: $($testTypesToRun -join ', ')"
    Write-Info "Will test components: $($componentsToTest -join ', ')"
    
    $overallSuccess = $true
    
    # Run component tests
    foreach ($comp in $componentsToTest) {
        $componentTestTypes = $testTypesToRun | Where-Object { $_ -in @("unit", "integration", "performance") }
        if ($componentTestTypes.Count -gt 0) {
            $success = Run-ComponentTests -ComponentName $comp -TestTypes $componentTestTypes
            $overallSuccess = $overallSuccess -and $success
        }
    }
    
    # Run global tests (e2e, infrastructure)
    if ($Component -eq "all" -or $Component -eq "global") {
        $globalTestTypes = $testTypesToRun | Where-Object { $_ -in @("e2e", "infrastructure", "integration") }
        if ($globalTestTypes.Count -gt 0) {
            $success = Run-GlobalTests -TestTypes $globalTestTypes
            $overallSuccess = $overallSuccess -and $success
        }
    }
    
    # Generate reports
    Generate-TestReport
    
    # Final summary
    Write-Header "🎯 Test Execution Complete"
    
    if ($overallSuccess -and $TestResults.FailedTests -eq 0) {
        Write-Success "All tests passed successfully! 🎉"
        Write-Info "Total: $($TestResults.TotalTests) tests, $($TestResults.PassedTests) passed"
        exit 0
    } else {
        Write-Error "Some tests failed"
        Write-Info "Total: $($TestResults.TotalTests) tests, $($TestResults.PassedTests) passed, $($TestResults.FailedTests) failed"
        exit 1
    }
    
} catch {
    Write-Error "Fatal error during test execution: $_"
    Write-Error $_.ScriptStackTrace
    exit 1
}

# Usage examples at the end of file as comments:
<#
Examples:

# Run all tests for all components
.\run-comprehensive-tests.ps1

# Run only unit tests
.\run-comprehensive-tests.ps1 -TestType unit

# Run integration tests with coverage
.\run-comprehensive-tests.ps1 -TestType integration -Coverage

# Run performance tests for specific component  
.\run-comprehensive-tests.ps1 -TestType performance -Component producer

# Run E2E tests with verbose output
.\run-comprehensive-tests.ps1 -TestType e2e -Verbose

# Dry run to see what would be executed
.\run-comprehensive-tests.ps1 -DryRun -Verbose

# Clean build and run all tests in parallel with coverage
.\run-comprehensive-tests.ps1 -CleanFirst -Parallel -Coverage

# Run tests with fail-fast for CI/CD
.\run-comprehensive-tests.ps1 -FailFast -TestType "unit,integration"
#>
