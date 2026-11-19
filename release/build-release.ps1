#Requires -Version 5.1
<#
.SYNOPSIS
    One-shot release build for Hospital CRM: frontend -> fat jar (with web) -> staging -> Inno Setup .exe.
.DESCRIPTION
    Reads the version from the repo-root VERSION file (or -Version), builds admin-web, packages the
    Spring Boot jar with the embedded console (with-web profile), assembles a staging folder, writes
    version.json, and compiles the Windows installer with Inno Setup (ISCC).
    NOTE: keep this script ASCII-only. Windows PowerShell 5.1 reads .ps1 files without a BOM using the
    system ANSI codepage, so non-ASCII string literals break parsing on Chinese-locale hosts.
.PARAMETER Version
    Release version x.y.z. Defaults to the contents of the VERSION file.
.PARAMETER SkipInstaller
    Build the jar + staging only; do not invoke ISCC (useful when Inno Setup is not installed).
.PARAMETER WithJre
    Bundle a portable JRE into the package (recommended for target machines without Java 21).
.PARAMETER JrePath
    Source JDK/JRE 21 home to bundle. Defaults to JAVA_HOME.
.PARAMETER WithPg
    Bundle the portable PostgreSQL from -PgPath.
.PARAMETER PgPath
    Source PostgreSQL home (its bin/ contains initdb.exe). Default D:\pgbin\pgsql.
.PARAMETER Tag
    Create an annotated git tag v<version> on success.
.PARAMETER SkipTests
    Skip backend unit tests during packaging.
.EXAMPLE
    .\build-release.ps1 -Version 1.0.1 -WithJre -WithPg
.EXAMPLE
    .\build-release.ps1 -SkipInstaller
#>
[CmdletBinding()]
param(
    [string]$Version = '',
    [switch]$SkipInstaller,
    [switch]$WithJre,
    [string]$JrePath = '',
    [switch]$WithPg,
    [string]$PgPath = 'D:\pgbin\pgsql',
    [switch]$Tag,
    [switch]$SkipTests
)
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $repoRoot

function Step([string]$m) { Write-Host "`n==== $m ====" -ForegroundColor Cyan }
function Info([string]$m) { Write-Host "  $m" }

# ---------- 0. version ----------
if (-not $Version) {
    $vf = Join-Path $repoRoot 'VERSION'
    if (-not (Test-Path $vf)) { throw 'No -Version given and VERSION file missing.' }
    $Version = (Get-Content $vf -Raw).Trim()
}
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw "Version '$Version' must be x.y.z (no SNAPSHOT)." }
$buildTime = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss')
$gitCommit = ''
try { $gitCommit = (& git rev-parse --short HEAD).Trim() } catch { }
Info "Version=$Version  commit=$gitCommit  buildTime=$buildTime"

# ---------- 1. toolchain ----------
$defaultJdk = 'C:\Users\skyes\.trae-cn\extensions\redhat.java-1.56.0-win32-x64\jre\21.0.12.1-win32-x86_64'
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    if (Test-Path (Join-Path $defaultJdk 'bin\javac.exe')) { $env:JAVA_HOME = $defaultJdk }
}
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    throw 'JDK 21 not found. Set JAVA_HOME (or -JrePath) to a JDK 21.'
}
Info "JAVA_HOME=$env:JAVA_HOME"
$mvn = Join-Path $repoRoot 'backend\mvnw.cmd'
if (-not (Test-Path $mvn)) { $mvn = 'mvn' }

