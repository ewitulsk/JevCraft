$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'ProcessArguments.ps1')
$root=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot));$artifact=Join-Path $root ('artifacts\persistence-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'));$serverDir=Join-Path $artifact 'server'
$null=New-Item -ItemType Directory -Path $serverDir;'eula=true'|Set-Content (Join-Path $serverDir 'eula.txt')
@('server-ip=127.0.0.1','server-port=0','online-mode=false','level-type=minecraft:flat','generate-structures=false','view-distance=5','simulation-distance=5')|Set-Content (Join-Path $serverDir 'server.properties')
function Run-Phase([string]$phase,[string]$marker){
  $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=(Join-Path $root 'gradlew.bat');$info.WorkingDirectory=$root;$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.WindowStyle=[Diagnostics.ProcessWindowStyle]::Hidden;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true;$info.RedirectStandardInput=$true
  Set-ProcessArguments $info @('--no-daemon','runPersistenceServer',"-PjevcraftPersistenceDir=$serverDir","-PjevcraftPersistencePhase=$phase")
  $process=[Diagnostics.Process]::new();$process.StartInfo=$info;$null=$process.Start();$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
  try{$deadline=[DateTime]::UtcNow.AddSeconds(120);$log=Join-Path $serverDir 'logs\latest.log';while([DateTime]::UtcNow -lt $deadline){if($process.HasExited){break};if((Test-Path $log)-and((Get-Content $log -Raw)-match $marker)){break};Start-Sleep -Milliseconds 500};if(!(Test-Path $log)-or((Get-Content $log -Raw)-notmatch $marker)){throw "$phase marker missing"};if(!$process.WaitForExit(30000)){throw "$phase server did not stop itself"};Copy-Item $log (Join-Path $artifact "$phase-latest.log");if($process.ExitCode-ne 0){throw "$phase server exited $($process.ExitCode)"}}
  finally{if(!$process.HasExited){$process.Kill($true);$process.WaitForExit()};$stdout.GetAwaiter().GetResult()|Set-Content (Join-Path $artifact "$phase-stdout.log");$stderr.GetAwaiter().GetResult()|Set-Content (Join-Path $artifact "$phase-stderr.log");$process.Dispose()}
}
Run-Phase 'create' 'PERSISTENCE_CREATE_READY';Run-Phase 'reload' 'PERSISTENCE_RELOAD_PASS';@{status='PASS';artifact=$artifact;finishedUtc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json|Set-Content (Join-Path $artifact 'result.json');Write-Output "PERSISTENCE_PASS artifact=$artifact"
