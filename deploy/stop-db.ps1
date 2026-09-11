# Dot-source the environment script to prevent missing variable warnings from Docker Compose

# Check if the container is running or exists
$container = docker ps -a --filter "name=pgvector-gata" -q

if ($container) {
    Write-Host "Stopping and removing pgvector-gata container..." -ForegroundColor Yellow
    docker compose down pgvector-gata
    Write-Host "pgvector-gata server stopped successfully." -ForegroundColor Green
} else {
    Write-Host "pgvector-gata is not currently running." -ForegroundColor Cyan
}