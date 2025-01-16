$ErrorActionPreference = 'Stop'

function ExitError {
    param([string]$msg)
    Write-Host "`n$msg" -ForegroundColor Red -BackgroundColor Black
    Read-Host "Press Enter to exit"
    exit 1
}

try {
    Start-Service "$gdnServName"
}
catch {
    ExitError "Failed to start $gdnServName Windows service: $_. `nTry reinstalling TigerSafe for Windows."
}

try {
    $isTsUserDisabled = !(Get-LocalUser -Name "$tsUserName").Enabled
    $checkNum = 0
    while ($isTsUserDisabled -and ($checkNum -lt 20)) {
        Write-Host "Waiting... $checkNum"
        Start-Sleep -Milliseconds 500
        $checkNum++
        $isTsUserDisabled = !(Get-LocalUser -Name "$tsUserName").Enabled
    }
  
    if ($isTsUserDisabled) {
        ExitError "$gdnServName Windows service failed to open a protected execution environment, cancelling TigerSafe launch for security reasons. `nPlease check that TigerSafe is not already open on this computer. `nIf it's not the case, please retry in some seconds. `nIf this launcher still does not work after 5 minutes, try restarting your computer and wait some minutes before retrying to launch TigerSafe. `nUltimately, try reinstalling TigerSafe for Windows."
    }

    $tsUserSecPw = ConvertTo-SecureString $tsUserPw -AsPlainText -Force
    $tsUserCred = New-Object System.Management.Automation.PSCredential ($tsUserName, $tsUserSecPw)
    $workDir = Split-Path -LiteralPath "$ps1InternalPath"
    try {
        Start-Process "$pwshPath" "-ExecutionPolicy AllSigned -File `"$ps1InternalPath`"" -Credential $tsUserCred -WorkingDirectory "$workDir"
    }
    catch {
        ExitError "Failed to launch TigerSafe: $_. `n(workDir = $workDir, the password of `"$tsUserName`" Windows user has probably been changed or the account is disabled/deleted). `nTry reinstalling TigerSafe for Windows."
    }
}
catch {
    if ($_.InvocationInfo) {
        $errLineInfo = "line $($_.InvocationInfo.ScriptLineNumber):$($_.InvocationInfo.OffsetInLine)"
    }
    else {
        $errLineInfo = "Unknown line"
    }
    ExitError "An unexpected error has occurred at [$errLineInfo]: $($_.Exception.Message)"
}