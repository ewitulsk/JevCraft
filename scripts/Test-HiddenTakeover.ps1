param([int]$Port = 25575, [switch]$LiveGateway)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ProcessArguments.ps1')
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$artifact = Join-Path $root ('artifacts\hidden-takeover-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$serverDir = Join-Path $artifact 'server'; $clientDir = Join-Path $artifact 'client'
$null = New-Item -ItemType Directory -Path $serverDir,(Join-Path $clientDir 'config')
@('eula=true') | Set-Content (Join-Path $serverDir 'eula.txt')
@("server-ip=127.0.0.1","server-port=$Port",'online-mode=false','spawn-protection=0','difficulty=peaceful','level-type=minecraft:flat','generate-structures=false','view-distance=5','simulation-distance=5') | Set-Content (Join-Path $serverDir 'server.properties')
@('earlyWindowControl=false','versionCheck=false') | Set-Content (Join-Path $clientDir 'config\fml.toml')
@('fullscreen:false','pauseOnLostFocus:false','narrator:0','onboardAccessibility:false','soundCategory_master:0.0','maxFps:30','enableVsync:false','renderDistance:5','simulationDistance:5','skipMultiplayerWarning:true') | Set-Content (Join-Path $clientDir 'options.txt')

function Start-Owned([string]$task,[string[]]$properties,[string]$prefix) {
    $info=[Diagnostics.ProcessStartInfo]::new(); $info.FileName=(Join-Path $root 'gradlew.bat'); $info.WorkingDirectory=$root
    $info.UseShellExecute=$false; $info.CreateNoWindow=$true; $info.WindowStyle=[Diagnostics.ProcessWindowStyle]::Hidden
    $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true; $info.RedirectStandardInput=$true
    Set-ProcessArguments $info (@('--no-daemon',$task)+$properties)
    $process=[Diagnostics.Process]::new(); $process.StartInfo=$info; $null=$process.Start()
    return @{process=$process;stdout=$process.StandardOutput.ReadToEndAsync();stderr=$process.StandardError.ReadToEndAsync();prefix=$prefix}
}
$owned=@()
try {
    $server=Start-Owned 'runHiddenServer' @("-PjevcraftServerDir=$serverDir","-PjevcraftTestPort=$Port") 'server'; $owned+=$server
    $deadline=[DateTime]::UtcNow.AddSeconds(120); $serverLog=Join-Path $serverDir 'logs\latest.log'
    while([DateTime]::UtcNow -lt $deadline){if($server.process.HasExited){throw "server exited $($server.process.ExitCode)"};if((Test-Path $serverLog)-and((Get-Content $serverLog -Raw)-match 'Done \(')){break};Start-Sleep -Milliseconds 500}
    if(!(Test-Path $serverLog)-or((Get-Content $serverLog -Raw)-notmatch 'Done \(')){throw 'server startup timed out'}
    $clientProperties=@("-PjevcraftClientDir=$clientDir","-PjevcraftTestPort=$Port")
    if($LiveGateway){if([string]::IsNullOrWhiteSpace($env:AI_GATEWAY_API_KEY)){throw 'AI_GATEWAY_API_KEY is required for -LiveGateway'};$clientProperties+="-PjevcraftHiddenLive=true"}
    $client=Start-Owned 'runHiddenClient' $clientProperties 'client'; $owned+=$client
    if(!$client.process.WaitForExit(180000)){throw 'hidden client timed out'}
    if($client.process.ExitCode -ne 0){throw "hidden client exited $($client.process.ExitCode)"}
    $clientLog=Get-Content (Join-Path $clientDir 'logs\latest.log') -Raw; $serverText=Get-Content $serverLog -Raw
    if($clientLog -notmatch 'HIDDEN_TAKEOVER_CLIENT_PASS'){throw 'client pass evidence missing'}
    if($serverText -notmatch 'HIDDEN_TAKEOVER_SERVER_PASS'){throw 'server pass evidence missing'}
    $decisionCount=[regex]::Matches($clientLog,'TAKEOVER_DECISION provider=').Count
    if($LiveGateway -and $decisionCount -lt 1){throw 'live gateway decision evidence missing'}
    @{status='PASS';mode=$(if($LiveGateway){'live_gateway'}else{'deterministic_fixture'});liveDecisionCount=$decisionCount;artifact=$artifact;clientEvidence='HIDDEN_TAKEOVER_CLIENT_PASS';serverEvidence='HIDDEN_TAKEOVER_SERVER_PASS';finishedUtc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content (Join-Path $artifact 'result.json')
    Write-Output "HIDDEN_TAKEOVER_PASS artifact=$artifact"
} finally {
    foreach($item in $owned){
        if(!$item.process.HasExited){try{$item.process.StandardInput.WriteLine('stop');$item.process.StandardInput.Flush()}catch{};if(!$item.process.WaitForExit(10000)){$item.process.Kill($true);$item.process.WaitForExit()}}
        $item.stdout.GetAwaiter().GetResult() | Set-Content (Join-Path $artifact ($item.prefix+'-stdout.log'))
        $item.stderr.GetAwaiter().GetResult() | Set-Content (Join-Path $artifact ($item.prefix+'-stderr.log'));$item.process.Dispose()
    }
}
