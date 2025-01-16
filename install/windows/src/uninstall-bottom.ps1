$ErrorActionPreference = 'Stop'

Write-Host 'Welcome to the uninstallation process of TigerSafe for Windows.'
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

function CheckLastProgramOutput {
    param($output, [string]$errContext)
    if ($LastExitCode) {
        ExitError "$errContext`: $output"
    }
    else {
        Write-Verbose "$output"
    }
}

function AskDeleteCert {
    param([string]$certPath)
    $answer = AskStr "Do you want to delete this certificate ($certPath) ? (if this certificate was previously installed by TigerSafe, you should delete it) ? Type `"y`" to delete it, anything else to keep it"
    if ($answer -eq 'y') {
        DeleteCert "$certPath"
    }
    else {
        Write-Host "`nKept $certPath certificate."
    }
}

function DeleteCert {
    param([string]$certPath)
    Remove-Item -LiteralPath $certPath -DeleteKey -Force
    if (Test-Path -LiteralPath $certPath -PathType Leaf) {
        ExitError "Failed to delete $certPath certificate."
    }
    else {
        Write-Host "`nDeleted $certPath certificate."
    }
}

function GetUser {
    param([string]$userName)
    try {
        return Get-LocalUser -Name "$userName"
    }
    catch {
        return $null
    }
}

if (!$installPath) {
    ExitError "Missing TigerSafe installation path."
}

$confirm = Read-Host "`nYou are about to uninstall TigerSafe. Please type `"y`" to uninstall, or anything else to cancel (then press Enter to validate)"
if ($confirm -ne 'y') {
    ExitError "Cancelled uninstallation of TigerSafe."
}

try {
    if (Test-Path -LiteralPath "$installPath" -PathType Container) {
        # NB: try unlock here, before removing trusted certificates
        try {
            $unlockProc = Start-Process powershell.exe "-ExecutionPolicy AllSigned -File `"$ps1UnlockInstallPath`"" -PassThru -Wait
            if ($unlockProc.ExitCode -ne 0) {
                throw "Failed to execute $ps1UnlockInstallPath, maybe because it has been deleted, or has been modified and its signature is no longer valid, or the certificate with which it was signed has been removed from trusted certificates."
            }
            else {
                Write-Host "Successfully unlocked installation of TigerSafe for Windows."
            }
        }
        catch {
            WriteError "Failed to unlock installation of TigerSafe for Windows: $_. This will probably prevent to delete $installPath directory."
            $answer = AskStr "Do you want to trust and execute $ps1UnlockInstallPath without checking its signature (WARNING: this is dangerous) ? Type `"y`" to execute it without checking its signature, anything else to continue without unlocking TigerSafe installation for Windows"
            if ($answer -eq 'y') {
                try {
                    $unlockProc2 = Start-Process powershell.exe "-ExecutionPolicy Bypass -File `"$ps1UnlockInstallPath`"" -PassThru -Wait
                    if ($unlockProc2.ExitCode -ne 0) {
                        WriteError "Failed again to unlock installation of TigerSafe for Windows: $_. This will probably prevent to delete $installPath directory."
                    }
                    else {
                        Write-Host "`nSuccessfully unlocked installation of TigerSafe for Windows."
                    }
                }
                catch {
                    WriteError "Unexpectedly failed to unlock installation of TigerSafe for Windows: $_. This will probably prevent to delete $installPath directory."
                }
            }
        }
    }

    if ($certSubject -and ($certsStores.Count -gt 0) -and $certThumb) {
        foreach ($certsStore in $certsStores) {
            $oldCerts = Get-ChildItem -LiteralPath $certsStore -Force | Where-Object { ($_.Thumbprint -eq $certThumb) -or ($_.Subject -eq $certSubject) }
            foreach ($oldCert in $oldCerts) {
                $oldCertPath = "$($oldCert.PSPath)"
                if ($oldCert.Thumbprint -eq $certThumb) {
                    if ($oldCert.Subject -eq $certSubject) {
                        DeleteCert $oldCertPath
                    }
                    else {
                        Write-Host "`nYou have a certificate with the same thumbprint as the one of the certificate created during TigerSafe installation ($certThumb), but with a different subject: `"$($oldCert.Subject)`" in your `"$certsStore`": $oldCert"
                        AskDeleteCert $oldCertPath
                    }
                }
                else {
                    Write-Host "`nYou have a certificate with the subject: `"$certSubject`" in your `"$certsStore`": $oldCert"
                    AskDeleteCert $oldCertPath
                }
            }
        }
    }
    else {
        Write-Host "No TigerSafe certificates information, they have probably not been generated."
    }

    if ($gdnServName) {
        $isGdnServInstalled = $false
        try {
            $ignoredOut = Get-Service $gdnServName
            $isGdnServInstalled = $true
        }
        catch {
            Write-Host "$gdnServName Windows service is not installed."
        }
        if ($isGdnServInstalled) {
            Stop-Service $gdnServName
            $lastOut = sc.exe delete "$gdnServName"
            CheckLastProgramOutput $lastOut "Failed to delete $gdnServName Windows service"
            Write-Host "Deleted $gdnServName Windows service."
        }
        foreach ($gdnFilePath in ($ps1GdnPath, $exeGdnPath)) {
            if ($gdnFilePath -and (Test-Path -LiteralPath $gdnFilePath -PathType Leaf)) {
                if ($isGdnServInstalled -or ((AskStr "Do you want to delete $gdnFilePath file ? Type `"y`" to delete it, anything else to keep it") -eq 'y')) {
                    $ignoredOut = takeown '/F' "`"$gdnFilePath`"" '/A'
                    $lastOut = icacls "`"$gdnFilePath`"" '/grant' '*S-1-5-32-544:F'
                    CheckLastProgramOutput $lastOut "Failed to set full-control permission to Administrators on $gdnFilePath"
    
                    Remove-Item -LiteralPath $gdnFilePath -Force
                    if (Test-Path -LiteralPath $gdnFilePath -PathType Leaf) {
                        ExitError "Failed to delete $gdnFilePath file. This can be caused by your antivirus."
                    }
                    else {
                        Write-Host "Deleted $gdnFilePath file."
                    }
                }
            }
        }
    }
    else {
        Write-Host "No TigerSafeGuardian Windows service information, it has probably not been created."
    }

    $tsUser = GetUser "$tsUserName"
    if ($tsUser) {
        $answer = AskStr "Do you want to delete the Windows user named `"$tsUserName`" that was created for TigerSafe (be careful, if this user owns data like files, they could be lost; if you are reinstalling/updating TigerSafe, you can keep this user and it will be reused during installation) ? Type `"y`" to delete this user, anything else to keep it"
        if ($answer -eq 'y') {
            $tsUser | Remove-LocalUser
            $tsUser = GetUser "$tsUserName"
            if ($tsUser) {
                ExitError "Failed to delete $tsUserName Windows user."
            }
            else {
                Write-Host "`nDeleted $tsUserName Windows user."
            }
        }
        else {
            Write-Host "`nKept $tsUserName Windows user."
        }
    }
    else {
        Write-Host "$tsUserName Windows user does not exist."
    }

    if (Test-Path -LiteralPath "$installPath" -PathType Container) {
        Remove-Item -LiteralPath "$installPath" -Recurse -Force
        if (Test-Path -LiteralPath "$installPath" -PathType Container) {
            ExitError "Failed to delete $installPath directory."
        }
        Write-Host "Deleted $installPath directory."
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

Write-Host "`nSuccessfully finished uninstallation of TigerSafe for Windows." -ForegroundColor Green -BackgroundColor Black

Read-Host "Press Enter to exit"