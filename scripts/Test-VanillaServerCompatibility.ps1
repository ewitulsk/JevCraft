param([int]$Port = 25576)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$artifact = Join-Path $root ('artifacts\vanilla-compat-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$serverDir = Join-Path $artifact 'vanilla-server'
$clientDir = Join-Path $artifact 'client-only'
$null = New-Item -ItemType Directory -Path $serverDir,(Join-Path $clientDir 'config'),(Join-Path $clientDir 'mods')

function Resolve-OfficialServer {
    $neoformCache = Join-Path $env:USERPROFILE '.gradle\caches\neoformruntime\artifacts\minecraft_1.21.1_server.jar'
    $manifest = Invoke-RestMethod 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
    $version = $manifest.versions | Where-Object id -eq '1.21.1' | Select-Object -First 1
    if (!$version) { throw 'Mojang manifest does not list Minecraft 1.21.1' }
    $details = Invoke-RestMethod $version.url
    $expected = $details.downloads.server.sha1.ToLowerInvariant()
    $cacheDir = Join-Path $root '.gradle\jevcraft'
    $download = Join-Path $cacheDir 'minecraft-server-1.21.1.jar'
    $candidate = if (Test-Path $neoformCache) { $neoformCache } else { $download }
    if (!(Test-Path $candidate) -or (Get-FileHash -Algorithm SHA1 $candidate).Hash.ToLowerInvariant() -ne $expected) {
        $null = New-Item -ItemType Directory -Path $cacheDir -Force
        Invoke-WebRequest $details.downloads.server.url -OutFile $download
        $candidate = $download
    }
    $actual = (Get-FileHash -Algorithm SHA1 $candidate).Hash.ToLowerInvariant()
    if ($actual -ne $expected) { throw "official server SHA-1 mismatch: expected $expected, got $actual" }
    return @{Path=$candidate;Sha1=$actual;Url=$details.downloads.server.url}
}

function Start-Owned([string]$file,[string[]]$arguments,[string]$workingDirectory) {
    $info=[Diagnostics.ProcessStartInfo]::new(); $info.FileName=$file; $info.WorkingDirectory=$workingDirectory
    $info.UseShellExecute=$false; $info.CreateNoWindow=$true; $info.WindowStyle=[Diagnostics.ProcessWindowStyle]::Hidden
    $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true; $info.RedirectStandardInput=$true
    foreach($argument in $arguments){$info.ArgumentList.Add($argument)}
    $process=[Diagnostics.Process]::new(); $process.StartInfo=$info; $null=$process.Start()
    return @{process=$process;stdout=$process.StandardOutput.ReadToEndAsync();stderr=$process.StandardError.ReadToEndAsync()}
}

$official = Resolve-OfficialServer
@('eula=true') | Set-Content (Join-Path $serverDir 'eula.txt')
@("server-ip=127.0.0.1","server-port=$Port",'online-mode=false','enforce-secure-profile=false','spawn-protection=0','difficulty=peaceful','level-type=minecraft:flat','generate-structures=false','view-distance=5','simulation-distance=5') | Set-Content (Join-Path $serverDir 'server.properties')
@('earlyWindowControl=false','versionCheck=false') | Set-Content (Join-Path $clientDir 'config\fml.toml')
@('fullscreen:false','pauseOnLostFocus:false','narrator:0','onboardAccessibility:false','soundCategory_master:0.0','maxFps:30','enableVsync:false','renderDistance:5','simulationDistance:5','skipMultiplayerWarning:true') | Set-Content (Join-Path $clientDir 'options.txt')

& (Join-Path $root 'gradlew.bat') --no-daemon clientOnlyJar | Out-Null
if($LASTEXITCODE -ne 0){throw 'client-only jar build failed'}
$clientJar=(Get-ChildItem -LiteralPath (Join-Path $root 'build\libs') -File -Filter 'jevcraft-client-*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
if(!$clientJar){throw 'built client-only jar was not found'}
$entries=& jar tf $clientJar
$forbidden=$entries | Where-Object {$_ -eq 'dev/jevcraft/JevCraft.class' -or $_ -like 'dev/jevcraft/companion/*' -or $_ -like 'dev/jevcraft/testing/*' -or $_ -like 'dev/jevcraft/mixin/*'}
if($forbidden){throw "client-only jar contains forbidden full/test classes: $($forbidden -join ', ')"}
if($entries -notcontains 'dev/jevcraft/client/JevCraftClient.class'){throw 'client-only entry point missing from jar'}
Copy-Item -LiteralPath $clientJar -Destination (Join-Path $clientDir 'mods\jevcraft-client.jar')

$server=$null; $client=$null
try {
    $server=Start-Owned 'java' @('-Xmx1G','-jar',$official.Path,'nogui') $serverDir
    $deadline=[DateTime]::UtcNow.AddSeconds(120); $serverLog=Join-Path $serverDir 'logs\latest.log'
    while([DateTime]::UtcNow -lt $deadline){if($server.process.HasExited){throw "vanilla server exited $($server.process.ExitCode)"};if((Test-Path $serverLog)-and((Get-Content $serverLog -Raw)-match 'Done \(')){break};Start-Sleep -Milliseconds 500}
    if(!(Test-Path $serverLog)-or((Get-Content $serverLog -Raw)-notmatch 'Done \(')){throw 'vanilla server startup timed out'}
    $client=Start-Owned (Join-Path $root 'gradlew.bat') @('--no-daemon','runHiddenCompatClient',"-PjevcraftClientDir=$clientDir","-PjevcraftTestPort=$Port") $root
    if(!$client.process.WaitForExit(180000)){throw 'client-only compatibility client timed out'}
    if($client.process.ExitCode -ne 0){throw "client-only compatibility client exited $($client.process.ExitCode)"}
    $clientLog=Get-Content (Join-Path $clientDir 'logs\latest.log') -Raw
    if($clientLog -notmatch 'VANILLA_COMPAT_CLIENT_PASS'){throw 'client compatibility pass evidence missing'}
    if($clientLog -notmatch 'JevCraft Client'){throw 'client-only mod loading evidence missing'}
    if($clientLog -match 'JevCraft initialized; credentials'){throw 'full JevCraft mod was unexpectedly loaded'}
    $serverText=Get-Content $serverLog -Raw
    if($serverText -notmatch 'logged in with entity id'){throw 'official server did not record a successful player login'}
    $result=[ordered]@{status='PASS';minecraft='1.21.1';serverDistribution='official_mojang';serverSha1=$official.Sha1;clientJarSha256=(Get-FileHash -Algorithm SHA256 $clientJar).Hash.ToLowerInvariant();clientMod='jevcraft_client';fullModLoaded=$false;serverLogin=$true;clientEvidence='VANILLA_COMPAT_CLIENT_PASS';artifact=$artifact;finishedUtc=[DateTime]::UtcNow.ToString('o')}
    $result | ConvertTo-Json | Set-Content (Join-Path $artifact 'result.json')
    Write-Output "VANILLA_COMPAT_PASS artifact=$artifact"
} finally {
    if($client){
        if(!$client.process.HasExited){$client.process.Kill($true);$client.process.WaitForExit()}
        $client.stdout.GetAwaiter().GetResult() | Set-Content (Join-Path $artifact 'client-stdout.log')
        $client.stderr.GetAwaiter().GetResult() | Set-Content (Join-Path $artifact 'client-stderr.log')
        $client.process.Dispose()
    }
    if($server){
        if(!$server.process.HasExited){try{$server.process.StandardInput.WriteLine('stop');$server.process.StandardInput.Flush()}catch{};if(!$server.process.WaitForExit(15000)){$server.process.Kill($true);$server.process.WaitForExit()}}
        $server.stdout.GetAwaiter().GetResult() | Set-Content (Join-Path $artifact 'server-stdout.log')
        $server.stderr.GetAwaiter().GetResult() | Set-Content (Join-Path $artifact 'server-stderr.log')
        $server.process.Dispose()
    }
}
