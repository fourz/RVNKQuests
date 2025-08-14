# PowerShell script to fix parameterized logging calls for FZLogger interface
param(
    [switch]$WhatIf = $false
)

$projectRoot = Get-Location
$javaFiles = Get-ChildItem -Path "$projectRoot\src" -Include "*.java" -Recurse

$changedFiles = @()
$totalChanges = 0

Write-Host "Starting parameterized logging fixes..."

foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw
    $fileChanges = 0
    
    # Pattern 1: Simple single parameter: logger.debug("Message: {}", value)
    $pattern1 = 'logger\.(debug|info|warning|error)\("([^"]*)\{[^}]*\}", ([^)]+)\)'
    $matches1 = [regex]::Matches($content, $pattern1)
    
    foreach ($match in $matches1) {
        $logLevel = $match.Groups[1].Value
        $messageTemplate = $match.Groups[2].Value
        $parameter = $match.Groups[3].Value
        
        # Replace {} with concatenation
        $newMessage = $messageTemplate.Replace('{}', '" + ' + $parameter + ' + "')
        # Clean up any dangling + ""
        $newMessage = $newMessage -replace '\s*\+\s*""\s*$', ''
        $newMessage = $newMessage -replace '^\s*""\s*\+\s*', ''
        
        $newCall = "logger.$logLevel(`"$newMessage`")"
        $content = $content.Replace($match.Value, $newCall)
        $fileChanges++
    }
    
    # Pattern 2: Multiple parameters: logger.debug("Message: {} and {}", value1, value2)
    $pattern2 = 'logger\.(debug|info|warning|error)\("([^"]*)", ([^)]+)\)'
    $matches2 = [regex]::Matches($content, $pattern2)
    
    foreach ($match in $matches2) {
        $logLevel = $match.Groups[1].Value
        $messageTemplate = $match.Groups[2].Value
        $parameters = $match.Groups[3].Value
        
        # Skip if this doesn't contain {} placeholders
        if (-not $messageTemplate.Contains('{}')) {
            continue
        }
        
        # Split parameters by comma (basic splitting)
        $paramList = $parameters -split ',\s*'
        
        # Replace {} placeholders with concatenation
        $newMessage = $messageTemplate
        foreach ($param in $paramList) {
            $trimmedParam = $param.Trim()
            $replacement = '" + ' + $trimmedParam + ' + "'
            # Replace only the first occurrence
            $index = $newMessage.IndexOf('{}')
            if ($index -ge 0) {
                $newMessage = $newMessage.Substring(0, $index) + $replacement + $newMessage.Substring($index + 2)
            }
        }
        
        # Clean up concatenation
        $newMessage = $newMessage -replace '\s*\+\s*""\s*\+\s*', ' + '
        $newMessage = $newMessage -replace '\s*\+\s*""\s*$', ''
        $newMessage = $newMessage -replace '^\s*""\s*\+\s*', ''
        
        $newCall = "logger.$logLevel(`"$newMessage`")"
        $content = $content.Replace($match.Value, $newCall)
        $fileChanges++
    }
    
    # Pattern 3: Complex multi-line parameters (like in LoreDatabase)
    $pattern3 = 'logger\.(debug|info|warning|error)\("([^"]*): "\s*\+\s*\n\s*([^,]+),([^)]+)\)'
    $matches3 = [regex]::Matches($content, $pattern3)
    
    foreach ($match in $matches3) {
        $logLevel = $match.Groups[1].Value
        $firstParam = $match.Groups[3].Value.Trim()
        $remainingParams = $match.Groups[4].Value
        
        # Build proper concatenation
        $paramList = ($firstParam + "," + $remainingParams) -split ',\s*'
        $newMessage = "`"Recording discovery: `" + " + ($paramList -join ' + ", " + ')
        
        $newCall = "logger.$logLevel($newMessage)"
        $content = $content.Replace($match.Value, $newCall)
        $fileChanges++
    }
    
    if ($fileChanges -gt 0) {
        $changedFiles += $file.FullName
        $totalChanges += $fileChanges
        
        if ($WhatIf) {
            Write-Host "Would fix: $($file.FullName) ($fileChanges changes)"
        } else {
            Set-Content -Path $file.FullName -Value $content -NoNewline
            Write-Host "Fixed: $($file.FullName) ($fileChanges changes)"
        }
    }
}

if ($WhatIf) {
    Write-Host "`nWould fix $totalChanges parameterized logging calls in $($changedFiles.Count) files."
} else {
    Write-Host "`nFixed $totalChanges parameterized logging calls in $($changedFiles.Count) files."
}

Write-Host "`nFiles that would be modified:"
$changedFiles | ForEach-Object { Write-Host "  $_" }
