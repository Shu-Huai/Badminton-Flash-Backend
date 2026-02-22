param(
    [string]$BaseUrl = "http://localhost:25001",
    [int]$Start = 19120100,
    [int]$End = 19122100,
    [string]$Password = "123456",
    [int]$Concurrency = 20,
    [int]$TimeoutSec = 5,
    [int]$Retries = 2,
    [string]$OutputFile = "scripts/perf/tokens.json",
    [string]$FailedFile = "scripts/perf/fetch-tokens-failed.json"
)

if ($Start -gt $End) {
    throw "--Start must be <= --End"
}
if ($Concurrency -le 0) {
    throw "--Concurrency must be > 0"
}

$loginUrl = ($BaseUrl.TrimEnd('/') + "/auth/login")
$total = $End - $Start + 1
$done = 0
$okCount = 0
$failed = [System.Collections.Generic.List[object]]::new()
$tokens = [System.Collections.Generic.List[object]]::new()
$sw = [System.Diagnostics.Stopwatch]::StartNew()
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
        $script:tokens.Add([PSCustomObject]@{
            studentId = $Result.studentId
            token     = $Result.token
        })
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
    $job = & $jobCommand -ArgumentList $loginUrl, $studentId, $Password, $Retries, $TimeoutSec -ScriptBlock {
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
                $token = ""
                if ($null -ne $resp.data -and $null -ne $resp.data.token) {
                    $token = [string]$resp.data.token
                }
                $ok = ($code -eq 200 -and -not [string]::IsNullOrWhiteSpace($token))
                return [PSCustomObject]@{
                    studentId = $Sid
                    ok        = $ok
                    code      = $code
                    message   = $msg
                    token     = $token
                    attempts  = $attempt
                }
            } catch {
                if ($attempt -gt $RetryTimes) {
                    return [PSCustomObject]@{
                        studentId = $Sid
                        ok        = $false
                        code      = 0
                        message   = $_.Exception.Message
                        token     = ""
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

$tokensSorted = $tokens | Sort-Object { [int]$_.studentId }
$outputDir = Split-Path -Parent $OutputFile
if (-not [string]::IsNullOrWhiteSpace($outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# Append mode: merge with existing tokens file (if present), then upsert by studentId.
$existingTokens = @()
if (Test-Path $OutputFile) {
    try {
        $existingRaw = [System.IO.File]::ReadAllText((Resolve-Path $OutputFile))
        if (-not [string]::IsNullOrWhiteSpace($existingRaw)) {
            $existingRaw = $existingRaw -replace "^\uFEFF", ""
            $parsed = $existingRaw | ConvertFrom-Json
            if ($parsed -is [System.Array]) {
                $existingTokens = @($parsed)
            } elseif ($null -ne $parsed) {
                $existingTokens = @($parsed)
            }
        }
    } catch {
        Write-Warning "failed to parse existing tokens file, will overwrite with new merged content: $OutputFile"
    }
}

$mergedMap = @{}
foreach ($t in $existingTokens) {
    if ($null -ne $t -and -not [string]::IsNullOrWhiteSpace([string]$t.studentId) -and -not [string]::IsNullOrWhiteSpace([string]$t.token)) {
        $mergedMap[[string]$t.studentId] = [PSCustomObject]@{
            studentId = [string]$t.studentId
            token = [string]$t.token
        }
    }
}
foreach ($t in $tokensSorted) {
    $mergedMap[[string]$t.studentId] = [PSCustomObject]@{
        studentId = [string]$t.studentId
        token = [string]$t.token
    }
}
$mergedTokens = $mergedMap.Values | Sort-Object { [int]$_.studentId }
$tokensJson = $mergedTokens | ConvertTo-Json -Depth 6
[System.IO.File]::WriteAllText($OutputFile, $tokensJson, $utf8NoBom)

Write-Host ""
Write-Host "==== Fetch Tokens Summary ===="
Write-Host "range: $Start-$End"
Write-Host "total: $total"
Write-Host "success: $okCount"
Write-Host "failed: $($failed.Count)"
Write-Host ("time: {0:N2}s" -f $sw.Elapsed.TotalSeconds)
Write-Host "tokens file: $OutputFile"
Write-Host "tokens merged total: $($mergedTokens.Count)"

if ($failed.Count -gt 0) {
    $failedDir = Split-Path -Parent $FailedFile
    if (-not [string]::IsNullOrWhiteSpace($failedDir)) {
        New-Item -ItemType Directory -Path $failedDir -Force | Out-Null
    }
    $failedJson = $failed | ConvertTo-Json -Depth 6
    [System.IO.File]::WriteAllText($FailedFile, $failedJson, $utf8NoBom)
    Write-Host "failed details written to: $FailedFile"
}
