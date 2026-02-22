param(
    [string]$BaseUrl = "http://localhost:25001",
    [int]$Start = 19120100,
    [int]$End = 19122100,
    [string]$Password = "123456",
    [int]$Concurrency = 20,
    [int]$TimeoutSec = 5,
    [int]$Retries = 2,
    [string]$FailedFile = "scripts/batch-register-failed.json"
)

if ($Start -gt $End) {
    throw "--Start must be <= --End"
}
if ($Concurrency -le 0) {
    throw "--Concurrency must be > 0"
}

$total = $End - $Start + 1
$done = 0
$okCount = 0
$failed = [System.Collections.Generic.List[object]]::new()
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$registerUrl = ($BaseUrl.TrimEnd('/') + "/auth/register")
$jobs = @()
$jobCommand = if (Get-Command Start-ThreadJob -ErrorAction SilentlyContinue) { "Start-ThreadJob" } else { "Start-Job" }
Write-Host "Async mode: $jobCommand"

function Handle-Result {
    param(
        [pscustomobject]$Result
    )
    $script:done++
    if ($Result.ok) {
        $script:okCount++
    } else {
        $script:failed.Add($Result)
    }
    if (($script:done % 20) -eq 0 -or $script:done -eq $total) {
        Write-Host "[$($script:done)/$total] success=$($script:okCount) failed=$($script:failed.Count)"
    }
}

for ($sid = $Start; $sid -le $End; $sid++) {
    while (($jobs | Where-Object { $_.State -eq 'Running' }).Count -ge $Concurrency) {
        $doneJob = Wait-Job -Job $jobs -Any -Timeout 2
        if ($null -ne $doneJob) {
            $result = Receive-Job -Job $doneJob
            Remove-Job -Job $doneJob -Force
            $jobs = @($jobs | Where-Object { $_.Id -ne $doneJob.Id })
            if ($null -ne $result) {
                Handle-Result -Result $result
            }
        }
    }

    $studentId = [string]$sid
    $job = & $jobCommand -ArgumentList $registerUrl, $studentId, $Password, $Retries, $TimeoutSec -ScriptBlock {
        param($Url, $Sid, $Pwd, $RetryTimes, $HttpTimeoutSec)
        $attempt = 0
        while ($attempt -le $RetryTimes) {
            $attempt++
            try {
                $body = @{
                    studentId = $Sid
                    password  = $Pwd
                } | ConvertTo-Json -Compress
                $resp = Invoke-RestMethod -Uri $Url -Method Post -ContentType "application/json" -Body $body -TimeoutSec $HttpTimeoutSec
                $code = [int]$resp.code
                $msg = [string]$resp.message
                $ok = ($code -eq 200 -or $code -eq 2042)
                return [PSCustomObject]@{
                    studentId = $Sid
                    ok        = $ok
                    code      = $code
                    message   = $msg
                    attempts  = $attempt
                }
            } catch {
                if ($attempt -gt $RetryTimes) {
                    return [PSCustomObject]@{
                        studentId = $Sid
                        ok        = $false
                        code      = 0
                        message   = $_.Exception.Message
                        attempts  = $attempt
                    }
                }
                Start-Sleep -Milliseconds (100 * $attempt)
            }
        }
    }
    $jobs = @($jobs + $job)
}

while ($jobs.Count -gt 0) {
    $doneJob = Wait-Job -Job $jobs -Any
    $result = Receive-Job -Job $doneJob
    Remove-Job -Job $doneJob -Force
    $jobs = @($jobs | Where-Object { $_.Id -ne $doneJob.Id })
    if ($null -ne $result) {
        Handle-Result -Result $result
    }
}

$sw.Stop()

Write-Host ""
Write-Host "==== Batch Register Summary ===="
Write-Host "range: $Start-$End"
Write-Host "total: $total"
Write-Host "success: $okCount"
Write-Host "failed: $($failed.Count)"
Write-Host ("time: {0:N2}s" -f $sw.Elapsed.TotalSeconds)

if ($failed.Count -gt 0) {
    $failedDir = Split-Path -Parent $FailedFile
    if (-not [string]::IsNullOrWhiteSpace($failedDir)) {
        New-Item -ItemType Directory -Path $failedDir -Force | Out-Null
    }
    $failed | ConvertTo-Json -Depth 6 | Set-Content -Path $FailedFile -Encoding UTF8
    Write-Host "failed details written to: $FailedFile"
}
