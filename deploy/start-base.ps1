# start_base.ps1
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

& "$PSScriptRoot\start-db.ps1" -d
& "$PSScriptRoot\start-minio.ps1" -d
& "$PSScriptRoot\start-mineru.ps1" -d