# ---------- 2. frontend ----------
Step 'Building admin-web (vite)'
Push-Location (Join-Path $repoRoot 'admin-web')
try {
    if (-not (Test-Path 'node_modules')) { & npm ci; if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' } }
    & npm run build; if ($LASTEXITCODE -ne 0) { throw 'npm run build failed' }
} finally { Pop-Location }
$distDir = Join-Path $repoRoot 'admin-web\dist'
if (-not (Test-Path (Join-Path $distDir 'index.html'))) { throw "admin-web dist not found: $distDir" }

# ---------- 3. backend jar (with-web profile auto-active because dist exists) ----------
Step 'Packaging crm-api.jar (embedded console)'
Push-Location (Join-Path $repoRoot 'backend')
try {
    # Inject the release version via a Maven property consumed by application.yml resource
    # filtering (@crm.release.version@). No versions-maven-plugin / network needed.
    $pkgArgs = @('-B','-o','clean','package',"-Dcrm.release.version=$Version")
    if ($SkipTests) { $pkgArgs += '-DskipTests' } else { $pkgArgs += '-Dmaven.test.skip=false' }
    & $mvn @pkgArgs
    if ($LASTEXITCODE -ne 0) { throw 'mvn package failed' }
} finally { Pop-Location }
$jar = Join-Path $repoRoot 'backend\crm-boot\target\crm-api.jar'
if (-not (Test-Path $jar)) { throw "jar not produced: $jar" }
$jarSha = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLower()
Info ("jar={0} ({1:N0} bytes) sha256={2}" -f $jar, (Get-Item $jar).Length, $jarSha)

# ---------- 4. staging ----------
Step 'Assembling staging'
$staging = Join-Path $repoRoot 'dist\staging'
if (Test-Path $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $staging 'app'), (Join-Path $staging 'bin') | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $staging 'app\crm-api.jar') -Force
Copy-Item -Path (Join-Path $repoRoot 'installer\scripts\*.ps1') -Destination (Join-Path $staging 'bin') -Force
Copy-Item -LiteralPath (Join-Path $repoRoot 'installer\templates\env.template') -Destination (Join-Path $staging 'env.template') -Force

# schemaVersion = highest Flyway migration number
$schema = 0
$migDir = Join-Path $repoRoot 'backend\crm-boot\src\main\resources\db\migration'
Get-ChildItem -Path $migDir -Filter 'V*__*.sql' -ErrorAction SilentlyContinue | ForEach-Object {
    if ($_.Name -match '^V(\d+)__') { $n = [int]$matches[1]; if ($n -gt $schema) { $schema = $n } }
}

# version.json
$versionObj = [ordered]@{
    version       = $Version
    buildTime     = $buildTime
    gitCommit     = $gitCommit
    jarSha256     = $jarSha
    installerName = "HospitalCRM-Setup-$Version.exe"
    schemaVersion = $schema
}
$versionObj | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $staging 'version.json') -Encoding ASCII
Info "version.json written (schemaVersion=$schema)"

# README.txt (ASCII)
$readmeLines = @(
    "Hospital CRM v$Version",
    '======================',
    'Layout:',
    '  app\crm-api.jar   application (admin console + API + Swagger, single process)',
    '  bin\start.ps1     start the service',
    '  bin\stop.ps1      stop the service',
    '  bin\status.ps1    show installed/running version and health',
    '  bin\upgrade.ps1   hot upgrade (swap jar, auto DB backup, auto rollback on failure)',
    '  bin\rollback.ps1  roll back to the previous version',
    '  config\.env       runtime configuration (never overwritten by upgrades)',
    '  version.json      release manifest',
    '',
    'Quick start:',
    '  1) Start "Hospital CRM" from the Start menu, or run:  powershell -File bin\start.ps1',
    '  2) Open  http://localhost:8080  and sign in with the admin account set during install.',
    '',
    'Upgrade:',
    '  A (recommended): run the new Setup.exe; the wizard stops the old service, replaces the',
    '     program and keeps .env and data.',
    '  B (silent): powershell -File bin\upgrade.ps1 -NewJar <new>\crm-api.jar -NewVersionJson <new>\version.json',
    '',
    'Rollback: powershell -File bin\rollback.ps1',
    'Uninstall: via Control Panel; you may keep data.'
)
Set-Content -LiteralPath (Join-Path $staging 'README.txt') -Value $readmeLines -Encoding ASCII

