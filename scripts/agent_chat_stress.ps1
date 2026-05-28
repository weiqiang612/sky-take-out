param(
    [string]$BaseUrl = "http://localhost:8081",
    [int]$Count = 20,
    [string]$Question = "你们店什么时候开始营业？",
    [string]$UserId = "user_stress_test"
)

$EscapedQuestion = [uri]::EscapeDataString($Question)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  蒼穹外賣 AI Agent Chat High-Throughput Stress Tester" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Target Endpoint: $BaseUrl/ai/ask"
Write-Host "Concurrency:     $Count parallel requests"
Write-Host "Query Question:  $Question"
Write-Host "User ID:         $UserId"
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Launching stress workers..." -ForegroundColor Yellow

$jobs = @()
$startTime = [System.Diagnostics.Stopwatch]::StartNew()

for ($i = 1; $i -le $Count; $i++) {
    $convId = "stress-conv-" + $i + "-" + (Get-Random -Minimum 100000 -Maximum 999999)
    $jobs += Start-Job -ScriptBlock {
        param($BaseUrl, $EscapedQuestion, $UserId, $ConvId, $Index)
        
        $url = "$BaseUrl/ai/ask?question=$EscapedQuestion&userId=$UserId&conversationId=$ConvId"
        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        
        try {
            $response = Invoke-RestMethod -Method Get -Uri $url -TimeoutSec 15
            $stopwatch.Stop()
            
            [pscustomobject]@{
                Index        = $Index
                StatusCode   = 200
                LatencyMs    = $stopwatch.ElapsedMilliseconds
                Answer       = $response.answer
                ErrorMessage = ""
            }
        } catch {
            $stopwatch.Stop()
            $status = "ERROR"
            $msg = $_.Exception.Message
            if ($_.Exception.Response) {
                $status = [int]$_.Exception.Response.StatusCode
            }
            
            [pscustomobject]@{
                Index        = $Index
                StatusCode   = $status
                LatencyMs    = $stopwatch.ElapsedMilliseconds
                Answer       = ""
                ErrorMessage = $msg
            }
        }
    } -ArgumentList $BaseUrl, $EscapedQuestion, $UserId, $convId, $i
}

Write-Host "Waiting for all workers to finish..." -ForegroundColor Yellow
$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job
$startTime.Stop()

$totalWallTimeMs = $startTime.ElapsedMilliseconds
$qps = [math]::Round(($Count / ($totalWallTimeMs / 1000.0)), 2)

# Aggregating Latencies
$latencies = $results | Select-Object -ExpandProperty LatencyMs | Sort-Object
$avgLatency = [math]::Round(($latencies | Measure-Object -Average | Select-Object -ExpandProperty Average), 1)
$maxLatency = $latencies[-1]
$p95Index = [math]::Max(0, [math]::Min($Count - 1, [math]::Ceiling($Count * 0.95) - 1))
$p95Latency = $latencies[$p95Index]

# Status counts
$successes = ($results | Where-Object { $_.StatusCode -eq 200 }).Count
$errors = $Count - $successes
$successRate = [math]::Round(($successes / $Count * 100.0), 1)

Write-Host "`n================📊 STRESS TEST RESULTS 📊=================" -ForegroundColor Green
Write-Host "Total Concurrency Run:  $Count requests"
Write-Host "Total Elapsed Time:     $totalWallTimeMs ms"
Write-Host "Aggregated QPS:         $qps queries/sec" -ForegroundColor Yellow
Write-Host "Average Response Time:  $avgLatency ms"
Write-Host "P95 Response Time:      $p95Latency ms" -ForegroundColor Yellow
Write-Host "Max Response Time:      $maxLatency ms"
Write-Host "Success Rate:           $successRate% ($successes successes, $errors failures)" -ForegroundColor (if ($successRate -eq 100) { "Green" } else { "Red" })
Write-Host "==========================================================" -ForegroundColor Green

Write-Host "`n📝 Detail Request log sample (Top 5):" -ForegroundColor Yellow
$results | Sort-Object Index | Select-Object -First 5 | Format-Table Index, StatusCode, LatencyMs, Answer -AutoSize

if ($successRate -ge 95) {
    Write-Host "🏅 Test Passed! High-concurrency performance verified successfully." -ForegroundColor Green
} else {
    Write-Host "❌ Test Failed! Success rate below 95% threshold. Please check system resources." -ForegroundColor Red
}
