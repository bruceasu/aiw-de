#!/usr/bin/env bash

# docker build -t aiw-dev:java25-node24 .

docker run --rm --name aiw-control \
  -p 127.0.0.1:8080:8080 \
  --mount "type=bind,source=$(pwd)/target/ai-dev-manager-10-9.0.0-SNAPSHOT.jar,target=/opt/aiw/app.jar,readonly" \
  -v aiw-data:/opt/aiw/data \
  aiw-dev:java25-node24
