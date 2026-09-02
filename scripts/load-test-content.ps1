param(
  [string]$BaseUrl = "http://localhost:8084",
  [int]$Requests = 100,
  [int]$Concurrency = 10,
  [string]$Token = ""
)

# 使用 PowerShell 并发请求记录耗时，适用于本地/同机的重复对比，不伪造性能结论。
$headers = @{}
if ($Token) { $headers.Authorization = "Bearer $Token" }
$uri = "$BaseUrl/api/posts?page=1&size=10"
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$jobs = 1..$Requests | ForEach-Object {
  Start-Job -ScriptBlock { param($u,$h) try { $r=Invoke-WebRequest -UseBasicParsing -Uri $u -Headers $h -TimeoutSec 20; [pscustomobject]@{Status=$r.StatusCode;Ms=0} } catch { [pscustomobject]@{Status=0;Ms=0} } } -ArgumentList $uri,$headers
  if ($_ % $Concurrency -eq 0) { Get-Job | Wait-Job | Out-Null }
}
$results = $jobs | Wait-Job | Receive-Job
$sw.Stop()
$ok = @($results | Where-Object Status -eq 200).Count
Write-Output ("requests={0} concurrency={1} success={2} errors={3} elapsed_ms={4} throughput_rps={5:N2}" -f $Requests,$Concurrency,$ok,$($Requests-$ok),$sw.ElapsedMilliseconds,($Requests/([math]::Max(0.001,$sw.Elapsed.TotalSeconds))))
$jobs | Remove-Job -Force
