# Dot-source the environment script to prevent missing variable warnings from Docker Compose

# Check if the container is running or exists
$container = docker ps -a --filter "name=minio-gata" -q

if ($container) {
    Write-Host "Stopping and removing minio-gata container..." -ForegroundColor Yellow
    docker compose down minio-gata
    Write-Host "minio-gata server stopped successfully." -ForegroundColor Green
} else {
    Write-Host "minio-gata is not currently running." -ForegroundColor Cyan
}