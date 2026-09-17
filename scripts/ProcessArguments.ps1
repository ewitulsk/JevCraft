function Set-ProcessArguments {
    param(
        [Parameter(Mandatory=$true)][Diagnostics.ProcessStartInfo]$Info,
        [Parameter(Mandatory=$true)][string[]]$Values
    )
    if (($Info.PSObject.Properties.Name -contains 'ArgumentList') -and $null -ne $Info.ArgumentList) {
        foreach ($value in $Values) { $Info.ArgumentList.Add($value) }
        return
    }
    $Info.Arguments = ($Values | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }) -join ' '
}
