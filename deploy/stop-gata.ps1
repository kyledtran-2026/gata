# Dot-source the environment script to prevent missing variable warnings from Docker Compose

# Check if the container is running or exists
$container = docker ps -a --filter "name=kdt-gata" -q

if ($container) {
    Write-Host "Stopping and removing kdt-gata container..." -ForegroundColor Yellow
    docker compose down kdt-gata
    Write-Host "kdt-gata server stopped successfully." -ForegroundColor Green
} else {
    Write-Host "kdt-gata is not currently running." -ForegroundColor Cyan
}