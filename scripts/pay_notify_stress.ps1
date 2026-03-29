param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$OutTradeNo = "test_out_trade_no_001",
    [int]$Count = 20
)

$body = @{ out_trade_no = $OutTradeNo } | ConvertTo-Json -Compress

$jobs = @()
for ($i = 1; $i -le $Count; $i++) {
    $jobs += Start-Job -ScriptBlock {
        param($Url, $Payload, $Index)
        try {
            $response = Invoke-WebRequest -Method Post -Uri "$Url/notify/paySuccess" -ContentType "application/json" -Body $Payload
            [pscustomobject]@{
                Index       = $Index
                StatusCode  = $response.StatusCode
                ContentType = $response.Headers["Content-Type"]
            }
        } catch {
            [pscustomobject]@{
                Index       = $Index
                StatusCode  = "ERROR"
                ContentType = $_.Exception.Message
            }
        }
    } -ArgumentList $BaseUrl, $body, $i
}

$results = $jobs | Wait-Job | Receive-Job
$results | Sort-Object Index | Format-Table -AutoSize
$jobs | Remove-Job

Write-Host "Verify:"
Write-Host "1. orders.pay_status changes from 0 to 1 once."
Write-Host "2. pay_callback_record contains exactly one row for the out_trade_no."
Write-Host "3. duplicate responses still return HTTP 200."
