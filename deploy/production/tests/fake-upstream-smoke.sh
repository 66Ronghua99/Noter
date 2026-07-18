#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
production_dir="$(cd -- "$script_dir/.." && pwd)"
repo_root="$(cd -- "$production_dir/../.." && pwd)"
compose=(docker compose -f "$production_dir/compose.yml" -f "$production_dir/compose.smoke.yml")
audio_file="$repo_root/.noter-smoke-recording.m4a"

cleanup() {
  rm -f -- "$audio_file"
  "${compose[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

export NOTER_API_IMAGE=noter-api-smoke
export NOTER_API_DOMAIN=localhost
export NOTER_ENV_FILE="$production_dir/.env.example"
"${compose[@]}" config --quiet
"${compose[@]}" up --build --detach

ready_url="http://127.0.0.1:18080/health/ready"
for _ in {1..30}; do
  if curl --noproxy '*' --fail --silent --max-time 3 "$ready_url" 2>/dev/null | grep -q '"status":"ok"'; then
    break
  fi
  sleep 1
done
curl --noproxy '*' --fail --silent --show-error --max-time 3 \
  "$ready_url" | grep -q '"status":"ok"'
curl --noproxy '*' --fail --silent --show-error --max-time 3 \
  -H 'Authorization: Bearer smoke-client-token' \
  -H 'Content-Type: application/json' \
  --data '{"messages":[{"role":"user","content":"wake me at eight"}],"tools":[{"name":"create_alarm","description":"Create an alarm.","parameters":{"type":"object"}}],"toolChoice":{"mode":"required"}}' \
  http://127.0.0.1:18080/api/v1/agent/completions | grep -q 'smoke-call-1'

printf 'smoke-audio' > "$audio_file"
curl --noproxy '*' --fail --silent --show-error --max-time 3 \
  -H 'Authorization: Bearer smoke-client-token' \
  -F "audio=@$audio_file;type=audio/mp4" \
  -F 'language=en-US' \
  http://127.0.0.1:18080/api/v1/asr/transcriptions | grep -q 'wake me at eight'

echo "fake-upstream smoke passed"
