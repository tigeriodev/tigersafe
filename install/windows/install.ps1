[CmdletBinding()]
param([switch]$AsAdmin, [string]$WorkDir)

#Requires -Version 5.1

$ErrorActionPreference = 'Stop'

Write-Host 'Welcome to the installation process of TigerSafe for Windows.'
Write-Host 'This script requires admin privileges to be executed (but after installation, TigerSafe can be executed without admin privileges).'
Write-Host 'This script can take some time, do not close it and wait for future instructions...'

if (!([System.Security.Principal.WindowsPrincipal] [System.Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([System.Security.Principal.WindowsBuiltInRole]::Administrator)) {
    if ($AsAdmin) {
        Read-Host "Failed to get admin privileges for executing this script, aborted the execution."
        exit 1
    }
    else {
        Read-Host "Press Enter to continue"
        Start-Process powershell.exe "-ExecutionPolicy Bypass -File `"$PSCommandPath`" -AsAdmin -WorkDir `"$((Get-Location).Path)`"" -Verb RunAs
        exit 0
    }
}

$ps1Encoding = 'ascii'
$batEncoding = 'ascii'

$pwMaxLenLimit = 20
$basicPws = @('abAB12##', 'abAB12**', 'abAB12', 'abAB', 'ab', '12', 'abAB12##cdCD', 'abAB12##cdCD34')
$validBasicPw = $null

$certSubject = 'CN=TigerSafe Win Signing'
$certsStores = @('Cert:\LocalMachine\Root', 'Cert:\LocalMachine\TrustedPublisher')

$monitoringPeriodMs = 3000
$monitoringMinProcessesNum = 3
$monitoringMaxProcessesNum = 5

$exitingError = $false

function WriteError {
    param([string]$msg)
    Write-Host "`n$msg" -ForegroundColor Red -BackgroundColor Black
}

function ExitError {
    param([string]$msg)
    $global:exitingError = $true
    WriteError $msg
    $ignoredOut = Read-Host "Please do not close this script now, and press Enter to continue"
    exit 1
}

function AskStr {
    param([string]$msg)
    return Read-Host "`n$msg (then press Enter to validate)"
}

function ProcessUnexpectedError {
    param($ex)
    if ($ex.InvocationInfo) {
        $errLineInfo = "line $($ex.InvocationInfo.ScriptLineNumber):$($ex.InvocationInfo.OffsetInLine)"
    }
    else {
        $errLineInfo = "Unknown line"
    }
    ExitError "An unexpected error has occurred at [$errLineInfo]: $($ex.Exception.Message)."
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

function AskCmdPath {
    param([string]$cmd, [string]$displayName, [string]$notFoundTips)
    $res = ''
    try {
        $res = (Get-Command $cmd).Source
        $answer = AskStr "Please confirm that the $displayName path is `"$res`" by typing `"y`", or anything else to choose another path"
        if ($answer -ne 'y') {
            $res = ''
        }
    }
    catch {
        WriteError "Failed to find the path to $displayName. $notFoundTips"
    }

    if (!$res) {
        while ($true) {
            $res = AskStr "Please type the path to $displayName installed on this computer"
            if (!$res) {
                continue
            }
            if (!(Test-Path -LiteralPath "$res" -PathType Leaf)) {
                WriteError "The `"$res`" file does not exist."
                continue
            }
            break
        }
    }
    return $res
}

function CheckAvailableDirPath {
    param([string]$path)
    if (Test-Path -LiteralPath "$path" -PathType Container) {
        WriteError "The path `"$path`" is already used by a directory."
        return $false
    }
    if (!(Test-Path -LiteralPath "$path" -IsValid)) {
        WriteError "The path `"$path`" is invalid."
        return $false
    }
    return $true
}

function CheckAvailableFilePath {
    param([string]$path)
    if (Test-Path -LiteralPath "$path" -PathType Leaf) {
        WriteError "The path `"$path`" is already used by a file."
        return $false
    }
    if (!(Test-Path -LiteralPath "$path" -IsValid)) {
        WriteError "The path `"$path`" is invalid."
        return $false
    }
    return $true
}

function GetUser {
    param([string]$name)
    try {
        $users = Get-LocalUser -Name "$name"
        if ($users.Count -eq 1) {
            return $users[0]
        }
        else {
            return $null
        }
    }
    catch {
        return $null
    }
}

function TryCreateUser {
    param([string]$name)
    if (!$name) {
        WriteError "A Windows user cannot have an empty name."
        return $null
    }
    if (GetUser "$name") {
        WriteError "A Windows user named `"$name`" already exists."
        return $null
    }
    $res = $null
    $pwInd = 0
    $basicPwsNum = $basicPws.Count
    for (; $pwInd -lt $basicPwsNum; $pwInd++) {
        try {
            $res = New-LocalUser -Name "$name" -Password (ConvertTo-SecureString $basicPws[$pwInd] -AsPlainText -Force) -PasswordNeverExpires -AccountNeverExpires -UserMayNotChangePassword
            $res = GetUser "$name"
            if ($res) {
                $global:validBasicPw = $basicPws[$pwInd]
                Write-Host "`nSuccessfully created $name Windows user."
                return $res
            }
            else {
                WriteError "Failed to create $name Windows user with basic password number $pwInd."
            }
        }
        catch {
            WriteError "Failed to create $name Windows user with basic password number $pwInd`: $_"
        }
    }

    WriteError "Failed to create $name Windows user with all basic passwords."

    while ($true) {
        $secPw = Read-Host "Please type a valid Windows user password that is as short as possible, or nothing to stop trying to create a Windows user named `"$name`" (then press Enter to validate)" -AsSecureString
        if (!$secPw -or ($secPw.Length -le 0)) {
            break
        }
        try {
            $res = New-LocalUser -Name "$name" -Password $secPw -PasswordNeverExpires -AccountNeverExpires -UserMayNotChangePassword
            $res = GetUser "$name"
            if ($res) {
                $userCred = New-Object System.Management.Automation.PSCredential ($name, $secPw)
                $global:validBasicPw = $userCred.GetNetworkCredential().Password
                Write-Host "`nSuccessfully created $name Windows user."
                return $res
            }
            else {
                WriteError "Failed to create $name Windows user with the provided password."
            }
        }
        catch {
            WriteError "Failed to create $name Windows user with the provided password: $_"
        }
    }
    return $res
}

function TrySetUserPw {
    param($user, [string]$name, [string]$pw)
    try {
        Write-Verbose "Try to set password of $name to `"$pw`"..."
        $secPw = ConvertTo-SecureString $pw -AsPlainText -Force
        $user | Set-LocalUser -Password $secPw

        $userCred = New-Object System.Management.Automation.PSCredential ($name, $secPw)
        $proc = Start-Process "$pwshPath" "-ExecutionPolicy Bypass -WindowStyle Hidden -c `"exit 0`"" -Credential $userCred -WorkingDirectory "$anyUserAccessibleDirPath" -PassThru -Wait
        if ($proc.ExitCode -eq 0) {
            return $true
        }
    }
    catch {
        WriteError "Failed to change the password of $name Windows user: $_"
    }
    return $false
}

function NewPw {
    $randBytes = New-Object byte[] $pwMaxLen
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($randBytes)

    $sb = New-Object System.Text.StringBuilder
    $randBytesLen = $randBytes.Count
    $pwAllowedCharsLen = $pwAllowedChars.Length
    $i = 0
    for (; $i -lt $randBytesLen; $i++) {
        [void]$sb.Append($pwAllowedChars[$randBytes[$i] % $pwAllowedCharsLen])
    }
    $res = $sb.ToString()
    return $res
}

function NewPs1StrVarsLines {
    param([string[]]$strVarsName)
    return @(
        $strVarsName | ForEach-Object {
            "`$$_ = '$(Get-Variable $_ -ValueOnly)'"
        }
    )
}

function GeneratePs1 {
    param([string]$finalPath, [string]$bottomPath, [string[]]$topLines)
    Set-Content -LiteralPath "$finalPath" -Value $topLines -Encoding $ps1Encoding -Force -NoNewline:$false
    Get-Content -LiteralPath "$bottomPath" -Force | Add-Content -LiteralPath "$finalPath"
}

function SetReadOnly {
    param([string]$path)
    try {
        Set-ItemProperty -LiteralPath "$path" -Name IsReadOnly -Value $true -Force
    }
    catch {
        WriteError "Failed to set $path as read-only: $_."
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

Write-Host ' '
Write-Host @'
TigerSafe
Copyright (c) 2024-2025 tigeriodev (tigeriodev@tutamail.com)

This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License version 2 only, as
published by the Free Software Foundation.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
version 2 for more details (a copy is included in the LICENSE file that
accompanied this code).

You should have received a copy of the GNU General Public License version
2 along with this program; if not, write to the Free Software Foundation,
Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
'@

Write-Host "`nLegal information is provided in the NOTICE and LICENSE ASCII files in the root directory of the TigerSafe project."
Write-Host "`nWARNING: This script will install and modify several things on this computer. You should fully understand what this script and all the things that it will install do before executing them. This script and all the things that it will install are provided absolutely without any warranty, including, but not limited to, any warranty of usefulness, or that they will not break this computer or damage certain aspects of it."
Write-Host "`nWARNING: If you provide custom paths (of files or directories) during this installation process, in order to avoid problems, those paths should contain only latin letters (a to Z) and basic symbols (no accents, no chinese or russian characters...)."
Write-Host "`nWARNING: You should not close this script manually, even if you encounter an error. It should automatically close by itself when it has finished."

$answer = Read-Host "`nIf you understand and accept the terms and conditions of this program, type `"y`" then press Enter to continue, otherwise stop this script by closing the current window"
if ($answer -ne 'y') {
    ExitError "You did not confirm that you understand and accept the terms and conditions of this program. The installation of TigerSafe for Windows has been cancelled."
}

if ($WorkDir) {
    try {
        Set-Location -LiteralPath "$WorkDir"
    }
    catch {
        WriteError "Failed to move current working directory location to `"$WorkDir`": $_."
    }
}

$projectRootPath = ''
try {
    $projectRootPath = Join-Path '..' -ChildPath '..' -Resolve
}
catch {
    WriteError "Failed to find the TigerSafe project root directory: $_. This script is probably wrongly placed."
}

$winSrcPath = ''
$firstTry = $true;
while ($true) {
    if (!$firstTry -or !$projectRootPath) {
        $projectRootPath = AskStr "Please type the path of the TigerSafe project root directory (which should contain the install\windows directory where this script is)"
    }
    if ($firstTry) {
        $firstTry = $false
    }
    if (!$projectRootPath) {
        continue
    }
    
    try {
        $winSrcPath = Join-Path (Join-Path (Join-Path "$projectRootPath" -ChildPath 'install') -ChildPath 'windows') -ChildPath 'src'
        if (!(Test-Path -LiteralPath "$winSrcPath" -PathType Container)) {
            WriteError "The $winSrcPath directory, which should exist, does not exist."
            continue
        }
    }
    catch {
        WriteError "Failed to process this path: $_."
        continue
    }
    break
}

$uiPath = Join-Path "$projectRootPath" -ChildPath 'tigersafe-ui'
$uiPomPath = Join-Path "$uiPath" -ChildPath 'pom.xml'
$jreiSrcPath = Join-Path (Join-Path "$uiPath" -ChildPath 'target') -ChildPath 'tigersafeJREI'

try {
    if (!(Test-Path -LiteralPath "$projectRootPath" -PathType Container)) {
        ExitError "Failed to find the TigerSafe project root directory at `"$projectRootPath`", this script is probably wrongly placed."
    }

    $buildJREI = $true
    if (Test-Path -LiteralPath "$jreiSrcPath" -PathType Container) {
        $answer = AskStr "Do you want to rebuild TigerSafe JREI ? Type `"n`" to keep the already built TigerSafe JREI, anything else to rebuild it"
        $buildJREI = $answer -ne 'n'
    }

    if ($buildJREI) {
        Push-Location -LiteralPath "$projectRootPath"

        if (!(Test-Path -LiteralPath "$uiPomPath" -PathType Leaf)) {
            ExitError "Failed to find the pom.xml file of tigersafe-ui at `"$uiPomPath`"."
        }

        $mvnPath = AskCmdPath 'mvn' 'Maven command file "mvn.cmd"' 'Is it correctly installed and is its "bin" directory referenced in the "Path" System (not current user) environment variable of this computer ?'

        Write-Host "Using Maven at $mvnPath."

        Write-Host "`nCompiling TigerSafe components...`n"

        & "$mvnPath" 'clean' 'install' '-DskipTests'
        if ($LastExitCode) {
            ExitError "Failed to compile TigerSafe components. Please check that your Maven installed version and JDK (Java Development Kit) installed version are supported by TigerSafe current version, and that your computer is correctly connected to the internet (Maven needs to download some components)."
        }

        Write-Host "`nBuilding TigerSafe JREI...`n"

        & "$mvnPath" '-f' "$uiPomPath" 'clean' '-Pjrei'
        if ($LastExitCode) {
            ExitError "Failed to build TigerSafe JREI."
        }

        if (!(Test-Path -LiteralPath "$jreiSrcPath" -PathType Container)) {
            ExitError "Failed to find TigerSafe JREI at $jreiSrcPath."
        }

        Write-Host "`nSuccessfully created TigerSafe JREI."

        Pop-Location
    }
}
catch {
    ProcessUnexpectedError $_
}

$pwshPath = AskCmdPath 'powershell.exe' 'PowerShell executable file "powershell.exe"' 'Is it correctly installed and is its installation directory referenced in the "Path" System (not current user) environment variable of this computer ?'
$anyUserAccessibleDirPath = Split-Path -LiteralPath "$pwshPath"

$defInstallPath = Join-Path "$env:ProgramFiles" -ChildPath 'TigerSafe'

while ($true) {
    $oldInstallPath = AskStr "If TigerSafe is not currently installed on this computer, please type `"n`", else please type the directory path where TigerSafe is installed, or nothing to use the default path `"$defInstallPath`""
    if ($oldInstallPath -eq 'n') {
        break
    }
    if (!$oldInstallPath) {
        $oldInstallPath = $defInstallPath
    }
    $oldWinPath = Join-Path "$oldInstallPath" -ChildPath 'win'
    $oldPs1UninstallPath = Join-Path "$oldWinPath" -ChildPath 'uninstall.ps1'
    if (!(Test-Path -LiteralPath $oldPs1UninstallPath -PathType Leaf)) {
        WriteError "No uninstallation script was found at $oldPs1UninstallPath."
        continue
    }
    $confirm = AskStr "The uninstallation script of TigerSafe has been found at $oldPs1UninstallPath, please type `"y`" to execute it, or anything else to choose another installation directory of TigerSafe"
    if ($confirm -ne 'y') {
        continue
    }
	
    try {
        $uninstallProc = Start-Process "$pwshPath" "-ExecutionPolicy AllSigned -File `"$oldPs1UninstallPath`"" -PassThru -Wait
        if ($uninstallProc.ExitCode -ne 0) {
            WriteError "Failed to uninstall previous installation of TigerSafe for Windows (maybe the uninstallation script has been modified, or the certificate with which it was signed has been removed from trusted certificates)."
            continue
        }
        else {
            Write-Host "`nSuccessfully uninstalled previous installation of TigerSafe for Windows."
            break
        }
    }
    catch {
        WriteError "Failed to uninstall previous installation of TigerSafe for Windows (maybe the uninstallation script has been modified, or the certificate with which it was signed has been removed from trusted certificates): $_."
        continue
    }
}

while ($true) {
    $installPath = AskStr "Please type a non existing directory path where TigerSafe should be installed, or nothing to use the default path `"$defInstallPath`""
    if (!$installPath) {
        $installPath = $defInstallPath
    }
    if (!(CheckAvailableDirPath $installPath)) {
        continue
    }
    if ($installPath -ne $defInstallPath) {
        $confirm = AskStr "Please confirm the installation directory for TigerSafe `"$installPath`" by typing `"y`", or anything else to choose another directory"
        if ($confirm -ne 'y') {
            continue
        }
    }
    break
}

$jreiPath = Join-Path "$installPath" -ChildPath 'jrei'
$jreiBinPath = Join-Path "$jreiPath" -ChildPath 'bin'
$jreiBatLauncherPath = Join-Path "$jreiBinPath" -ChildPath 'tigersafeLauncher.bat'
$jreiMonitorProcsCmdPath = Join-Path "$jreiBinPath" -ChildPath 'tigersafeMonitoring.procscmd'
$jreiAppIconPath = Join-Path "$jreiBinPath" -ChildPath 'tigersafeIcon.ico'

$winPath = Join-Path "$installPath" -ChildPath 'win'
$winTempPath = Join-Path "$winPath" -ChildPath 'temp'
$winBatLauncherPath = Join-Path "$winPath" -ChildPath 'launcher.bat'
$ps1LauncherPath = Join-Path "$winPath" -ChildPath 'launcher.ps1'
$ps1InternalPath = Join-Path "$winPath" -ChildPath 'internal.ps1'
$ps1LockInstallPath = Join-Path "$winPath" -ChildPath 'lock-install.ps1'
$ps1UnlockInstallPath = Join-Path "$winPath" -ChildPath 'unlock-install.ps1'
$ps1UninstallPath = Join-Path "$winPath" -ChildPath 'uninstall.ps1'
$ps1EnableLogsPath = Join-Path "$winPath" -ChildPath 'enable-logs.ps1'
$ps1DisableLogsPath = Join-Path "$winPath" -ChildPath 'disable-logs.ps1'
$ps1DisableMonitoringPath = Join-Path "$winPath" -ChildPath 'disable-monitoring.ps1'

$publicCertPath = Join-Path "$winTempPath" -ChildPath 'tspublic.cer'
$ps1InitTsUserEnvPath = Join-Path "$winTempPath" -ChildPath 'init-ts-user-env.ps1'
$ps1InitTsUserEnvResPath = Join-Path "$winTempPath" -ChildPath 'init-ts-user-env-res.txt'

$defGdnInstallPath = Join-Path "$env:windir" -ChildPath 'System32'
while ($true) {
    $gdnInstallPath = AskStr "Please type an existing directory path where TigerSafe Windows service should be installed, or nothing to use the default path `"$defGdnInstallPath`""
    if (!$gdnInstallPath) {
        $gdnInstallPath = $defGdnInstallPath
    }
    if (!(Test-Path -LiteralPath "$gdnInstallPath" -PathType Container)) {
        continue
    }
    if ($gdnInstallPath -ne $defGdnInstallPath) {
        $confirm = AskStr "Please confirm the installation directory for TigerSafe Windows service `"$gdnInstallPath`" by typing `"y`", or anything else to choose another directory"
        if ($confirm -ne 'y') {
            continue
        }
    }
    break
}

$gdnServName = "TigerSafeGuardian"
$gdnFirstTry = $true

while ($true) {
    if (!$gdnFirstTry) {
        $gdnServName = AskStr "Please type an unique non existing Windows service name (with only latin letters) that will be used for TigerSafe"
    }
    else {
        $gdnFirstTry = $false
    }
    if (!$gdnServName) {
        continue
    }
    try {
        $ignoredOut = Get-Service $gdnServName
        WriteError "There is already a Windows service named `"$gdnServName`"."
        continue
    }
    catch {
        # $gdnServName name is available
        $ps1GdnPath = Join-Path "$gdnInstallPath" -ChildPath "$gdnServName.ps1"
        $exeGdnPath = Join-Path "$gdnInstallPath" -ChildPath "$gdnServName.exe"
        if (!(CheckAvailableFilePath $ps1GdnPath) -or !(CheckAvailableFilePath $exeGdnPath)) {
            continue
        }
        break
    }
}

$ps1GdnTempPath = Join-Path "$winTempPath" -ChildPath "$gdnServName.ps1"
$exeGdnTempPath = Join-Path "$winTempPath" -ChildPath "$gdnServName.exe"

$signedExecutablesPath = @("$ps1UninstallPath", "$ps1UnlockInstallPath", "$ps1LockInstallPath", "$ps1LauncherPath", "$ps1InternalPath", "$ps1GdnTempPath", "$exeGdnTempPath", "$ps1EnableLogsPath", "$ps1DisableLogsPath", "$ps1DisableMonitoringPath")
$executablesPath = $signedExecutablesPath + "$winBatLauncherPath"
$gdnExecutablesPath = @("$exeGdnPath", "$ps1GdnPath")

$certThumb = ''

$tsDefaultUserName = 'TigerSafeUser'
$tsUserName = "$tsDefaultUserName"
$tsUser = $null
$tsUserPw = ''

$pwMaxLen = 0
$pwAllowedChars = ''

try {
    $tsUser = GetUser "$tsUserName"
    if ($tsUser) {
        $answer = AskStr "There is already a Windows user named `"$tsUserName`", do you want to reuse it for TigerSafe, or create a new one ? Type `"y`" to reuse it, anything else to create a new Windows user"
        if ($answer -eq 'y') {
            try {
                $tsUser | Enable-LocalUser
            }
            catch {
                Write-Host "An error occurred while trying to enable $tsUserName Windows user, it is probably already enabled: $_"
            }
            Write-Host "Searching a valid basic password..."
            foreach ($basicPw in $basicPws) {
                Write-Host "Try a basic password..."
                if (TrySetUserPw $tsUser "$tsUserName" "$basicPw") {
                    $validBasicPw = $basicPw
                    Write-Host "Found a valid basic password."
                    break
                }
            }
        }
        else {
            $tsUser = $null
            $i = 0
            while (!$tsUser -and ($i -lt 5)) {
                $tsUserName = "$tsDefaultUserName$i"
                $tsUser = TryCreateUser "$tsUserName"
                $i++
            }
            while (!$tsUser) {
                $tsUserName = AskStr "Please type a valid and available Windows user name"
                if (!$tsUserName) {
                    continue
                }
                $tsUser = TryCreateUser "$tsUserName"
            }
        }
    }
    else {
        $tsUser = TryCreateUser "$tsUserName"
        if (!$tsUser) {
            ExitError "An unexpected issue occurred, preventing to correctly create $tsUserName Windows user. Aborted the installation."
        }
    }

    if (!$tsUser) {
        ExitError "Unexpected issue to determine Windows user for TigerSafe (tsUser: $tsUser)"
    }
    
    if (!$validBasicPw -or $validBasicPw.Length -le 0) {
        ExitError "Unexpected issue to determine a valid basic password for Windows user (validBasicPw: $validBasicPw)"
    }

    Write-Host "`nTigerSafe needs to determine Windows user password constraints to know how to generate strong random passwords for when it will be executed. Unfortunately, these constraints are not directly accessible, therefore the process to retrieve them can take some time...`n"

    $validBasicPwLen = $validBasicPw.Length
    Write-Host "Valid basic password length: $validBasicPwLen"
    Write-Host "Searching max length..."
    
    $pwMinLen = $validBasicPwLen
    $pwMaxLen = 60 # big enough for faster allowed chars discovery
    for (; $pwMaxLen -gt 20; $pwMaxLen = $pwMaxLen - 10) {
        $pad = 'a' * ($pwMaxLen - $validBasicPwLen)
        Write-Host "Try length of $pwMaxLen..."
        if (TrySetUserPw $tsUser "$tsUserName" "$validBasicPw$pad") {
            break
        }
    }
    $pad = 'a' * ($pwMaxLen - $validBasicPwLen)
    if (($pwMaxLen -le 20) -and !(TrySetUserPw $tsUser "$tsUserName" "$validBasicPw$pad")) {
        Write-Host "Since max length is small, searching exact max length..."
        while ($pwMinLen -lt $pwMaxLen) {
            $midLen = [int][Math]::Ceiling(($pwMinLen + $pwMaxLen) / 2)
            $pad = 'a' * ($midLen - $validBasicPwLen)
            Write-Host "minLen = $pwMinLen, maxLen = $pwMaxLen, midLen = $midLen"
            if (TrySetUserPw $tsUser "$tsUserName" "$validBasicPw$pad") {
                $pwMinLen = $midLen
            }
            else {
                $pwMaxLen = $midLen - 1
            }
        }
        $pwMaxLen = $pwMinLen
    }

    if ($pwMaxLen -lt $validBasicPw.Length) {
        ExitError "Unexpected issue, pwMaxLen $pwMaxLen < validBasicPw len $($validBasicPw.Length)."
    }
    Write-Host "Found max length: $pwMaxLen"
    if ($pwMaxLen -eq $validBasicPw.Length) {
        ExitError "The valid basic password is too long (has the maximum number of characters: $pwMaxLen)."
    }

    $testableLen = $pwMaxLen - $validBasicPwLen
    Write-Host "`nSearching allowed chars (testableLen = $testableLen)..."

    $chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+[]{}|;:,.<>?/'
    $groupsNum = [int][Math]::Ceiling($chars.Length / $testableLen)
    $pwAllowedChars = ''

    for ($i = 0; $i -lt $groupsNum; $i++) {
        $startInd = $i * $testableLen
        $endExclInd = [Math]::Min(($i + 1) * $testableLen, $chars.Length)
        $testChars = $chars.Substring($startInd, $endExclInd - $startInd)
        Write-Host "Test $($i+1)/$groupsNum chars: $testChars..."
        if (TrySetUserPw $tsUser "$tsUserName" "$basicPw$testChars") {
            $pwAllowedChars = $pwAllowedChars + $testChars
        }
        else {
            Write-Host "An invalid char seems present in $($i+1)/$groupsNum chars, testing chars individually..."
            foreach ($testChar in $testChars) {
                if (TrySetUserPw $tsUser "$tsUserName" "$basicPw$testChar") {
                    $pwAllowedChars = $pwAllowedChars + $testChar
                }
            }
        }
    }

    if ($pwAllowedChars.Length -eq 0) {
        Write-Error "Unexpected issue, pwAllowedChars is empty."
        exit 1
    }
    Write-Host "Found allowed chars: $pwAllowedChars"

    $pwMaxLen = [Math]::Min($pwMaxLen, $pwMaxLenLimit)

    Write-Host "`nSuccessfully determined Windows user passwords constraints: max len = $pwMaxLen (limit = $pwMaxLenLimit), allowed chars = $pwAllowedChars"

    if ([Math]::Pow($pwAllowedChars.Length, $pwMaxLen) -lt [Math]::Pow(10, 12)) {
        $answer = Write-Host "The Windows user passwords contraints are currently considered unsafe because too restrictive, and should therefore be modified. Do you still want to continue the installation of TigerSafe ? Type `"y`" to continue, anything else to abort the installation"
        if ($answer -ne 'y') {
            ExitError "Aborted the installation."
        }
    }
    
    Write-Host "Configuring $tsUserName Windows user..."

    $tsUserPw = NewPw
    if (!$tsUserPw -or ($tsUserPw.Length -le 0) -or ($tsUserPw.Length -ne $pwMaxLen)) {
        ExitError "Unexpectedly failed to generate a strong password: $tsUserPw."
    }
    $tsUser | Set-LocalUser -Password (ConvertTo-SecureString $tsUserPw -AsPlainText -Force) -PasswordNeverExpires $true -UserMayChangePassword $false -AccountNeverExpires
    $tsUser | Disable-LocalUser
}
catch {
    ProcessUnexpectedError $_
}
finally {
    if ($exitingError) {
        Write-Host "`nDespite the installation failure, the eventually created Windows user named `"$tsUserName`" is not automatically deleted by this script because it can be reused for a future installation attempt (which is recommended to avoid creating several useless Windows users and directories)."
        $ignoredOut = Read-Host "Press Enter to exit"
    }
}

try {
    $ignoredOut = New-Item -Path "$installPath" -ItemType Directory -Force
    if (!(Test-Path -LiteralPath "$installPath" -PathType Container)) {
        ExitError "Failed to create $installPath directory."
    }
}
catch {
    ExitError "Failed to create $installPath directory: $_."
}

try {
    $ignoredOut = New-Item -Path "$winPath" -ItemType Directory -Force
    $ignoredOut = New-Item -Path "$winTempPath" -ItemType Directory -Force

    Write-Host "Creating self-signed certificate for TigerSafe executables..."

    $cert = New-SelfSignedCertificate -Type CodeSigningCert -Subject "$certSubject" -KeySpec Signature -KeyAlgorithm RSA -KeyLength 4096 -HashAlgorithm SHA256 -KeyExportPolicy NonExportable -NotAfter (Get-Date).AddYears(5) -CertStoreLocation "Cert:\CurrentUser\My"
    
    try {
        $certThumb = $cert.Thumbprint
        Write-Host "Created self-signed certificate for TigerSafe executables (thumbprint: $certThumb)."

        $ignoredOut = Export-Certificate -Cert $cert -FilePath "$publicCertPath" -Force -Type CERT
        $ignoredOut = Import-Certificate -FilePath "$publicCertPath" -CertStoreLocation Cert:\LocalMachine\Root
        $ignoredOut = Import-Certificate -FilePath "$publicCertPath" -CertStoreLocation Cert:\LocalMachine\TrustedPublisher
        Remove-Item -LiteralPath "$publicCertPath" -Force

        foreach ($certsStore in $certsStores) {
            $curCerts = Get-ChildItem -LiteralPath $certsStore | Where-Object { $_.Thumbprint -eq $certThumb }
            if (!$curCerts -or $curCerts.Count -ne 1) {
                ExitError "Failed to import the self-signed certificate in `"$certsStore`"."
            }
        }

        Write-Host "Imported self-signed certificate for TigerSafe executables."

        Write-Host "Creating TigerSafe Windows executables..."

        $uninstallTopLines = @(
            "[CmdletBinding()]",
            "param([switch]`$AsAdmin)",
            "`$certsStores = @('$($certsStores -join "', '")')"
        ) + (NewPs1StrVarsLines @('installPath', 'tsUserName', 'certSubject', 'certThumb', 'gdnServName', 'ps1GdnPath', 'exeGdnPath', 'ps1UnlockInstallPath'))
        GeneratePs1 "$ps1UninstallPath" (Join-Path "$winSrcPath" -ChildPath 'uninstall-bottom.ps1') $uninstallTopLines

        GeneratePs1 "$ps1UnlockInstallPath" (Join-Path "$winSrcPath" -ChildPath 'unlock-install-bottom.ps1') (NewPs1StrVarsLines @('installPath', 'jreiBatLauncherPath'))

        GeneratePs1 "$ps1LockInstallPath" (Join-Path "$winSrcPath" -ChildPath 'lock-install-bottom.ps1') (NewPs1StrVarsLines @('installPath', 'jreiBatLauncherPath'))
        
        GeneratePs1 "$ps1LauncherPath" (Join-Path "$winSrcPath" -ChildPath 'launcher-bottom.ps1') (NewPs1StrVarsLines @('gdnServName', 'pwshPath', 'ps1InternalPath', 'tsUserName', 'tsUserPw'))

        $internalIncompleteBottomLines = Get-Content -LiteralPath (Join-Path "$winSrcPath" -ChildPath 'internal-bottom.ps1') -Force
        $tsUserSID = $tsUser.SID.Value
        $internalCompletedLines = NewPs1StrVarsLines @('jreiBatLauncherPath', 'tsUserSID')
        $foundPlaceholder = $false
        foreach ($line in $internalIncompleteBottomLines) {
            if ($line -eq '#@# internal-proc-perms.cs #@#') {
                $internalCompletedLines += Get-Content -LiteralPath (Join-Path "$winSrcPath" -ChildPath 'internal-proc-perms.cs') -Force
                $foundPlaceholder = $true
            }
            else {
                $internalCompletedLines += $line
            }
        }
        if (!$foundPlaceholder) {
            ExitError "Missing placeholder in $ps1InternalPath."
        }
        Set-Content -LiteralPath "$ps1InternalPath" -Value $internalCompletedLines -Encoding $ps1Encoding -Force -NoNewline:$false

        GeneratePs1 "$ps1EnableLogsPath" (Join-Path "$winSrcPath" -ChildPath 'enable-logs-bottom.ps1') (NewPs1StrVarsLines @('pwshPath', 'jreiBatLauncherPath', 'ps1UnlockInstallPath', 'ps1LockInstallPath', 'batEncoding'))
        
        GeneratePs1 "$ps1DisableLogsPath" (Join-Path "$winSrcPath" -ChildPath 'disable-logs-bottom.ps1') (NewPs1StrVarsLines @('pwshPath', 'jreiBatLauncherPath', 'ps1UnlockInstallPath', 'ps1LockInstallPath', 'batEncoding'))

        GeneratePs1 "$ps1DisableMonitoringPath" (Join-Path "$winSrcPath" -ChildPath 'disable-monitoring-bottom.ps1') (NewPs1StrVarsLines @('pwshPath', 'jreiBatLauncherPath', 'ps1UnlockInstallPath', 'ps1LockInstallPath', 'batEncoding'))
        
        @(
            "`"$pwshPath`" -ExecutionPolicy AllSigned -File `"$ps1LauncherPath`""
        ) | Set-Content -LiteralPath "$winBatLauncherPath" -Encoding $batEncoding -Force -NoNewline:$false

        GeneratePs1 "$ps1GdnTempPath" (Join-Path "$winSrcPath" -ChildPath 'guardian-bottom.ps1') (NewPs1StrVarsLines @('gdnServName', 'pwMaxLen', 'pwAllowedChars', 'tsUserName', 'tsUserPw'))

        try {
            Write-Host "Compiling $exeGdnTempPath..."
            $ps1GdnPathCSStr = $ps1GdnPath.Replace('\', '\\')
            $pwshPathCSStr = $pwshPath.Replace('\', '\\')

            $csGdnSrc = Get-Content -LiteralPath (Join-Path "$winSrcPath" -ChildPath 'guardian.cs') -Raw
            $csGdnSrc = $csGdnSrc.Replace('$pwshPath', "$pwshPathCSStr")
            $csGdnSrc = $csGdnSrc.Replace('$gdnServName', "$gdnServName")
            $csGdnSrc = $csGdnSrc.Replace('$ps1GdnPath', "$ps1GdnPathCSStr")

            Write-Verbose "----- csGdnSrc start -----"
            Write-Verbose $csGdnSrc
            Write-Verbose "-----  csGdnSrc end  -----"
            Add-Type -TypeDefinition $csGdnSrc -Language CSharp -OutputAssembly $exeGdnTempPath -OutputType ConsoleApplication -ReferencedAssemblies "System.ServiceProcess" -Debug:$false
        }
        catch {
            ExitError "Failed to create $exeGdnTempPath file: $_"
        }

        if (!(Test-Path -LiteralPath $exeGdnTempPath -PathType Leaf)) {
            ExitError "Failed to create $exeGdnTempPath file."
        }

        foreach ($executablePath in $executablesPath) {
            if (!(Test-Path -LiteralPath "$executablePath" -PathType Leaf) -or ((Get-Item "$executablePath").length -eq 0)) {
                ExitError "Failed to create $executablePath file."
            }
        }

        Write-Host "Created TigerSafe Windows executables."

        foreach ($signedExecutablePath in $signedExecutablesPath) {
            $ignoredOut = Set-AuthenticodeSignature -FilePath "$signedExecutablePath" -Certificate $cert -HashAlgorithm SHA256 -Force
            $sign = Get-AuthenticodeSignature -FilePath "$signedExecutablePath"
            if ($sign.Status -ne 'Valid') {
                ExitError "Failed to sign $signedExecutablePath."
            }
        }

        Write-Host "Signed TigerSafe Windows executables."

        foreach ($executablePath in $executablesPath) {
            SetReadOnly "$executablePath"
        }
    }
    finally {
        try {
            $certPath = "$($cert.PSPath)"
            Remove-Item -LiteralPath "$certPath" -DeleteKey -Force

            if (Test-Path -LiteralPath "$certPath") {
                ExitError "Failed to delete the created self-signed certificate ($certPath). This is dangerous."
            }
            else {
                Write-Host "Deleted the created self-signed certificate."
            }
        }
        catch {
            ProcessUnexpectedError $_
        }
    }
}
catch {
    ProcessUnexpectedError $_
}
finally {
    try {
        if ($exitingError) {
            if ($certThumb) {
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

            $answer = AskStr "Since TigerSafe could not be fully installed, do you want to keep the $installPath directory that has been created (it could be useful to keep it for debugging) ? Type `"y`" to keep it, anything else to delete it"
            if ($answer -ne 'y') {
                Remove-Item -LiteralPath "$installPath" -Recurse -Force
                if (Test-Path -LiteralPath "$installPath" -PathType Container) {
                    ExitError "Failed to delete $installPath directory."
                }
                Write-Host "Deleted $installPath directory."
            }
        }
    }
    catch {
        ProcessUnexpectedError $_
    }
}

try {
    Copy-Item -LiteralPath "$exeGdnTempPath" "$exeGdnPath"
    Copy-Item -LiteralPath "$ps1GdnTempPath" "$ps1GdnPath"
    foreach ($gdnFilePath in $gdnExecutablesPath) {
        if (!(Test-Path -LiteralPath $gdnFilePath -PathType Leaf)) {
            ExitError "Failed to install $gdnFilePath file. This can be caused by your antivirus."
        }
        SetReadOnly "$gdnFilePath"
        $lastOut = icacls "`"$gdnFilePath`"" '/grant:r' '*S-1-5-18:(R,X)' '/inheritancelevel:r'
        CheckLastProgramOutput $lastOut "Failed to set permissions of SYSTEM on $gdnFilePath"
    }

    Write-Verbose "Registering $gdnServName Windows service..."

    $emptyPw = New-Object System.Security.SecureString
    $cred = New-Object System.Management.Automation.PSCredential ('.\LocalSystem', $emptyPw)
    try {
        $ignoredOut = New-Service $gdnServName $exeGdnPath -DisplayName $gdnServName -Description 'Protects users of TigerSafe' -StartupType Automatic -Credential $cred
    }
    catch {
        ExitError "Failed to register $gdnServName Windows service: $_"
    }
    $lastOut = sc.exe sdset "$gdnServName" 'D:(A;;CCLCSWRPWPDTLOCRRC;;;SY)(A;;CCDCLCSWRPWPDTLOCRSDRCWDWO;;;BA)(A;;LCRP;;;BU)'
    CheckLastProgramOutput $lastOut "Failed to set permissions on $gdnServName Windows service."

    Write-Host "`nInitializing $tsUserName Windows user environment for TigerSafe (this can potentially take a lot of time, be patient and do not close the windows)..."

    try {
        $tsUser | Enable-LocalUser
		
        Set-Content -LiteralPath "$ps1InitTsUserEnvResPath" -Value "invalid" -Encoding ascii -Force
        $lastOut = icacls "`"$ps1InitTsUserEnvResPath`"" '/grant' "`"$tsUserName`":W"
        CheckLastProgramOutput $lastOut "Failed to give write permission to $tsUserName on $ps1InitTsUserEnvResPath file."
		
        GeneratePs1 "$ps1InitTsUserEnvPath" (Join-Path "$winSrcPath" -ChildPath 'init-ts-user-env-bottom.ps1') (NewPs1StrVarsLines @('ps1InitTsUserEnvResPath'))
		
        $secPw = ConvertTo-SecureString $tsUserPw -AsPlainText -Force
        $userCred = New-Object System.Management.Automation.PSCredential ($tsUserName, $secPw)
        $proc = Start-Process "$pwshPath" "-ExecutionPolicy Bypass -File `"$ps1InitTsUserEnvPath`"" -Credential $userCred -WorkingDirectory "$anyUserAccessibleDirPath" -PassThru -Wait
        if ($proc.ExitCode -ne 0) {
            ExitError "Failed to execute initializer of $tsUserName Windows user environment for TigerSafe."
        }
		
        $lines = Get-Content -LiteralPath "$ps1InitTsUserEnvResPath" -Force
        if ($lines.Count -gt 1) {
            $tsUserDirPath = $lines[0]
        }
        else {
            $tsUserDirPath = $lines
        }
        if (!$tsUserDirPath) {
            ExitError "Failed to read tsUserDirPath ($tsUserDirPath)."
        }
    }
    catch {
        ExitError "Failed to execute initializer of $tsUserName Windows user environment for TigerSafe: $_"
    }
    finally {
        $tsUser | Disable-LocalUser
        $lastOut = icacls "`"$ps1InitTsUserEnvResPath`"" '/remove:g' "`"$tsUserName`""
        CheckLastProgramOutput $lastOut "Failed to remove write permission from $tsUserName on $ps1InitTsUserEnvResPath file."
    }
    
    if (!(Test-Path -LiteralPath "$tsUserDirPath" -PathType Container)) {
        ExitError "Failed to create a directory for TigerSafe in $tsUserName Windows home directory (tsUserDirPath = $tsUserDirPath)."
    }

    Remove-Item -LiteralPath "$winTempPath" -Recurse -Force
    if (Test-Path -LiteralPath "$winTempPath" -PathType Container) {
        WriteError "Failed to delete $winTempPath directory."
    }

    Write-Host "Installing TigerSafe JREI..."
    if (!(Test-Path -LiteralPath "$jreiSrcPath" -PathType Container)) {
        ExitError "Could not find TigerSafe JREI at $jreiSrcPath."
    }
    Copy-Item -Path "$jreiSrcPath" -Destination "$jreiPath" -Recurse -Force
    if (!(Test-Path -LiteralPath "$jreiPath" -PathType Container)) {
        ExitError "Failed to install TigerSafe JREI at $jreiPath."
    }
    if (!(Test-Path -LiteralPath "$jreiBatLauncherPath" -PathType Leaf)) {
        ExitError "Could not find TigerSafe JREI launcher at $jreiBatLauncherPath."
    }

    $tsUserConfigPath = Join-Path "$tsUserDirPath" -ChildPath 'config.properties'
    $tsUserMonitorProcsCmdPath = Join-Path "$tsUserDirPath" -ChildPath 'tigersafeMonitoring.procscmd'

    $jreiBatLines = Get-Content -LiteralPath "$jreiBatLauncherPath" -Force
    if ($jreiBatLines.Count -lt 3) {
        ExitError "Failed to retrieve lines of $jreiBatLauncherPath."
    }
    $jreiBatInitJvmArgsLine = $jreiBatLines[1]
    $jreiBatLines = $jreiBatLines + @('PAUSE')

    $jreiBatLines[1] = "$jreiBatInitJvmArgsLine -Dtigersafe.monitoringPeriodMs=10000 -Dtigersafe.monitoringProcessesCommandFile=`"$tsUserMonitorProcsCmdPath`" -Dtigersafe.monitoringUpdateProcessesCommandFile=true"
    $execLine = $jreiBatLines[-2]
    $execLineArgsStartInd = $execLine.IndexOf('%*')
    if ($execLineArgsStartInd -lt 1) {
        ExitError "Failed to find the position of TigerSafe arguments in $jreiBatLauncherPath."
    }
    $jreiBatLines[-2] = $execLine.Substring(0, $execLineArgsStartInd) + "-config `"$tsUserConfigPath`""

    Set-Content -LiteralPath "$jreiBatLauncherPath" -Value $jreiBatLines -Force -Encoding $batEncoding -NoNewline:$false

    Write-Host "Launching TigerSafe (this can potentially take a lot of time, be patient, do not close the windows, and wait for the prompt inviting you to press a key)..."

    try {
        $proc = Start-Process "$winBatLauncherPath" -PassThru -Wait
        if ($proc.ExitCode -ne 0) {
            ExitError "Failed to launch TigerSafe with $winBatLauncherPath."
        }
        if (!(Test-Path -LiteralPath "$tsUserMonitorProcsCmdPath" -PathType Leaf)) {
            ExitError "Failed to make TigerSafe fill $tsUserMonitorProcsCmdPath."
        }
    }
    catch {
        ExitError "Failed to launch TigerSafe with $winBatLauncherPath`: $_"
    }

    Move-Item -LiteralPath "$tsUserMonitorProcsCmdPath" "$jreiMonitorProcsCmdPath" -Force
    $monitorProcsCmdLines = Get-Content -LiteralPath "$jreiMonitorProcsCmdPath" -Force
    $monitorProcsCmdLinesNum = $monitorProcsCmdLines.Count
    if ($monitorProcsCmdLinesNum -le 3) {
        $monitoringMaxProcessesNum = 3
    }
    elseif ($monitorProcsCmdLinesNum -eq 4) {
        $monitoringMaxProcessesNum = 5
    }
    else {
        $monitoringMaxProcessesNum = $monitorProcsCmdLinesNum
    }

    $jreiBatLines[1] = "$jreiBatInitJvmArgsLine -Dtigersafe.monitoringPeriodMs=$monitoringPeriodMs -Dtigersafe.monitoringProcessesCommandFile=`"$jreiMonitorProcsCmdPath`" -Dtigersafe.monitoringMinProcessesNum=$monitoringMinProcessesNum -Dtigersafe.monitoringMaxProcessesNum=$monitoringMaxProcessesNum"

    Set-Content -LiteralPath "$jreiBatLauncherPath" -Value $jreiBatLines -Encoding $batEncoding -Force -NoNewline:$false
    
    try {
        $tsUserConfigLines = Get-Content -LiteralPath "$tsUserConfigPath" -Force
        if ($tsUserConfigLines.Count -lt 3) {
            WriteError "Failed to retrieve lines of $tsUserConfigLines (this is not a breaking issue)."
        }
        $lineInd = 0
        foreach ($line in $tsUserConfigLines) {
            if ($line.StartsWith('lastSafeFile')) {
                $formattedConfigPath = $tsUserConfigPath.Replace('\', '\\').Replace(':', '\:')
                $tsUserConfigLines[$lineInd] = "lastSafeFile=$formattedConfigPath"
            }
            $lineInd++
        }
        Set-Content -LiteralPath "$tsUserConfigPath" -Value $tsUserConfigLines -Encoding $batEncoding -Force -NoNewline:$false
    }
    catch {
        WriteError "Failed to initialize the default directory where to store safe files: $_. You will have to manually select it in the TigerSafe interface."
    }

    $lockProc = Start-Process "$pwshPath" "-ExecutionPolicy AllSigned -File `"$ps1LockInstallPath`"" -PassThru -Wait
    if ($lockProc.ExitCode -ne 0) {
        WriteError "Failed to lock installation of TigerSafe for Windows (the permissions on $installPath could not be set to minimal required). This is not recommended."
    }
    else {
        Write-Host "Successfully locked installation of TigerSafe for Windows."
    }
}
catch {
    ProcessUnexpectedError $_
}
finally {
    try {
        if ($exitingError) {
            $answer = AskStr "Since TigerSafe could not be fully installed, do you want to uninstall the part that has been installed (it can be useful to keep it for debugging) ? Type `"y`" to uninstall, anything else to keep everything"
            if ($answer -eq 'y') {
                $uninstallProc = Start-Process "$pwshPath" "-ExecutionPolicy AllSigned -File `"$ps1UninstallPath`"" -PassThru -Wait
                if ($uninstallProc.ExitCode -ne 0) {
                    ExitError "Failed to uninstall the incomplete installed part of TigerSafe for Windows."
                }
                else {
                    Write-Host "`nSuccessfully uninstalled the incomplete installed part of TigerSafe for Windows."
                    $ignoredOut = Read-Host "Press Enter to exit"
                }
            }
        }
    }
    catch {
        ProcessUnexpectedError $_
    }
}

Write-Host "`nSuccessfully finished installation of TigerSafe for Windows." -ForegroundColor Green -BackgroundColor Black

$lnkWinLauncherPath = 'n'
$defLnkWinLauncherPath = ''
try {
    $curUserFullName = (Get-CimInstance -ClassName Win32_ComputerSystem).username
    $curUserName = $curUserFullName.Split('\')[-1]
    $curUserProfile = "$env:userprofile".Replace([System.Environment]::UserName, $curUserName)
    $defLnkWinLauncherPath = Join-Path "$curUserProfile" -ChildPath 'Desktop'
}
catch {
    $defLnkWinLauncherPath = Join-Path "$env:userprofile" -ChildPath 'Desktop'
}
$defLnkWinLauncherPath = Join-Path "$defLnkWinLauncherPath" -ChildPath 'TigerSafe.lnk'
while ($true) {
    $lnkWinLauncherPath = AskStr "Do you want to create a shortcut for launching TigerSafe ? If not, type `"n`", otherwise type the path of the shortcut file to create (including its name, that must end with `".lnk`"), or nothing to use the default path `"$defLnkWinLauncherPath`""
    if ($lnkWinLauncherPath -eq 'n') {
        break;
    }
    if (!$lnkWinLauncherPath) {
        $lnkWinLauncherPath = $defLnkWinLauncherPath
    }
    $lnkWinLauncherDirPath = Split-Path -LiteralPath "$lnkWinLauncherPath"
    if (!(Test-Path -LiteralPath "$lnkWinLauncherDirPath" -PathType Container)) {
        WriteError "The $lnkWinLauncherDirPath directory does not exist."
        continue
    }
    if (!$lnkWinLauncherPath.EndsWith('.lnk')) {
        WriteError "The shortcut file path must end with `".lnk`"."
        continue
    }
    try {
        $wsh = New-Object -ComObject 'WScript.Shell'
        $lnkWinLauncher = $wsh.CreateShortcut($lnkWinLauncherPath)
        $lnkWinLauncher.TargetPath = $winBatLauncherPath
        $lnkWinLauncher.WorkingDirectory = $winPath
        $lnkWinLauncher.IconLocation = $jreiAppIconPath
        $lnkWinLauncher.Save()
        Write-Host "`nSuccessfully created the shortcut at $lnkWinLauncherPath."
        break
    }
    catch {
        WriteError "Failed to create the shortcut at $lnkWinLauncherPath`: $_."
        continue
    }
}

$shortcutTxt = ''
if ($lnkWinLauncherPath -and ($lnkWinLauncherPath -ne 'n')) {
    $shortcutTxt = "`nor `n  $lnkWinLauncherPath "
}

Write-Host "`nExplanation: `n`nTigerSafe has been installed at: `n  $installPath `n(this installation is valid for 5 years, after that period it must be reinstalled, but the stored data can be kept for life)`n`nIt can be launched by any Windows user by executing: `n  $winBatLauncherPath `nor `n  $ps1LauncherPath $shortcutTxt`n(but cannot be launched simultaneously several times). `n`nWhen TigerSafe is launched, it is effectively executed by the special $tsUserName Windows user, who has his own files and Windows environment. Therefore, you cannot see and use your own Windows files and directories (of your Windows user) in TigerSafe, but instead you can see and use the files and directories of $tsUserName Windows user. `n`nWARNING: For security reasons, TigerSafe can automatically close itself, and unfortunately it can happen when opening `"This PC`" section of Windows file browser through TigerSafe. But it should not happen when just unfolding this section (to see and open its contents on the left), without clicking on it (without opening it directly). `nTherefore, you should use the following directory to store files and directories used in TigerSafe: `n  $tsUserDirPath `nwhich should be available in `"Quick Access`" on the left of Windows file browser when opening a file/directory through TigerSafe. `nIf this automatic closing mechanism prevents the use of TigerSafe, you can try disabling it by executing as administrator: `n  $ps1DisableMonitoringPath `n(right click on this file, then click `"Execute with PowerShell`") `nHowever, this is not recommended, the underlying issue should be identified and resolved instead. `n`nFor security reasons, TigerSafe installation directory ($installPath) is `"locked`" (its Windows permissions should prevent anyone, including Administrators and SYSTEM, to modify it). When needed (typically when needing to change some execution arguments of TigerSafe), it can be unlocked by executing as administrator: `n  $ps1UnlockInstallPath `n(right click on this file, then click `"Execute with PowerShell`") `nand then can be locked back with: `n  $ps1LockInstallPath `n`nTigerSafe can be uninstalled by executing as administrator: `n  $ps1UninstallPath `n(right click on this file, then click `"Execute with PowerShell`")"

Read-Host "Press Enter to exit"