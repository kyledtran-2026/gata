# Check if vllm-server container is running using native Docker filtering
$hash = docker ps --filter "name=pgvector-gata" -q

if (-not $hash) {
    # Apply environment changes and start/recreate the container
    docker compose up $args --force-recreate pgvector-gata
}