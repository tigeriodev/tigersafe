$ErrorActionPreference = 'Stop'

function ExitError {
    param([string]$msg)
    Write-Host "`n$msg" -ForegroundColor Red -BackgroundColor Black
    Read-Host "Press Enter to exit"
    exit 1
}

try {
    if ($tsUserSID -ne [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value) {
        ExitError "Invalid current Windows user (SID). `nTry reinstalling TigerSafe for Windows."
    }

    $isTsUserEnabled = (Get-LocalUser -SID "$tsUserSID").Enabled
    $checkNum = 0
    while ($isTsUserEnabled -and ($checkNum -lt 20)) {
        Write-Host "Waiting... $checkNum"
        Start-Sleep -Milliseconds 500
        $checkNum++
        $isTsUserEnabled = (Get-LocalUser -SID "$tsUserSID").Enabled
    }

    if ($isTsUserEnabled) {
        ExitError "Failed to disable current Windows user ($tsUserSID), cancelling TigerSafe launch for security reasons. `nPlease retry in some seconds. `nIf this launcher still does not work after 5 minutes, try restarting your computer and wait some minutes before retrying to launch TigerSafe. `nUltimately, try reinstalling TigerSafe for Windows."
    }

    $jreiBatProc = Start-Process -FilePath "$jreiBatLauncherPath" -PassThru
	
    Add-Type @'
#@# internal-proc-perms.cs #@#
'@

    $sddlToSet = "D:(A;;0x1fffff;;;$tsUserSID)(A;;0x1001;;;SY)"
    function SetProcessSDDL {
        param([int]$procId)
        try {
            [TigerSafeProcPerms]::SetProcessSDDL($procId, $sddlToSet)
        }
        catch {
            ExitError "Failed to change permissions of the process with id $procId`: $_."
        }
    }

    $jreiBatProcId = $jreiBatProc.Id
    Write-Host "jreiBatProcId = $jreiBatProcId"
    SetProcessSDDL $jreiBatProcId
	
    $foundJava = $false
    $i = 0
    while ($i -lt 30) {
        $childrenProc = Get-CimInstance -ClassName Win32_Process -Filter "parentprocessid = '$jreiBatProcId'"
        if ($childrenProc.Count -gt 0) {
            Write-Host "-----"
            foreach ($child in $childrenProc) {
                $childId = $child.ProcessId
                $childName = $child.Name
                Write-Host "$childId`: $childName"
                SetProcessSDDL $childId
                if ($childName -eq 'java.exe') {
                    $foundJava = $true
                }
            }
            if ($foundJava) {
                Write-Host "Found java.exe"
                break
            }
        }
        Start-Sleep -Seconds 1
        $i++
    }
    if (!$foundJava) {
        ExitError "The TigerSafe java.exe process was not found, this is suspicious and potentially dangerous. `nIf you are installing TigerSafe, or closed TigerSafe just after opening it, you can ignore this error. Otherwise, TigerSafe should be stopped and checked to see if the issue persists."
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