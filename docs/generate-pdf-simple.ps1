# ETL Production Deployment - PDF Generator
# PowerShell script to open HTML report

Write-Host "ETL Production Deployment - PDF Generator" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

$htmlFile = Join-Path $PSScriptRoot "ETL_Production_Deployment_Report.html"

if (Test-Path $htmlFile) {
    Write-Host "HTML report found: $htmlFile" -ForegroundColor Green
    Write-Host ""
    Write-Host "Document Contains:" -ForegroundColor Yellow
    Write-Host "  - Executive Summary" -ForegroundColor White
    Write-Host "  - 7 Critical Errors and Solutions" -ForegroundColor White
    Write-Host "  - Fargate to EC2 Migration Details" -ForegroundColor White
    Write-Host "  - Architecture Comparison" -ForegroundColor White
    Write-Host "  - Production Metrics" -ForegroundColor White
    Write-Host "  - Lessons Learned" -ForegroundColor White
    Write-Host ""
    
    Write-Host "Opening HTML report in browser..." -ForegroundColor Blue
    Start-Process $htmlFile
    
    Write-Host ""
    Write-Host "To generate PDF:" -ForegroundColor Yellow
    Write-Host "  1. Wait for browser to open" -ForegroundColor White
    Write-Host "  2. Press Ctrl+P" -ForegroundColor White
    Write-Host "  3. Select Save as PDF" -ForegroundColor White
    Write-Host "  4. Adjust margins to Minimum" -ForegroundColor White
    Write-Host "  5. Enable Background graphics" -ForegroundColor White
    Write-Host "  6. Click Save" -ForegroundColor White
    Write-Host ""
    
    Write-Host "Status: Production Operational" -ForegroundColor Green
    
} else {
    Write-Host "HTML file not found: $htmlFile" -ForegroundColor Red
}

Write-Host ""
Read-Host "Press Enter to continue"
