#!/usr/bin/env bash
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
CWD="$(pwd)"
cd "$repo_root"
docker build -t aiw-dev:java25-node24 .
cd "$CWD"
