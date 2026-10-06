$baseUrl = "http://localhost:8080"
$showId = 6
$seat = "A1"
$requests = 50

$jobs = @()

Write-Host "Starting $requests concurrent reservation requests..."

for ($i = 1; $i -le $requests; $i++) {

    $userId = "user-$i"
    $idempotencyKey = "concurrency-$i"

    $jobs += Start-Job -ScriptBlock {
        param($baseUrl, $showId, $seat, $userId, $idempotencyKey)

        $body = @{
            seats = @($seat)
        } | ConvertTo-Json

        try {

            $response = Invoke-WebRequest `
                -Uri "$baseUrl/shows/$showId/reserve" `
                -Method POST `
                -Headers @{
                    "X-User-Id" = $userId
                    "Idempotency-Key" = $idempotencyKey
                } `
                -ContentType "application/json" `
                -Body $body `
                -ErrorAction Stop

            return @{
                Status = [int]$response.StatusCode
                Body = $response.Content
            }

        } catch {

            if ($_.Exception.Response) {
                return @{
                    Status = [int]$_.Exception.Response.StatusCode
                    Body = $_.ErrorDetails.Message
                }
            }

            return @{
                Status = 500
                Body = $_.Exception.Message
            }
        }

    } -ArgumentList $baseUrl, $showId, $seat, $userId, $idempotencyKey
}

$results = $jobs | ForEach-Object {
    Wait-Job $_ | Out-Null
    Receive-Job $_
    Remove-Job $_
}

$success = @($results | Where-Object { $_.Status -eq 201 }).Count
$conflicts = @($results | Where-Object { $_.Status -eq 409 }).Count
$errors = @($results | Where-Object { $_.Status -ge 500 }).Count

Write-Host ""
Write-Host "========== RESULTS =========="
Write-Host "Total requests : $requests"
Write-Host "201 Created    : $success"
Write-Host "409 Conflict   : $conflicts"
Write-Host "5xx Errors     : $errors"
Write-Host "=============================="