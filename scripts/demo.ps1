<#
    Fires N requests at the protected endpoint as one client and prints the status of each,
    so you can watch 201s turn into 429s.

    usage: .\scripts\demo.ps1 alice 12
#>
param(
    [string]$ClientId = "alice",
    [int]$Count = 12,
    [string]$BaseUrl = "http://localhost:8080"
)

Write-Host "Active configuration:" -ForegroundColor Cyan
Invoke-RestMethod -Uri "$BaseUrl/api/admin/rate-limit" | ConvertTo-Json -Depth 4

Write-Host "`nSending $Count requests as '$ClientId' ..." -ForegroundColor Cyan
for ($i = 1; $i -le $Count; $i++) {
    $body = @{ message = "request $i" } | ConvertTo-Json -Compress
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/api/messages" -Method Post -Body $body `
            -ContentType "application/json" -Headers @{ "X-Client-Id" = $ClientId }
        $status = $response.StatusCode
        $algorithm = $response.Headers["X-RateLimit-Algorithm"]
        $remaining = $response.Headers["X-RateLimit-Remaining"]
        $color = "Green"
        $extra = "remaining=$remaining"
    }
    catch {
        $errorResponse = $_.Exception.Response
        $status = [int]$errorResponse.StatusCode
        $algorithm = $errorResponse.Headers["X-RateLimit-Algorithm"]
        $retryAfter = $errorResponse.Headers["Retry-After"]
        $color = "Yellow"
        $extra = "retry_after=${retryAfter}s"
    }
    Write-Host ("  {0,2} -> {1} algo={2} {3}" -f $i, $status, $algorithm, $extra) -ForegroundColor $color
}

Write-Host "`nReset this client with:" -ForegroundColor Cyan
Write-Host "  Invoke-RestMethod -Method Delete -Uri `"$BaseUrl/api/admin/rate-limit/state?clientId=$ClientId`""
