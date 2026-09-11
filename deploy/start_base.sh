#!/bin/bash
source env-deploy.sh

hash=$(docker ps | grep pgvector-gata |awk '{print $1}')
if [ -z "$hash" ]
then
  docker compose up -d pgvector-gata
fi

hash=$(docker ps | grep minio-gata |awk '{print $1}')
if [ -z "$hash" ]
then
  docker compose up -d minio-gata
fi