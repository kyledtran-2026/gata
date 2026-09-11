# Deploy Directory
Contains services related to this project

Services:
* llama-swap: llm server that supports multiple LLMs
  * UI: http://localhost:8789/ui
* pgvector-gata: postgres db with vector extension to be able to store vectors

Scripts:
* ./start_db: starts pgvector-gata database
* ./reset_db: remove pgvector-gata container and associated volume
* ./start_minio.sh start minio
  

## Service doesn't stop due to permissions
1. Get PID: docker inspect --format '{{.State.Pid}}'  llama-swap
2. Kill Process: sudo kill -9 <PID>