# optional JRE
if ($WithJre) {
    if (-not $JrePath) { $JrePath = $env:JAVA_HOME }
    if (-not (Test-Path (Join-Path $JrePath 'bin\java.exe'))) { throw "JRE java.exe not found under $JrePath" }
    Step "Bundling JRE from $JrePath"
    Copy-Item -LiteralPath $JrePath -Destination (Join-Path $staging 'jre') -Recurse -Force
}
# optional PostgreSQL
if ($WithPg) {
    if (-not (Test-Path (Join-Path $PgPath 'bin\initdb.exe'))) { throw "initdb.exe not found under $PgPath\bin" }
    Step "Bundling PostgreSQL from $PgPath"
    Copy-Item -LiteralPath $PgPath -Destination (Join-Path $staging 'pgsql') -Recurse -Force
}

# ---------- 5. installer ----------
$exeOut = ''
if (-not $SkipInstaller) {
    Step 'Compiling Inno Setup installer'
    $isccCandidates = @(
        'C:\Program Files (x86)\Inno Setup 6\ISCC.exe',
        'C:\Program Files\Inno Setup 6\ISCC.exe',
        (Join-Path $env:LOCALAPPDATA 'Programs\Inno Setup 6\ISCC.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\InnoSetup6\ISCC.exe')
    ) | Where-Object { $_ -and (Test-Path $_) }
    if (-not $isccCandidates) { throw 'ISCC.exe not found. Install Inno Setup: winget install --id JRSoftware.InnoSetup -e   (or re-run with -SkipInstaller)' }
    # A partial install (missing islzma.dll / Languages\) cannot compile; prefer a complete one.
    $iscc = $isccCandidates | Where-Object { Test-Path (Join-Path (Split-Path -Parent $_) 'islzma.dll') } | Select-Object -First 1
    if (-not $iscc) { $iscc = $isccCandidates | Select-Object -First 1 }
    $iss = Join-Path $repoRoot 'installer\HospitalCRM.iss'
    # This ISCC build rejects /D command-line defines; inject values via a generated include file.
    $gen = Join-Path $repoRoot 'installer\generated.iss'
    # lzma2 needs islzma.dll next to ISCC; some minimal installs omit it -> fall back to zip (built-in zlib).
    $isccDir = Split-Path -Parent $iscc
    $compression = if (Test-Path (Join-Path $isccDir 'islzma.dll')) { 'lzma2/ultra64' } else { 'zip' }
    # Inno reads .iss as ANSI, and the repo path may contain non-ASCII characters, so pass the
    # staging folder as a path relative to the script (installer\) -- that keeps the file ASCII.
    $relStaging = $staging
    if ($staging.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
        $relStaging = '..' + $staging.Substring($repoRoot.Length)
    }
    if ($relStaging -match '[^\x20-\x7E]') { throw "Staging path is not ASCII-safe: $relStaging" }
    Info "ISCC=$iscc  compression=$compression  staging=$relStaging"
    @(
        '#define MyAppVersion "' + $Version + '"'
        '#define Staging "' + $relStaging + '"'
        '#define MyCompression "' + $compression + '"'
    ) | Set-Content -LiteralPath $gen -Encoding ASCII
    & $iscc "/Qp" $iss
    if ($LASTEXITCODE -ne 0) { throw "ISCC failed (exit=$LASTEXITCODE)" }
    $exeOut = Join-Path $repoRoot "dist\installer\HospitalCRM-Setup-$Version.exe"
    $exeSha = (Get-FileHash -LiteralPath $exeOut -Algorithm SHA256).Hash.ToLower()
    Info ("Installer: {0}" -f $exeOut)
    Info ("Installer sha256: {0}" -f $exeSha)
} else {
    Info ('SkipInstaller set; staging ready at ' + $staging)
}

# ---------- 6. tag ----------
if ($Tag) {
    Step "Tagging v$Version"
    & git tag -a "v$Version" -m "Hospital CRM v$Version"
    Info "Created tag v$Version (push with: git push origin v$Version)"
}

Step 'DONE'
Write-Host ("Version : v{0}" -f $Version)
Write-Host ("Staging : {0}" -f $staging)
if ($exeOut) { Write-Host ("Setup   : {0}" -f $exeOut) }
