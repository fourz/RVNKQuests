# Simple PowerShell script to fix common parameterized logging patterns
$projectRoot = Get-Location
$javaFiles = Get-ChildItem -Path "$projectRoot\src" -Include "*.java" -Recurse

$fixes = 0

foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw
    $originalContent = $content
    
    # Simple pattern replacements for most common cases
    $patterns = @(
        @{ Pattern = 'logger\.debug\("([^"]*)\{\}", ([^)]+)\)'; Replacement = 'logger.debug("$1" + $2)' },
        @{ Pattern = 'logger\.info\("([^"]*)\{\}", ([^)]+)\)'; Replacement = 'logger.info("$1" + $2)' },
        @{ Pattern = 'logger\.warning\("([^"]*)\{\}", ([^)]+)\)'; Replacement = 'logger.warning("$1" + $2)' },
        @{ Pattern = 'logger\.error\("([^"]*)\{\}", ([^)]+)\)'; Replacement = 'logger.error("$1" + $2)' }
    )
    
    foreach ($fix in $patterns) {
        $content = $content -replace $fix.Pattern, $fix.Replacement
    }
    
    # Two parameter cases - basic replacement
    $content = $content -replace 'logger\.debug\("([^"]*)\{\}([^"]*)\{\}", ([^,]+), ([^)]+)\)', 'logger.debug("$1" + $3 + "$2" + $4)'
    $content = $content -replace 'logger\.info\("([^"]*)\{\}([^"]*)\{\}", ([^,]+), ([^)]+)\)', 'logger.info("$1" + $3 + "$2" + $4)'
    $content = $content -replace 'logger\.warning\("([^"]*)\{\}([^"]*)\{\}", ([^,]+), ([^)]+)\)', 'logger.warning("$1" + $3 + "$2" + $4)'
    
    if ($content -ne $originalContent) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
        Write-Host "Fixed: $($file.FullName)"
        $fixes++
    }
}

Write-Host "Fixed $fixes files"
