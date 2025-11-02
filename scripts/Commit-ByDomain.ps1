# Git stage and commit by project sub-domain
# Ignores symlinked folders

param(
    [switch]$DryRun = $false
)

# Get untracked files and directories, excluding symlinks
$untracked = git status --porcelain | Where-Object { $_ -match "^\?\?" } | ForEach-Object { $_.Substring(3) }

# Function to check if path is a symlink
function Is-Symlink {
    param([string]$Path)
    $item = Get-Item -Path $Path -Force -ErrorAction SilentlyContinue
    return $null -ne $item -and $item.LinkType -eq "SymbolicLink"
}

# Organize files by domain
$domains = @{}
$symlinkExclusions = @()

foreach ($file in $untracked) {
    # Skip symlinks
    if (Is-Symlink $file) {
        $symlinkExclusions += $file
        Write-Host "⊘ Skipping symlink: $file" -ForegroundColor Gray
        continue
    }

    # Determine domain from path
    $parts = $file -split "[/\\]"
    $domain = if ($parts[0] -match "^\.") { ".config" } else { $parts[0] }

    if (-not $domains.ContainsKey($domain)) {
        $domains[$domain] = @()
    }
    $domains[$domain] += $file
}

# Stage and commit by domain
foreach ($domain in $domains.Keys | Sort-Object) {
    $files = $domains[$domain]
    $fileList = $files -join "`n  "

    Write-Host "`n[Domain: $domain]" -ForegroundColor Cyan
    Write-Host "  Files:`n  $fileList"

    if ($DryRun) {
        Write-Host "  [DRY RUN] Would stage these files" -ForegroundColor Yellow
    } else {
        # Stage files
        git add -- $files

        # Create commit message
        $fileCount = $files.Count
        $message = "feat($domain): add $fileCount new file(s)"
        if ($fileCount -gt 1) {
            $message = "feat($domain): add $fileCount new files"
        }

        # Commit
        git commit -m $message
        Write-Host "  [OK] Committed" -ForegroundColor Green
    }
}

if ($symlinkExclusions.Count -gt 0) {
    Write-Host "`n[WARN] Excluded symlinks (not staged):" -ForegroundColor Yellow
    $symlinkExclusions | ForEach-Object { Write-Host "  [skip] $_" -ForegroundColor Gray }
}

Write-Host "`n[Complete]" -ForegroundColor Green