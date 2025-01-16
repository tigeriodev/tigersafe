$ErrorActionPreference = 'Stop'

Write-Host 'Welcome to the logs enabling process of TigerSafe for Windows.'
Write-Host 'This script requires admin privileges to be executed.'
Write-Host 'This script can take some time, do not close it and wait for future instructions...'
Write-Host ' '

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

function WriteError {
    param([string]$msg)
    Write-Host "`n$msg" -ForegroundColor Red -BackgroundColor Black
}

function ExitError {
    param([string]$msg)
    WriteError $msg
    $ignoredOut = Read-Host "Press Enter to exit"
    exit 1
}

function AskStr {
    param([string]$msg)
    return Read-Host "`n$msg (then press Enter to validate)"
}

function RemoveJvmArg {
    param([string]$jvmArgName, [string]$initLine)

    $jvmArgPrefix = "-D$jvmArgName="
    $argStartInd = $initLine.IndexOf($jvmArgPrefix)
    if ($argStartInd -lt 0) {
        return $initLine
    }
    
    $argValStartInd = $argStartInd + $jvmArgPrefix.Length
    $argEndExclInd = 0
    if ($initLine[$argValStartInd] -eq '"') {
        $argEndExclInd = $initLine.IndexOf('"', $argValStartInd + 1) + 1
    }
    else {
        $argEndExclInd = $initLine.IndexOf(' ', $argValStartInd + 1)
        if ($argEndExclInd -lt 0) {
            $argEndExclInd = $initLine.Length
        }
    }
            
    $res = ''
    $resEndStartInd = 0
    if (($argStartInd -ge 1) -and ($initLine[$argStartInd - 1] -eq ' ')) {
        $res = $initLine.Substring(0, $argStartInd - 1)
        $resEndStartInd = $argEndExclInd
    }
    else {
        $res = $initLine.Substring(0, $argStartInd)
        $resEndStartInd = $argEndExclInd + 1
    }
    if ($resEndStartInd -lt $initLine.Length) {
        $res = $res + $initLine.Substring($resEndStartInd, $initLine.Length - $resEndStartInd)
    }
    return (RemoveJvmArg $jvmArgName $res)
}

function AddJvmArg {
    param([string]$jvmArgName, [string]$jvmVal, [string]$initLine)

    $writtenJvmVal = ''
    if ($jvmVal -ne 'true') {
        $writtenJvmVal = "`"$jvmVal`""
    }
    else {
        $writtenJvmVal = $jvmVal
    }
    $res = $initLine + " -D$jvmArgName=$writtenJvmVal"
    return $res
}

if (!$jreiBatLauncherPath) {
    ExitError "Missing TigerSafe JREI bat launcher path."
}

try {
    $jreiBinPath = Split-Path -LiteralPath "$jreiBatLauncherPath"
    if (!(Test-Path -LiteralPath "$jreiBinPath" -PathType Container)) {
        ExitError "The current installation of TigerSafe seems invalid/dangerous: $jreiBinPath does not exist."
    }

    $logsConfigPath = Join-Path "$jreiBinPath" -ChildPath 'tigersafeLogs.config'

    $logsConfigSrcPath = ''
    while ($true) {
        $logsConfigSrcPath = AskStr "Please type the path of the logs config file to clone, or nothing to create a new one"
        if (!$logsConfigSrcPath) {
            break
        }
        if (!(Test-Path -LiteralPath $logsConfigSrcPath -PathType Leaf)) {
            WriteError "The $logsConfigSrcPath file does not exist."
            continue
        }
        break
    }

    $logLevel = $null
    if (!$logsConfigSrcPath) {
        $logsLevels = @('D', 'I', 'W', 'E')
        $logsLevelsStr = '"' + ($logsLevels -join '", "') + '"'
        while ($true) {
            $logLevel = AskStr "Please type the log level (possible values: $logsLevelsStr)"
            if (!$logLevel) {
                continue
            }
            $logLevel = $logLevel.ToUpper()
            if (!($logsLevels -contains $logLevel)) {
                continue
            }
            break
        }
    }

    $answer = AskStr "Do you want to enable unsafe logs (WARNING: this is only for debugging, this should never be used to store/read real data in TigerSafe) ? Type `"y`" to enable unsafe logs, anything else not to"
    $enableUnsafe = $answer -eq 'y'

    try {
        $unlockProc = Start-Process "$pwshPath" "-ExecutionPolicy AllSigned -File `"$ps1UnlockInstallPath`"" -PassThru -Wait
        if ($unlockProc.ExitCode -ne 0) {
            ExitError "Failed to execute $ps1UnlockInstallPath, maybe because it has been deleted, or has been modified and its signature is no longer valid, or the certificate with which it was signed has been removed from trusted certificates."
        }
        else {
            Write-Host "Successfully unlocked installation of TigerSafe for Windows."
        }
    }
    catch {
        ExitError "Failed to execute $ps1UnlockInstallPath`: $_."
    }

    try {
        if (!$logsConfigSrcPath) {
            @(
                $logLevel
            ) | Set-Content -LiteralPath "$logsConfigPath" -Encoding ascii -Force -NoNewline:$false
        }
        else {
            Copy-Item "$logsConfigSrcPath" "$logsConfigPath"
        }

        $jreiBatLines = Get-Content -LiteralPath "$jreiBatLauncherPath" -Force
        if ($jreiBatLines.Count -lt 3) {
            ExitError "Failed to retrieve lines of $jreiBatLauncherPath."
        }
        $jvmArgsLine = $jreiBatLines[1]

        $logsArgName = 'tigersafe.logs'
        $unsafeLogsArgName = 'tigersafe.unsafeLoggers'

        $jvmArgsLine = RemoveJvmArg $logsArgName $jvmArgsLine
        $jvmArgsLine = AddJvmArg $logsArgName $logsConfigPath $jvmArgsLine
        
        $jvmArgsLine = RemoveJvmArg $unsafeLogsArgName $jvmArgsLine
        if ($enableUnsafe) {
            $jvmArgsLine = AddJvmArg $unsafeLogsArgName 'true' $jvmArgsLine
        }

        $jreiBatLines[1] = $jvmArgsLine
        Set-Content -LiteralPath "$jreiBatLauncherPath" -Value $jreiBatLines -Force -Encoding $batEncoding -NoNewline:$false
    }
    finally {
        try {
            $lockProc = Start-Process "$pwshPath" "-ExecutionPolicy AllSigned -File `"$ps1LockInstallPath`"" -PassThru -Wait
            if ($lockProc.ExitCode -ne 0) {
                ExitError "Failed to execute $ps1LockInstallPath, this is dangerous."
            }
            else {
                Write-Host "Successfully locked installation of TigerSafe for Windows."
            }
        }
        catch {
            ExitError "Failed to execute $ps1LockInstallPath`: $_."
        }
    }

    Write-Host "`nSuccessfully enabled logs of TigerSafe for Windows." -ForegroundColor Green -BackgroundColor Black
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