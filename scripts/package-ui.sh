#!/usr/bin/env bash
# Copy repolens-web-ui production build into repolens-web static resources.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UI_DIR="$ROOT/repolens-web-ui"
PUBLIC_DIR="$ROOT/repolens-web/src/main/resources/public"

cd "$UI_DIR"

if [[ ! -d node_modules ]]; then
  echo "Installing frontend dependencies…"
  npm ci
fi

npm run build

rm -rf "$PUBLIC_DIR"
mkdir -p "$PUBLIC_DIR"
cp -R dist/. "$PUBLIC_DIR/"

echo "Packaged UI → $PUBLIC_DIR"
