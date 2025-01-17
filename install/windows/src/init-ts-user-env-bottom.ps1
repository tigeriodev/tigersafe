$ErrorActionPreference = 'Stop'

try {
    $tsUserDirPath = Join-Path "$env:userprofile" -ChildPath '.tigersafe'
    if (!(Test-Path -LiteralPath "$tsUserDirPath" -PathType Container)) {
        $ignoredOut = New-Item -Path "$tsUserDirPath" -ItemType Directory -Force
    }
    Set-Content -LiteralPath "$ps1InitTsUserEnvResPath" -Value "$tsUserDirPath" -Encoding ascii -Force
    
    try {
        $shApp = New-Object -ComObject 'Shell.Application'
        $shApp.Namespace("$tsUserDirPath").Self.InvokeVerb('pintohome')
    }
    catch {
        Write-Host "Failed to pin as favorite $tsUserDirPath directory: $_." -ForegroundColor Red -BackgroundColor Black
        Read-Host "Press Enter to continue"
    }

    try { 
        $areColorsEnabled = $false
        if (!(Test-Path 'HKCU:\Console')) {
            New-Item -Path 'HKCU:' -Name 'Console'
        }
        else {
            try {
                $prop = Get-ItemProperty -Path 'HKCU:\Console' -Name 'VirtualTerminalLevel'
                if ($prop -and ($prop.VirtualTerminalLevel -eq 1)) {
                    $areColorsEnabled = $true
                } 
            }
            catch {
                # the property probably does not exist yet
            }
        }
        
        if (!$areColorsEnabled) {
            Set-ItemProperty -Path 'HKCU:\Console' -Name 'VirtualTerminalLevel' -Value 1 -Type DWord
            Write-Host "Enabled colors in Windows Terminal."
        }
        else {
            Write-Host "Colors are already enabled in Windows Terminal."
        }
    }
    catch {
        Write-Host "Failed to enable colors in Windows Terminal: $_." -ForegroundColor Red -BackgroundColor Black
        Read-Host "Press Enter to continue"
    }
    exit 0
}
catch {
    if ($_.InvocationInfo) {
        $errLineInfo = "line $($_.InvocationInfo.ScriptLineNumber):$($_.InvocationInfo.OffsetInLine)"
    }
    else {
        $errLineInfo = "Unknown line"
    }
    Write-Host "`nAn unexpected error has occurred at [$errLineInfo]: $($_.Exception.Message)" -ForegroundColor Red -BackgroundColor Black
    Read-Host "Press Enter to exit"
    exit 1
}