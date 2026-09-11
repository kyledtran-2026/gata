# Dot-source the environment script to prevent missing variable warnings from Docker Compose

# Check if the container is running or exists
$container = docker ps -a --filter "name=mineru-api-gata" -q

if ($container) {
    Write-Host "Stopping and removing mineru-api-gata container..." -ForegroundColor Yellow
    docker compose down mineru-api-gata
    Write-Host "mineru-api-gata server stopped successfully." -ForegroundColor Green
} else {
    Write-Host "mineru-api-gata is not currently running." -ForegroundColor Cyan
}