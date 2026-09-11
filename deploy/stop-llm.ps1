# Dot-source the environment script to prevent missing variable warnings from Docker Compose

# Check if the container is running or exists
$container = docker ps -a --filter "name=llama-swap" -q

if ($container) {
    Write-Host "Stopping and removing llama-swap container..." -ForegroundColor Yellow
    docker compose down llama-swap
    Write-Host "llama-swap server stopped successfully." -ForegroundColor Green
} else {
    Write-Host "llama-swap is not currently running." -ForegroundColor Cyan
}