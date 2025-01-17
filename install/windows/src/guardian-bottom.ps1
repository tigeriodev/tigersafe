$ErrorActionPreference = 'Stop'

$tsUserFullName = "$env:computername\$tsUserName"

function NewPw {
    $randBytes = New-Object byte[] $pwMaxLen
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($randBytes)

    $sb = New-Object -TypeName System.Text.StringBuilder
    $randBytesLen = $randBytes.Count
    $pwAllowedCharsLen = $pwAllowedChars.Length
    $i = 0
    for (; $i -lt $randBytesLen; $i++) {
        [void]$sb.Append($pwAllowedChars[$randBytes[$i] % $pwAllowedCharsLen])
    }
    $res = $sb.ToString()
    return $res
}

function GetTSUserProcs {
    return Get-Process -IncludeUserName | Where-Object { ($_.UserName -eq $tsUserFullName) -or ($_.UserName -eq $tsUserName) }
}

try {
    $procs = GetTSUserProcs
	
    $successfulConnect = $false
    if ($procs.Count -eq 0) {
        Enable-LocalUser -Name "$tsUserName"
		
        $maxChecks = 20
        $checkNum = 0
        while (($checkNum -lt $maxChecks) -and ($procs.Count -eq 0)) {
            Start-Sleep -Milliseconds 500
            $procs = GetTSUserProcs
            $checkNum++
        }
		
        $successfulConnect = ($checkNum -lt $maxChecks)
    }
	
    Disable-LocalUser -Name "$tsUserName"
	
    if ($successfulConnect) {
        try {
            $tsUser = Get-LocalUser -Name "$tsUserName"
            $tsUser | Set-LocalUser -Password (ConvertTo-SecureString (NewPw) -AsPlainText -Force)
        }
        catch {
            # ignored
        }
    }
	
    $checkDelaySec = 10
    $checkNum = 0
    while (($checkNum -lt 100) -and ($procs.Count -gt 0)) {
        if ($checkNum -gt 50) {
            $checkDelaySec = 600
        }
        elseif ($checkNum -gt 40) {
            $checkDelaySec = 300
        }
        elseif ($checkNum -gt 30) {
            $checkDelaySec = 60
        }
        elseif ($checkNum -gt 10) {
            $checkDelaySec = 30
        }
        Start-Sleep -Seconds $checkDelaySec
        $procs = GetTSUserProcs
        $checkNum++
    }

    try {
        $tsUser = Get-LocalUser -Name "$tsUserName"
        $tsUser | Set-LocalUser -Password (ConvertTo-SecureString $tsUserPw -AsPlainText -Force)
    }
    catch {
        # ignored
    }
}
finally {
    Disable-LocalUser -Name "$tsUserName"
	
    Stop-Service "$gdnServName" -NoWait
}
