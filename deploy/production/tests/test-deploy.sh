#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
production_dir="$(cd -- "$script_dir/.." && pwd)"
deploy_script="$production_dir/deploy.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "$tmp_dir"' EXIT

fake_bin="$tmp_dir/bin"
mkdir -p "$fake_bin"
docker_log="$tmp_dir/docker.log"
curl_count="$tmp_dir/curl.count"

cat > "$fake_bin/docker" <<'FAKE_DOCKER'
#!/usr/bin/env bash
set -Eeuo pipefail
printf 'image=%s args=%s\n' "${NOTER_API_IMAGE:-}" "$*" >> "$FAKE_DOCKER_LOG"
FAKE_DOCKER
chmod +x "$fake_bin/docker"

cat > "$fake_bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -Eeuo pipefail
count=0
if [[ -f "$FAKE_CURL_COUNT" ]]; then
  count="$(<"$FAKE_CURL_COUNT")"
fi
count=$((count + 1))
printf '%s\n' "$count" > "$FAKE_CURL_COUNT"
if [[ "${FAKE_CURL_MODE:-success}" == "fail-first" && "$count" == 1 ]]; then
  exit 22
fi
exit 0
FAKE_CURL
chmod +x "$fake_bin/curl"

new_sha=1111111111111111111111111111111111111111
old_sha=2222222222222222222222222222222222222222

make_fixture() {
  local fixture="$tmp_dir/$1"
  mkdir -p "$fixture/candidate"
  printf 'NOTER_API_DOMAIN=localhost\nNOTER_CLIENT_TOKEN=fixture-secret\n' > "$fixture/.env"
  printf 'ghcr.io/example/noter-api:%s\n' "$old_sha" > "$fixture/.image-reference"
  printf 'old-compose\n' > "$fixture/compose.yml"
  printf 'old-caddy\n' > "$fixture/Caddyfile"
  printf 'candidate-compose\n' > "$fixture/candidate/compose.yml"
  printf 'candidate-caddy\n' > "$fixture/candidate/Caddyfile"
  printf '%s\n' "$fixture"
}

fixture="$(make_fixture deploy)"
export PATH="$fake_bin:$PATH"
export FAKE_DOCKER_LOG="$docker_log"
export FAKE_CURL_COUNT="$curl_count"
export NOTER_DEPLOY_DIR="$fixture"
export NOTER_CANDIDATE_DIR="$fixture/candidate"
export NOTER_ENV_FILE="$fixture/.env"
export NOTER_IMAGE_STATE_FILE="$fixture/.image-reference"
export NOTER_READINESS_URL=http://127.0.0.1/health/ready
export NOTER_READINESS_ATTEMPTS=1
export NOTER_READINESS_INTERVAL_SECONDS=0

"$deploy_script" "ghcr.io/example/noter-api:$new_sha"
grep -Fxq "ghcr.io/example/noter-api:$new_sha" "$fixture/.image-reference"
grep -Fxq 'candidate-compose' "$fixture/compose.yml"
grep -Fxq 'candidate-caddy' "$fixture/Caddyfile"
grep -Fq 'fixture-secret' "$fixture/.env"
! grep -Fq 'fixture-secret' "$docker_log"

rm -f "$docker_log" "$curl_count"
printf 'ghcr.io/example/noter-api:%s\n' "$old_sha" > "$fixture/.image-reference"
printf 'old-compose\n' > "$fixture/compose.yml"
printf 'old-caddy\n' > "$fixture/Caddyfile"
export FAKE_CURL_MODE=fail-first
if "$deploy_script" "ghcr.io/example/noter-api:$new_sha"; then
  echo 'rollback test unexpectedly succeeded' >&2
  exit 1
fi
grep -Fxq "ghcr.io/example/noter-api:$old_sha" "$fixture/.image-reference"
grep -Fxq 'old-compose' "$fixture/compose.yml"
grep -Fxq 'old-caddy' "$fixture/Caddyfile"
grep -Fq "image=ghcr.io/example/noter-api:$new_sha" "$docker_log"
grep -Fq "image=ghcr.io/example/noter-api:$old_sha" "$docker_log"

if "$deploy_script" 'ghcr.io/example/noter-api:mutable'; then
  echo 'invalid image reference unexpectedly succeeded' >&2
  exit 1
fi

echo 'deploy script tests passed'
