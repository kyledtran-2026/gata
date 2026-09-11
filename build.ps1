#Requires -Version 5.1
$ErrorActionPreference = 'Stop'

$BuildNumber = if ($env:BUILD) { $env:BUILD } else { 'latest' }
$MavenExtraArgs = $args

Write-Host "Building Maven project with revision $BuildNumber..."
& mvn @MavenExtraArgs clean package "-Drevision=$BuildNumber" -DskipTests
if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed with exit code $LASTEXITCODE"
}

$JarFile = Get-ChildItem -Path 'target' -Filter '*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '(sources|javadoc)\.jar$' } |
    Select-Object -First 1

if (-not $JarFile) {
    Write-Error "Error: No JAR file found in target/ directory!"
    exit 1
}

# $JarPath = $JarFile.FullName.Replace('\', '/')
$JarPath = "target/$($JarFile.Name)"
Write-Host "Found JAR: $($JarFile.FullName)"

Write-Host "Building Docker image..."
& docker build `
    --build-arg "JAR_FILE=$JarPath" `
    -t "kdt/gata:$BuildNumber" `
    -f Dockerfile .
if ($LASTEXITCODE -ne 0) {
    throw "Docker build failed with exit code $LASTEXITCODE"
}

Write-Host "Build successful! Image tagged as kdt/gata:$BuildNumber"
