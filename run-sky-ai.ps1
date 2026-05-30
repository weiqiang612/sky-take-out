# =============================================================================
# run-sky-ai.ps1 — Helper script to run sky-ai with correct environment variables
# =============================================================================
$ROOT_DIR = Get-Location
$WorkspaceXmlPath = Join-Path $ROOT_DIR ".idea\workspace.xml"

Write-Host "[run-sky-ai] Reading workspace.xml at $WorkspaceXmlPath..." -ForegroundColor Cyan

if (Test-Path $WorkspaceXmlPath) {
    try {
        $Content = Get-Content $WorkspaceXmlPath -Raw
        
        # 1. Load SkyAiApplication envs
        if ($Content -match '(?s)<configuration name="SkyAiApplication".*?</configuration>') {
            $ConfigBlock = $Matches[0]
            $EnvMatches = [regex]::Matches($ConfigBlock, '<env name="([^"]+)" value="([^"]+)"')
            foreach ($Match in $EnvMatches) {
                $Name = $Match.Groups[1].Value
                $Value = $Match.Groups[2].Value
                if ($Name) {
                    [System.Environment]::SetEnvironmentVariable($Name, $Value, [System.EnvironmentVariableTarget]::Process)
                    Write-Host "[run-sky-ai] Loaded SkyAi env: $Name" -ForegroundColor DarkCyan
                }
            }
        } else {
            Write-Host "[run-sky-ai] WARNING: SkyAiApplication configuration not found in workspace.xml" -ForegroundColor Yellow
        }
        
        # 2. Load SkyApplication envs (optional but good for compatibility)
        if ($Content -match '(?s)<configuration name="SkyApplication".*?</configuration>') {
            $ConfigBlock = $Matches[0]
            $EnvMatches = [regex]::Matches($ConfigBlock, '<env name="([^"]+)" value="([^"]+)"')
            foreach ($Match in $EnvMatches) {
                $Name = $Match.Groups[1].Value
                $Value = $Match.Groups[2].Value
                if ($Name) {
                    # Only load if not already set by SkyAi
                    if (-not [System.Environment]::GetEnvironmentVariable($Name, [System.EnvironmentVariableTarget]::Process)) {
                        [System.Environment]::SetEnvironmentVariable($Name, $Value, [System.EnvironmentVariableTarget]::Process)
                        Write-Host "[run-sky-ai] Loaded Sky (fallback) env: $Name" -ForegroundColor DarkGray
                    }
                }
            }
        }
    } catch {
        Write-Host "[run-sky-ai] WARNING: Failed to parse workspace.xml: $_" -ForegroundColor Yellow
    }
} else {
    Write-Host "[run-sky-ai] WARNING: workspace.xml not found at $WorkspaceXmlPath" -ForegroundColor Yellow
}

# Run the sky-ai service
Write-Host "[run-sky-ai] Starting sky-ai service..." -ForegroundColor Green
mvn -pl sky-ai spring-boot:run
