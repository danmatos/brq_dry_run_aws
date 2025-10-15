# 🚀 GitHub Setup Automation Script

param(
    [Parameter(Mandatory=$true)]
    [string]$RepositoryName,
    
    [Parameter(Mandatory=$false)]
    [string]$GitHubUsername,
    
    [Parameter(Mandatory=$false)]
    [string]$Description = "AWS EKS MSK ETL Starter - Modern data processing pipeline",
    
    [Parameter(Mandatory=$false)]
    [switch]$Private = $false,
    
    [Parameter(Mandatory=$false)]
    [switch]$CreateRepository = $true,
    
    [Parameter(Mandatory=$false)]
    [switch]$SetupBranchProtection = $true,
    
    [Parameter(Mandatory=$false)]
    [switch]$ConfigureSecrets = $false,
    
    [Parameter(Mandatory=$false)]
    [string]$GitHubToken
)

# Configurações
$ErrorActionPreference = "Stop"

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
    
    # Git
    try {
        $gitVersion = git --version
        Write-Success "Git: $gitVersion"
    } catch {
        Write-Error "Git não encontrado. Instale: https://git-scm.com/"
        exit 1
    }
    
    # GitHub CLI
    try {
        $ghVersion = gh --version | Select-Object -First 1
        Write-Success "GitHub CLI: $ghVersion"
    } catch {
        Write-Error "GitHub CLI não encontrado. Instale: https://cli.github.com/"
        exit 1
    }
    
    # Verificar autenticação GitHub
    try {
        $currentUser = gh auth status 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "GitHub CLI não está autenticado"
            Write-Info "Execute: gh auth login"
            
            if (-not $GitHubToken) {
                Read-Host "Pressione ENTER após autenticar com GitHub CLI"
            }
        } else {
            Write-Success "GitHub CLI autenticado"
        }
    } catch {
        Write-Warning "Erro verificando autenticação GitHub CLI"
    }
    
    # Verificar se já estamos em um repositório Git
    if (Test-Path ".git") {
        Write-Info "Repositório Git já existe no diretório atual"
        $currentRemote = git remote get-url origin 2>$null
        if ($currentRemote) {
            Write-Info "Remote atual: $currentRemote"
        }
    } else {
        Write-Info "Novo repositório Git será criado"
    }
}

