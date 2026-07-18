#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "usage: $0 ghcr.io/<owner>/noter-api:<40-character-sha>" >&2
}

fail() {
  echo "noter deploy: $*" >&2
  return 1
}

image_ref="${1:-}"
if [[ -z "$image_ref" ]]; then
  usage
  exit 2
fi
if [[ ! "$image_ref" =~ ^ghcr\.io/[a-z0-9][a-z0-9._-]*/noter-api:[0-9a-f]{40}$ ]]; then
  fail "image reference must be an immutable GHCR SHA reference"
  exit 2
fi

app_dir="${NOTER_DEPLOY_DIR:-/opt/noter}"
candidate_dir="${NOTER_CANDIDATE_DIR:-$app_dir}"
env_file="${NOTER_ENV_FILE:-$app_dir/.env}"
state_file="${NOTER_IMAGE_STATE_FILE:-$app_dir/.image-reference}"
readiness_url="${NOTER_READINESS_URL:-}"
if [[ -z "$readiness_url" && -f "$env_file" ]]; then
  api_domain="$(sed -n 's/^NOTER_API_DOMAIN=//p' "$env_file" | head -n 1)"
  if [[ -n "$api_domain" ]]; then
    readiness_url="https://${api_domain}/health/ready"
  fi
fi
readiness_url="${readiness_url:-http://127.0.0.1/health/ready}"
readiness_attempts="${NOTER_READINESS_ATTEMPTS:-30}"
readiness_interval="${NOTER_READINESS_INTERVAL_SECONDS:-2}"

[[ "$readiness_attempts" =~ ^[1-9][0-9]*$ ]] || {
  fail "NOTER_READINESS_ATTEMPTS must be positive"
  exit 2
}
[[ "$readiness_interval" =~ ^[0-9]+$ ]] || {
  fail "NOTER_READINESS_INTERVAL_SECONDS must be non-negative"
  exit 2
}

for required_file in "$candidate_dir/compose.yml" "$candidate_dir/Caddyfile" "$env_file"; do
  [[ -f "$required_file" ]] || {
    fail "required deployment file is missing: $required_file"
    exit 2
  }
done
mkdir -p "$app_dir"

previous_image=""
if [[ -f "$state_file" ]]; then
  previous_image="$(sed -n '1p' "$state_file")"
  [[ "$previous_image" =~ ^ghcr\.io/[a-z0-9][a-z0-9._-]*/noter-api:[0-9a-f]{40}$ ]] || {
    fail "previous image state is invalid"
    exit 2
  }
fi

backup_dir="$(mktemp -d "$app_dir/.deploy-backup.XXXXXX")"
cleanup_backup() {
  rm -rf -- "$backup_dir"
}
trap cleanup_backup EXIT

had_compose=0
had_caddy=0
if [[ -f "$app_dir/compose.yml" ]]; then
  cp -p "$app_dir/compose.yml" "$backup_dir/compose.yml"
  had_compose=1
fi
if [[ -f "$app_dir/Caddyfile" ]]; then
  cp -p "$app_dir/Caddyfile" "$backup_dir/Caddyfile"
  had_caddy=1
fi

restore_files() {
  if (( had_compose )); then
    cp -p "$backup_dir/compose.yml" "$app_dir/compose.yml"
  else
    rm -f -- "$app_dir/compose.yml"
  fi
  if (( had_caddy )); then
    cp -p "$backup_dir/Caddyfile" "$app_dir/Caddyfile"
  else
    rm -f -- "$app_dir/Caddyfile"
  fi
}

if [[ "$candidate_dir" != "$app_dir" ]]; then
  cp -p "$candidate_dir/compose.yml" "$app_dir/compose.yml"
  cp -p "$candidate_dir/Caddyfile" "$app_dir/Caddyfile"
fi

compose=(docker compose --project-name noter --env-file "$env_file" -f "$app_dir/compose.yml")
export NOTER_API_IMAGE="$image_ref"

wait_for_ready() {
  local attempt
  for ((attempt = 1; attempt <= readiness_attempts; attempt += 1)); do
    if curl --fail --silent --show-error --max-time 5 "$readiness_url" >/dev/null; then
      return 0
    fi
    if (( attempt < readiness_attempts && readiness_interval > 0 )); then
      sleep "$readiness_interval"
    fi
  done
  return 1
}

apply_candidate() {
  "${compose[@]}" config --quiet || return
  "${compose[@]}" pull api || return
  "${compose[@]}" up --detach --remove-orphans || return
  wait_for_ready || return
}

rollback() {
  echo "noter deploy: candidate readiness failed; restoring previous state" >&2
  restore_files
  if [[ -n "$previous_image" ]]; then
    export NOTER_API_IMAGE="$previous_image"
    compose=(docker compose --project-name noter --env-file "$env_file" -f "$app_dir/compose.yml")
    if ! "${compose[@]}" pull api || ! "${compose[@]}" up --detach --remove-orphans || ! wait_for_ready; then
      echo "noter deploy: rollback readiness failed" >&2
      return 1
    fi
    printf '%s\n' "$previous_image" > "$state_file"
    return 0
  fi

  echo "noter deploy: no previous image state was available; stopping the candidate" >&2
  export NOTER_API_IMAGE="$image_ref"
  compose=(docker compose --project-name noter --env-file "$env_file" -f "$app_dir/compose.yml")
  "${compose[@]}" down --remove-orphans || true
  rm -f -- "$state_file"
  return 0
}

if apply_candidate; then
  if ! printf '%s\n' "$image_ref" > "$state_file"; then
    echo "noter deploy: could not record the deployed image; rolling back" >&2
    rollback || true
    exit 1
  fi
  echo "noter deploy: deployed $image_ref"
  exit 0
fi

if ! rollback; then
  exit 1
fi
exit 1
