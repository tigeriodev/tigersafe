$ErrorActionPreference = 'Stop'

Write-Host 'Welcome to the installation locking process of TigerSafe for Windows.'
Write-Host 'This script requires admin privileges to be executed.'
Write-Host 'This script can take some time, do not close it and wait for future instructions...'

if (!([System.Security.Principal.WindowsPrincipal] [System.Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([System.Security.Principal.WindowsBuiltInRole]::Administrator)) {
    if ($AsAdmin) {
        Read-Host "Failed to get admin privileges for executing this script, aborted the execution."
        exit 1
    }
    else {
        Read-Host "Press Enter to continue"
        Start-Process powershell.exe "-ExecutionPolicy Bypass -File `"$PSCommandPath`" -AsAdmin" -Verb RunAs
        exit 0
    }
}

function ExitError {
    param([string]$msg)
    Write-Host "`n$msg" -ForegroundColor Red -BackgroundColor Black
    Read-Host "Press Enter to exit"
    exit 1
}

function CheckLastProgramOutput {
    param($output, [string]$errContext)
    if ($LastExitCode) {
        ExitError "$errContext`: $output"
    }
    else {
        Write-Verbose "$output"
    }
}

if (!$installPath) {
    ExitError "Missing TigerSafe installation path."
}

if (!$jreiBatLauncherPath) {
    ExitError "Missing TigerSafe JREI bat launcher path."
}

try {
    $ignoredOut = takeown '/F' "`"$installPath`"" '/A' '/R' '/SKIPSL'
    $attribInstallPath = Join-Path "$installPath" -ChildPath '*.*'
    $lastOut = attrib '+R' "`"$attribInstallPath`"" '/S' '/D' '/L'
    CheckLastProgramOutput $lastOut "Failed to set read-only on $attribInstallPath"
    if (!(Get-Item -LiteralPath "$jreiBatLauncherPath").IsReadOnly) {
        ExitError "Failed to set read-only on $jreiBatLauncherPath and probably on $attribInstallPath."
    }

    $lastOut = icacls "`"$installPath`"" '/grant:r' '*S-1-5-32-545:(R,X)' '/inheritancelevel:r' '/T' '/L' '/C'
    CheckLastProgramOutput $lastOut "Failed to set permissions of Users on $installPath"
    $lastOut = icacls "`"$installPath`"" '/grant:r' '*S-1-5-18:(R,X)' '/inheritancelevel:r' '/T' '/L' '/C'
    CheckLastProgramOutput $lastOut "Failed to set permissions of SYSTEM on $installPath"
    $lastOut = icacls "`"$installPath`"" '/grant:r' '*S-1-5-32-544:(R,X)' '/inheritancelevel:r' '/T' '/L' '/C'
    CheckLastProgramOutput $lastOut "Failed to set permissions of Administrators on $installPath"


    Write-Host "`nSuccessfully locked installation of TigerSafe for Windows." -ForegroundColor Green -BackgroundColor Black
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