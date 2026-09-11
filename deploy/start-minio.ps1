# Check if vllm-server container is running using native Docker filtering
$hash = docker ps --filter "name=minio-gata" -q

if (-not $hash) {
    # Apply environment changes and start/recreate the container
    docker compose up $args --force-recreate minio-gata
}