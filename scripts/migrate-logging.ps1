# PowerShell script to migrate from old LogManager to new FZLogger system

param(
    [switch]$WhatIf
)

Write-Host "Starting logging migration..." -ForegroundColor Green
if ($WhatIf) {
    Write-Host "WhatIf mode enabled - no files will be modified" -ForegroundColor Yellow
}

# Find all Java files that need migration
$javaFiles = Get-ChildItem -Path ".\src\main\java" -Recurse -Filter "*.java"

$migratedCount = 0
$filesToMigrate = @()

foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw
    $originalContent = $content
    
    # Skip files that already use the new system
    if ($content -match "import org\.fourz\.RVNKQuests\.util\.log\.LogManager") {
        continue
    }
    
    # Skip the old LogManager files themselves
    if ($file.Name -eq "LogManager.java" -or $file.Name -eq "RVNKLogger.java" -or $file.Name -eq "Debug.java") {
        continue
    }
    
    # Skip files that don't use the old logging system
    if (-not ($content -match "import org\.fourz\.RVNKQuests\.util\.LogManager" -or 
              $content -match "import org\.fourz\.RVNKQuests\.util\.RVNKLogger" -or
              $content -match "RVNKLogger.*logger")) {
        continue
    }
    
    # Replace imports
    $content = $content -replace "import org\.fourz\.RVNKQuests\.util\.LogManager;", "import org.fourz.RVNKQuests.util.log.LogManager;"
    $content = $content -replace "import org\.fourz\.RVNKQuests\.util\.RVNKLogger;", "import org.fourz.RVNKQuests.util.log.FZLogger;"
    
    # Replace type declarations
    $content = $content -replace "private final RVNKLogger logger", "private final FZLogger logger"
    $content = $content -replace "private static RVNKLogger logger", "private static FZLogger logger"
    $content = $content -replace "RVNKLogger logger", "FZLogger logger"
    
    # Replace parameterized logging calls with simple {} placeholders
    $content = $content -replace 'logger\.debug\("([^"]*)\{\}([^"]*)", ([^)]+)\);', 'logger.debug("$1" + $3 + "$2");'
    $content = $content -replace 'logger\.info\("([^"]*)\{\}([^"]*)", ([^)]+)\);', 'logger.info("$1" + $3 + "$2");'
    $content = $content -replace 'logger\.warning\("([^"]*)\{\}([^"]*)", ([^)]+)\);', 'logger.warning("$1" + $3 + "$2");'
    
    # Replace parameterized logging calls with multiple {} placeholders (more complex regex)
    $content = $content -replace 'logger\.debug\("([^"]*)\{\}([^"]*)\{\}([^"]*)", ([^,]+), ([^)]+)\);', 'logger.debug("$1" + $4 + "$2" + $5 + "$3");'
    $content = $content -replace 'logger\.info\("([^"]*)\{\}([^"]*)\{\}([^"]*)", ([^,]+), ([^)]+)\);', 'logger.info("$1" + $4 + "$2" + $5 + "$3");'
    $content = $content -replace 'logger\.warning\("([^"]*)\{\}([^"]*)\{\}([^"]*)", ([^,]+), ([^)]+)\);', 'logger.warning("$1" + $4 + "$2" + $5 + "$3");'
    
    # Only process if content changed
    if ($content -ne $originalContent) {
        $changeInfo = @{
            File = $file.FullName
            RelativePath = $file.FullName.Replace((Get-Location).Path + "\", "")
            HasImportChanges = ($originalContent -match "import org\.fourz\.RVNKQuests\.util\.(LogManager|RVNKLogger)")
            HasTypeChanges = ($originalContent -match "RVNKLogger.*logger")
            HasLoggingCallChanges = ($originalContent -match 'logger\.(debug|info|warning)\([^)]*\{\}')
        }
        $filesToMigrate += $changeInfo
        
        if ($WhatIf) {
            Write-Host "Would migrate: $($changeInfo.RelativePath)" -ForegroundColor Cyan
            if ($changeInfo.HasImportChanges) { Write-Host "  - Import changes" -ForegroundColor Gray }
            if ($changeInfo.HasTypeChanges) { Write-Host "  - Type declaration changes" -ForegroundColor Gray }
            if ($changeInfo.HasLoggingCallChanges) { Write-Host "  - Logging call parameter changes" -ForegroundColor Gray }
        } else {
            Set-Content -Path $file.FullName -Value $content -NoNewline
            Write-Host "Migrated: $($changeInfo.RelativePath)" -ForegroundColor Green
        }
        $migratedCount++
    }
}

if ($WhatIf) {
    Write-Host "`nWhatIf Summary:" -ForegroundColor Yellow
    Write-Host "Files that would be migrated: $migratedCount" -ForegroundColor Yellow
    Write-Host "`nTo perform the actual migration, run without -WhatIf flag" -ForegroundColor Yellow
} else {
    Write-Host "`nMigration complete. $migratedCount files updated." -ForegroundColor Green
}

# Show summary of what types of changes were found
if ($filesToMigrate.Count -gt 0) {
    $importChanges = ($filesToMigrate | Where-Object { $_.HasImportChanges }).Count
    $typeChanges = ($filesToMigrate | Where-Object { $_.HasTypeChanges }).Count
    $loggingCallChanges = ($filesToMigrate | Where-Object { $_.HasLoggingCallChanges }).Count
    
    Write-Host "`nChange Summary:" -ForegroundColor Yellow
    Write-Host "  Import changes: $importChanges files" -ForegroundColor Gray
    Write-Host "  Type declaration changes: $typeChanges files" -ForegroundColor Gray
    Write-Host "  Logging call changes: $loggingCallChanges files" -ForegroundColor Gray
}