# Criar repositório no GitHub
function New-GitHubRepository {
    Write-Banner "CRIANDO REPOSITÓRIO GITHUB"
    
    if (-not $CreateRepository) {
        Write-Warning "Pulando criação do repositório (--CreateRepository false)"
        return
    }
    
    $visibilityFlag = if ($Private) { "--private" } else { "--public" }
    
    try {
        Write-Info "Criando repositório: $RepositoryName"
        
        # Criar repositório
        gh repo create $RepositoryName `
            --description $Description `
            $visibilityFlag `
            --clone
        
        Write-Success "Repositório criado com sucesso!"
        
        # Navegar para o diretório do repo
        Set-Location $RepositoryName
        
    } catch {
        Write-Error "Erro criando repositório: $_"
        exit 1
    }
}

# Setup inicial do Git
function Initialize-GitRepository {
    Write-Banner "CONFIGURANDO REPOSITÓRIO GIT"
    
    # Inicializar Git se necessário
    if (-not (Test-Path ".git")) {
        Write-Info "Inicializando repositório Git..."
        git init
        git branch -M main
    }
    
    # Configurar remote se necessário
    $remoteUrl = "https://github.com/$GitHubUsername/$RepositoryName.git"
    
    try {
        $currentRemote = git remote get-url origin 2>$null
        if (-not $currentRemote) {
            Write-Info "Adicionando remote origin: $remoteUrl"
            git remote add origin $remoteUrl
        } else {
            Write-Info "Remote origin já configurado: $currentRemote"
        }
    } catch {
        Write-Info "Configurando remote origin: $remoteUrl"
        git remote add origin $remoteUrl
    }
    
    # Commit inicial se necessário
    if (-not (git log --oneline 2>$null)) {
        Write-Info "Fazendo commit inicial..."
        
        # Adicionar todos os arquivos
        git add .
        git commit -m "feat: initial project structure with CI/CD pipelines

- Complete GitHub Actions workflows for CI/CD
- Terraform infrastructure as code
- Kubernetes manifests
- Comprehensive testing strategy
- Documentation and guides"
        
        # Push para GitHub
        git push -u origin main
        
        Write-Success "Commit inicial criado e pushed!"
    } else {
        Write-Info "Repositório já possui commits"
        
        # Push mudanças se houver
        $changes = git status --porcelain
        if ($changes) {
            Write-Info "Fazendo push das mudanças..."
            git add .
            git commit -m "feat: add GitHub Actions CI/CD workflows and documentation"
            git push
        }
    }
}

# Configurar branch protection
function Set-BranchProtection {
    Write-Banner "CONFIGURANDO BRANCH PROTECTION"
    
    if (-not $SetupBranchProtection) {
        Write-Warning "Pulando configuração de branch protection"
        return
    }
    
    try {
        Write-Info "Configurando branch protection para 'main'..."
        
        # Configurar branch protection via GitHub CLI
        gh api repos/$GitHubUsername/$RepositoryName/branches/main/protection `
            --method PUT `
            --field required_status_checks='{"strict":true,"contexts":["Code Quality","Unit Tests","Build Verification","Security Scan"]}' `
            --field enforce_admins=true `
            --field required_pull_request_reviews='{"required_approving_review_count":1,"dismiss_stale_reviews":true,"require_code_owner_reviews":false}' `
            --field restrictions=null `
            --field required_linear_history=true `
            --field allow_force_pushes=false `
            --field allow_deletions=false
        
        Write-Success "Branch protection configurado!"
        
    } catch {
        Write-Warning "Erro configurando branch protection: $_"
        Write-Info "Configure manualmente via GitHub web interface:"
        Write-Info "https://github.com/$GitHubUsername/$RepositoryName/settings/branches"
    }
}

# Configurar labels
function Set-GitHubLabels {
    Write-Banner "CONFIGURANDO LABELS"
    
    $labels = @(
        @{ name = "type: bug"; color = "d73a4a"; description = "Something isn't working" }
        @{ name = "type: feature"; color = "a2eeef"; description = "New feature or request" }
        @{ name = "type: enhancement"; color = "84b6eb"; description = "Enhancement to existing functionality" }
        @{ name = "type: documentation"; color = "0075ca"; description = "Improvements or additions to documentation" }
        @{ name = "priority: high"; color = "ff6b6b"; description = "High priority" }
        @{ name = "priority: medium"; color = "ffa726"; description = "Medium priority" }
        @{ name = "priority: low"; color = "66bb6a"; description = "Low priority" }
        @{ name = "area: backend"; color = "7057ff"; description = "Backend related" }
        @{ name = "area: frontend"; color = "008672"; description = "Frontend related" }
        @{ name = "area: infrastructure"; color = "5319e7"; description = "Infrastructure related" }
        @{ name = "area: ci-cd"; color = "1d76db"; description = "CI/CD pipeline related" }
        @{ name = "dependencies"; color = "0366d6"; description = "Pull requests that update a dependency file" }
        @{ name = "security"; color = "ee0701"; description = "Security related" }
        @{ name = "performance"; color = "ff9f40"; description = "Performance improvements" }
    )
    
    foreach ($label in $labels) {
        try {
            gh label create $label.name --color $label.color --description $label.description 2>$null
            Write-Success "Label criado: $($label.name)"
        } catch {
            Write-Info "Label já existe: $($label.name)"
        }
    }
}

# Configurar secrets (interativo)
function Set-GitHubSecrets {
    Write-Banner "CONFIGURANDO SECRETS"
    
    if (-not $ConfigureSecrets) {
        Write-Warning "Pulando configuração de secrets"
        Write-Info "Configure manualmente em: https://github.com/$GitHubUsername/$RepositoryName/settings/secrets/actions"
        return
    }
    
    $secrets = @(
        @{ name = "AWS_ACCESS_KEY_ID"; description = "AWS Access Key ID para deploy" }
        @{ name = "AWS_SECRET_ACCESS_KEY"; description = "AWS Secret Access Key para deploy" }
        @{ name = "AWS_ACCOUNT_DEV"; description = "AWS Account ID para desenvolvimento" }
        @{ name = "AWS_ACCOUNT_STAGING"; description = "AWS Account ID para staging" }
        @{ name = "AWS_ACCOUNT_PROD"; description = "AWS Account ID para produção" }
        @{ name = "TERRAFORM_STATE_BUCKET"; description = "S3 bucket para Terraform state" }
        @{ name = "SONAR_TOKEN"; description = "SonarCloud token (opcional)" }
        @{ name = "CODECOV_TOKEN"; description = "Codecov token (opcional)" }
        @{ name = "SLACK_WEBHOOK_URL"; description = "Slack webhook para notificações (opcional)" }
    )
    
    Write-Info "Configuração interativa de secrets..."
    Write-Warning "ATENÇÃO: Secrets são sensíveis. Digite com cuidado!"
    Write-Info ""
    
    foreach ($secret in $secrets) {
        Write-Host "🔐 " -NoNewline -ForegroundColor Yellow
        $value = Read-Host -Prompt "$($secret.name) - $($secret.description)"
        
        if ($value) {
            try {
                echo $value | gh secret set $secret.name
                Write-Success "Secret configurado: $($secret.name)"
            } catch {
                Write-Error "Erro configurando secret $($secret.name): $_"
            }
        } else {
            Write-Info "Pulando secret: $($secret.name)"
        }
    }
}

# Criar issue templates
function New-IssueTemplates {
    Write-Banner "CRIANDO ISSUE TEMPLATES"
    
    $templateDir = ".github/ISSUE_TEMPLATE"
    New-Item -ItemType Directory -Path $templateDir -Force | Out-Null
    
    # Bug Report Template
    $bugTemplate = @"
---
name: 🐛 Bug Report
about: Create a report to help us improve
title: '[BUG] '
labels: ['type: bug']
assignees: ''

---

## 🐛 Bug Description
A clear and concise description of what the bug is.

## 🔄 Steps to Reproduce
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

## ✅ Expected Behavior
A clear and concise description of what you expected to happen.

## 📸 Screenshots
If applicable, add screenshots to help explain your problem.

## 🖥️ Environment
- OS: [e.g. Windows 10, macOS 11.0, Ubuntu 20.04]
- Version: [e.g. v1.0.0]
- Browser: [e.g. chrome, safari] (if applicable)

## 📝 Additional Context
Add any other context about the problem here.
"@
    
    $bugTemplate | Out-File "$templateDir/bug_report.md" -Encoding UTF8
    
    # Feature Request Template
    $featureTemplate = @"
---
name: 🚀 Feature Request
about: Suggest an idea for this project
title: '[FEATURE] '
labels: ['type: feature']
assignees: ''

---

## 🚀 Feature Description
A clear and concise description of what you want to happen.

## 💡 Motivation
Is your feature request related to a problem? Please describe.
A clear and concise description of what the problem is. Ex. I'm always frustrated when [...]

## 📋 Detailed Description
Describe the solution you'd like
A clear and concise description of what you want to happen.

## 🔄 Alternatives Considered
Describe alternatives you've considered
A clear and concise description of any alternative solutions or features you've considered.

## 📝 Additional Context
Add any other context or screenshots about the feature request here.
"@
    
    $featureTemplate | Out-File "$templateDir/feature_request.md" -Encoding UTF8
    
    Write-Success "Issue templates criados!"
}

# Criar PR template
function New-PullRequestTemplate {
    Write-Banner "CRIANDO PR TEMPLATE"
    
    $prTemplate = @"
## 📋 Description
Brief description of what this PR accomplishes.

## 🔗 Related Issues
- Closes #[issue number]
- Relates to #[issue number]

## 🧪 Type of Change
- [ ] 🐛 Bug fix (non-breaking change which fixes an issue)
- [ ] 🚀 New feature (non-breaking change which adds functionality)
- [ ] ⚡ Performance improvement (non-breaking change which improves performance)
- [ ] 🔧 Refactoring (non-breaking change which improves code quality)
- [ ] 📝 Documentation update
- [ ] 🔒 Security improvement
- [ ] 💥 Breaking change (fix or feature that would cause existing functionality to not work as expected)

## ✅ Checklist
- [ ] My code follows the style guidelines of this project
- [ ] I have performed a self-review of my own code
- [ ] I have commented my code, particularly in hard-to-understand areas
- [ ] I have made corresponding changes to the documentation
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing unit tests pass locally with my changes

## 🧪 Testing
Describe the tests that you ran to verify your changes.

- [ ] Unit Tests
- [ ] Integration Tests  
- [ ] Manual Testing

## 📸 Screenshots (if applicable)
Add screenshots to help reviewers understand your changes.

## 📝 Additional Notes
Any additional information that reviewers should know.
"@
    
    $prTemplate | Out-File ".github/pull_request_template.md" -Encoding UTF8
    Write-Success "PR template criado!"
}

# Validar setup
function Test-GitHubSetup {
    Write-Banner "VALIDANDO SETUP"
    
    try {
        # Verificar se repositório existe
        $repo = gh repo view --json name,url,visibility
        $repoInfo = $repo | ConvertFrom-Json
        Write-Success "Repositório: $($repoInfo.name)"
        Write-Info "URL: $($repoInfo.url)"
        Write-Info "Visibilidade: $($repoInfo.visibility)"
        
        # Verificar workflows
        if (Test-Path ".github/workflows") {
            $workflows = Get-ChildItem ".github/workflows" -Filter "*.yml" | Measure-Object
            Write-Success "Workflows: $($workflows.Count) arquivos encontrados"
        }
        
        # Verificar branch protection (se configurado)
        if ($SetupBranchProtection) {
            try {
                $protection = gh api repos/$GitHubUsername/$RepositoryName/branches/main/protection 2>$null
                if ($protection) {
                    Write-Success "Branch protection: Configurado"
                } else {
                    Write-Warning "Branch protection: Não detectado"
                }
            } catch {
                Write-Warning "Branch protection: Não foi possível verificar"
            }
        }
        
        # Verificar secrets
        try {
            $secrets = gh secret list --json name
            $secretsList = ($secrets | ConvertFrom-Json)
            Write-Success "Secrets: $($secretsList.Count) configurados"
            
            foreach ($secret in $secretsList) {
                Write-Info "  - $($secret.name)"
            }
        } catch {
            Write-Warning "Não foi possível listar secrets"
        }
        
    } catch {
        Write-Error "Erro validando setup: $_"
    }
}

# Função principal
function Main {
    Write-Banner "🚀 GITHUB SETUP AUTOMATION"
    
    Write-Info "Configurações:"
    Write-Info "  Repository: $RepositoryName"
    Write-Info "  Username: $GitHubUsername"
    Write-Info "  Private: $Private"
    Write-Info "  Create Repo: $CreateRepository"
    Write-Info "  Branch Protection: $SetupBranchProtection"
    Write-Info "  Configure Secrets: $ConfigureSecrets"
    
    Write-Host ""
    Read-Host "Pressione ENTER para continuar ou CTRL+C para cancelar"
    
    # Detectar username se não fornecido
    if (-not $GitHubUsername) {
        try {
            $GitHubUsername = gh auth status --show-token 2>&1 | Select-String "Logged in to github.com as ([^)]+)" | ForEach-Object { $_.Matches[0].Groups[1].Value }
            if (-not $GitHubUsername) {
                $GitHubUsername = gh api user --jq '.login'
            }
            Write-Info "Username detectado: $GitHubUsername"
        } catch {
            Write-Error "Não foi possível detectar username. Use -GitHubUsername parameter"
            exit 1
        }
    }
    
    # Executar setup
    Test-Prerequisites
    
    if ($CreateRepository) {
        New-GitHubRepository
    }
    
    Initialize-GitRepository
    Set-BranchProtection
    Set-GitHubLabels
    New-IssueTemplates
    New-PullRequestTemplate
    
    if ($ConfigureSecrets) {
        Set-GitHubSecrets
    }
    
    Test-GitHubSetup
    
    Write-Banner "🎉 SETUP CONCLUÍDO COM SUCESSO!"
    
    Write-Success "Repositório GitHub configurado! 🚀"
    Write-Info ""
    Write-Info "PRÓXIMOS PASSOS:"
    Write-Info "1. Acesse: https://github.com/$GitHubUsername/$RepositoryName"
    Write-Info "2. Configure secrets se ainda não fez: Settings → Secrets and variables → Actions"
    Write-Info "3. Faça um commit para testar CI/CD:"
    Write-Info "   git add . && git commit -m 'test: trigger CI pipeline' && git push"
    Write-Info "4. Crie um PR para testar validação de PR"
    Write-Info "5. Configure notifications do Slack (opcional)"
    Write-Info ""
    
    if (-not $ConfigureSecrets) {
        Write-Warning "⚠️ LEMBRE-SE: Configure os secrets necessários para AWS!"
        Write-Info "Execute novamente com -ConfigureSecrets ou configure manualmente."
    }
    
    Write-Info "GitHub Actions será executado automaticamente nos próximos commits! 🔄"
    Write-Success "Happy coding! 🎉"
}

# Executar
try {
    Main
} catch {
    Write-Error "Erro inesperado: $_"
    exit 1
}
