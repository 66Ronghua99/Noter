# Noter First-Party API Backend Design

Date: 2026-07-18

Status: Approved

## 1. Purpose

Replace the Cloudflare Worker chat path and direct client-side OpenRouter access with a first-party Noter backend hosted on the user's Linux server.

The backend proxies both agent chat completions and ASR. Android retains the agent loop and all local alarm, permission, scheduling, and calendar tools. Model selection, upstream endpoints, upstream credentials, and cost controls move entirely to the server.

This design also removes obsolete AI provider configuration from Android persistence and UI. Users submit text or voice data without choosing a model or entering an API key.

## 2. Repository Baseline

The previous Cloudflare Worker migration is not part of the new implementation baseline.

- Local dev, origin/dev, local master, and origin/master all point to 147c650.
- The previous Worker commit and its follow-up local changes are preserved on the local backup branch backup/dev-cloudflare-worker-before-backend-20260718.
- The new design and implementation start from master commit 147c650.

The backend lives in the same repository as the Android client, under server/noter-api. Production deployment files live under deploy/production.

## 3. Goals

1. Proxy Noter agent chat requests through a first-party HTTPS API.
2. Make server ASR the only production speech-recognition path.
3. Keep the Android AgentLoopRunner and local tools unchanged in ownership.
4. Keep all upstream model, key, endpoint, timeout, and token-budget choices on the server.
5. Remove all user-facing OpenRouter key, Chat model, and ASR model configuration.
6. Define explicit user-facing, model-facing, silent App, and server-only failure ownership.
7. Package the backend with Docker Compose and Caddy.
8. Automatically deploy server changes after they enter master, once production SSH configuration is supplied.
9. Leave a clean boundary for future login and user-data services without implementing them now.

## 4. Non-Goals

The first version does not include:

- User registration, login, sessions, or account recovery.
- User, alarm, transcript, or application-data persistence on the server.
- PostgreSQL, Redis, queues, or a management dashboard.
- Billing, quotas by user, analytics, or usage history.
- Multiple-provider fallback or automatic model fallback.
- Streaming Chat responses or batch APIs.
- Moving AgentLoopRunner or Android tool execution to the server.
- Browser clients or general-purpose OpenAI proxy compatibility.
- Play Integrity, App Check, or device attestation.

## 5. System Architecture

~~~text
Android
  - AgentLoopRunner
  - local alarm, scheduling, permission, and calendar tools
  - m4a recording
  - no model or upstream key
        |
        | HTTPS + Bearer client token
        v
Caddy
  - public domain and TLS
  - forwards only to the private API container
        |
        v
Fastify API
  - authentication
  - strict request validation
  - request IDs, limits, and sanitized logs
  - Chat service and provider adapter
  - ASR service and provider adapter
        |
        | server-selected endpoint, model, and key
        v
OpenAI/OpenRouter-compatible upstream
~~~

The Android client sends only the data required to perform the current operation. The API adds all provider-specific configuration and normalizes provider responses into stable Noter response types.

Chat and ASR have independent upstream configurations. They may point to the same provider or different providers without changing the Android contract.

The first production deployment is a single API instance. Horizontal scaling and shared distributed rate limiting are deferred until traffic requires them.

## 6. Backend Technology And Boundaries

The backend uses TypeScript and Fastify.

Expected structure:

~~~text
server/noter-api/
  src/
    app/
    config/
    plugins/
    providers/
    routes/
    services/
  test/
  Dockerfile
  package.json

deploy/production/
  compose.yml
  Caddyfile
  deploy.sh

.github/workflows/
  server-deploy.yml
~~~

Responsibilities:

- config validates required environment at process startup.
- plugins own authentication, request IDs, limits, and error serialization.
- routes own HTTP schemas and translate HTTP input into service calls.
- services own Noter request policy and response normalization.
- providers own upstream request and response formats.
- no route accesses environment variables or calls an upstream provider directly.

Fastify structured logging is permitted only through the sanitized logging policy in this design.

## 7. Public API Contract

All API request schemas are strict. Unknown fields are rejected with HTTP 422. In particular, client-provided model, apiKey, baseUrl, maxTokens, timeout, and provider fields are rejected rather than ignored.

All protected routes require:

~~~http
Authorization: Bearer <NOTER_CLIENT_TOKEN>
~~~

The token is a temporary pre-login access gate embedded at build time. It is not treated as user identity or a durable secret.

### 7.1 Agent Chat

Endpoint:

~~~http
POST /api/v1/agent/completions
Content-Type: application/json
~~~

Request shape:

~~~json
{
  "messages": [
    {
      "role": "system",
      "content": "..."
    }
  ],
  "tools": [
    {
      "name": "create_alarm",
      "description": "...",
      "parameters": {}
    }
  ],
  "toolChoice": {
    "mode": "required"
  }
}
~~~

Supported toolChoice modes are auto, required, and named. named requires a toolName. Message roles and tool-call linkage follow the existing AgentMessage domain contract.

The server constructs the upstream request, injects CHAT_UPSTREAM_MODEL, forces parallel tool calls off, and enforces the server output-token cap.

Successful response:

~~~json
{
  "requestId": "req_...",
  "message": {
    "role": "assistant",
    "content": "",
    "toolCalls": [
      {
        "id": "call_...",
        "name": "create_alarm",
        "arguments": "{}"
      }
    ]
  }
}
~~~

The Chat request limit is 256 KiB. Streaming is not supported.

### 7.2 ASR

Endpoint:

~~~http
POST /api/v1/asr/transcriptions
Content-Type: multipart/form-data
~~~

Fields:

- audio: required m4a audio file.
- language: optional BCP-47 language hint supplied by the App locale.

The client does not send an ASR model. The first provider adapter converts the binary upload into the current OpenRouter-compatible JSON input_audio request with server-selected model and credentials. A later provider adapter may use a different upstream encoding without changing this public endpoint.

Successful response:

~~~json
{
  "requestId": "req_...",
  "text": "wake me at eight"
}
~~~

The audio limit is 10 MiB. The server releases request audio after the upstream call and never persists it.

### 7.3 Health

- GET /health/live confirms that the process is running.
- GET /health/ready confirms that all required configuration is valid and the service can accept requests.

Health routes do not require authentication and reveal only a status value. Readiness does not call a paid upstream API.

## 8. Server Configuration

Required production configuration:

- NOTER_CLIENT_TOKEN
- CHAT_UPSTREAM_URL
- CHAT_UPSTREAM_API_KEY
- CHAT_UPSTREAM_MODEL
- ASR_UPSTREAM_URL
- ASR_UPSTREAM_API_KEY
- ASR_UPSTREAM_MODEL
- NOTER_API_DOMAIN

Explicit defaults:

- CHAT_MAX_OUTPUT_TOKENS: 512
- CHAT_UPSTREAM_TIMEOUT_MS: 60000
- ASR_UPSTREAM_TIMEOUT_MS: 60000
- CHAT_RATE_LIMIT_PER_MINUTE: 30 per source IP
- ASR_RATE_LIMIT_PER_MINUTE: 10 per source IP
- CHAT_MAX_CONCURRENCY: 8
- ASR_MAX_CONCURRENCY: 4
- NOTER_LOG_LEVEL: info

Missing required configuration causes process startup to fail with a specific server log. The service does not substitute a model, switch provider, or silently disable a route.

Production secrets live only in /opt/noter/.env on the server. The deployment workflow never prints or overwrites that file.

## 9. Security And Privacy

- Caddy is the only public listener. The API container port is private to the Compose network.
- DNS points directly to the user's server and does not use Cloudflare.
- HTTPS is mandatory in production.
- The client token is compared without value-dependent early exit.
- Upstream URLs come only from validated server configuration, preventing client-controlled SSRF.
- Chat and ASR enforce separate body, rate, and concurrency limits.
- Fastify trusts forwarding headers only from the private Caddy network, so clients cannot forge the source IP used for rate limits.
- Browser CORS is not enabled.
- The server stores no user, text, audio, transcript, message, tool-argument, or response data.
- Logs exclude Authorization headers, upstream keys, prompts, messages, tool arguments, audio, and transcripts.
- Logs may contain request ID, route, status, latency, response size, and sanitized upstream status.
- Caddy access logging is disabled unless its format is explicitly configured to omit Authorization and query data.
- Provider response bodies are never returned inside errors.

The static App token can be extracted from an APK. It reduces casual public misuse but is not final authentication. Future login replaces it with user or device-scoped short-lived credentials.

## 10. Error Contract

All errors use:

~~~json
{
  "error": {
    "code": "upstream_timeout",
    "message": "The model service timed out.",
    "requestId": "req_...",
    "retryable": true
  }
}
~~~

Stable error codes include:

- unauthorized
- invalid_request
- payload_too_large
- rate_limited
- upstream_rejected
- upstream_unavailable
- upstream_timeout
- invalid_upstream_response
- service_unavailable
- internal_error

The App maps error.code to localized UI text. It does not render the server message, raw HTTP status, provider name, model name, or upstream response to the user.

### 10.1 Error Ownership

User-visible failures:

- Empty text input.
- Recording too long or upload too large.
- Final service busy or unavailable result after bounded retries.
- Invalid or expired App token, phrased only as a non-actionable service-unavailable result rather than a request to configure credentials.
- App/server contract incompatibility, phrased as an update requirement.
- User ambiguity returned through reject_unclear_request.
- Local permission recovery, scheduling failure, and calendar partial failure.
- Final invalid AI result, phrased without provider details.

Model-visible failures:

- Only structured business or tool validation errors that can be corrected safely.
- Invalid tool arguments may receive one corrective model turn only when no local write has committed.
- Authentication, network, DNS, rate-limit, timeout, server configuration, and provider diagnostics never enter model messages.

Silently handled by the App:

- One bounded foreground retry for retryable transport or service errors.
- Up to three total WorkManager attempts with exponential backoff.
- Retry-After handling for rate limiting.
- Mapping backend error codes to domain failures and localized UI.
- Preserving a committed local tool result when a later model finalization call fails.
- Suppressing intermediate background failure notifications.

Server-only diagnostics:

- Invalid server configuration.
- Upstream status and timeout category.
- Contract parse details.
- Sanitized stack traces and request IDs.

The backend itself does not retry paid upstream POST requests. Retry ownership remains in the App, where committed local tool state is known.

Once a local write tool commits, the App must never rerun the full agent request. Existing committed-result precedence remains authoritative so a later model or network failure cannot duplicate or hide a completed alarm operation.

## 11. Android Client Changes

### 11.1 Network Boundary

- Replace OpenRouterAgentClient with a Noter Agent API client implementing AgentLlmGateway.
- Replace OpenRouterAsrClient and OpenRouterVoiceAsrTranscriber with a Noter ASR client implementing RemoteAsrTranscriber.
- Add NOTER_API_BASE_URL and NOTER_CLIENT_TOKEN as BuildConfig inputs.
- Release builds fail when either value is absent.
- GitHub Actions provides the release URL through a repository variable and the token through a repository secret.
- Local development may provide both values through environment variables or untracked local.properties.

The client no longer sends model IDs or provider credentials.

### 11.2 Voice Flow

Production voice flow is:

~~~text
press and hold
  -> record m4a
  -> release
  -> upload directly to Noter ASR
  -> receive transcript
  -> enqueue local AgentLoopRunner work
~~~

Android SpeechRecognizer is removed from the production path. System speech recognition is not attempted before remote ASR and is not retained as an automatic fallback.

Remote ASR failure receives the bounded retry policy. Final failure offers record-again and text-input recovery actions.

### 11.3 Settings And Frontend Reduction

Remove the entire Settings > AI and voice row and detail destination because every control on that page becomes server-owned.

Remove:

- OpenRouter API key input and save action.
- Chat model and ASR model selectors.
- Provider and selected-model summaries.
- AI/voice navigation route, screen callbacks, UI state, test tags, and strings.
- Selected-model state and display from AI creation UI.
- Missing-key and missing-model user errors.
- OpenRouter and ASR model lists from Android.

Do not add a replacement provider-status or server-managed placeholder card. Service errors appear only in the active Chat or voice context.

Retain:

- Voice recording, processing, retry, and text fallback UI.
- Microphone, notification, exact-alarm, calendar, and other device-owned settings.
- Localized, actionable service-unavailable messages.

The Settings page subtitle and project documentation must no longer imply that users configure AI behavior, provider credentials, or models.

### 11.4 Local Data Migration

Remove openRouterApiKey, selectedModelId, and selectedAsrModelId from AppSettings and SettingsRepository.

DataStoreSettingsRepository performs an idempotent migration that removes only these legacy preference keys:

- open_router_api_key
- selected_model_id
- selected_asr_model_id

The migration preserves ringtone, calendar, theme, and all unrelated preferences. Tests prove that a previously stored API key is physically removed after migration.

## 12. Retry And Notification Semantics

- Foreground Chat and ASR perform at most one additional automatic attempt.
- Background AI work uses at most three total WorkManager attempts with exponential backoff.
- HTTP 401, 403, 413, 422, invalid upstream response, and local business failures are not retried automatically.
- HTTP 429 is retried only according to Retry-After and the attempt limit.
- HTTP 502, 503, and 504 map to retryable service failures.
- Intermediate background attempts keep the in-progress notification and do not post a failure notification.
- The final attempt posts exactly one terminal success, partial-success, or failure notification.
- A committed alarm or management result always wins over a later finalization failure.

## 13. Deployment Design

### 13.1 Trigger

server-deploy.yml runs on:

- A push to master that changes server/noter-api/**, deploy/production/**, or the workflow itself.
- workflow_dispatch for a manual redeploy.

Android-only master changes do not redeploy the backend.

The workflow uses a production concurrency group and does not allow overlapping deployments.

Build, test, and image publication run as soon as the workflow exists. The SSH deployment job runs only when the repository variable PRODUCTION_DEPLOY_ENABLED equals true. It remains false or unset until the server, DNS, secrets, and known-host entry have been provisioned, so the first merge cannot attempt an unconfigured production deployment.

### 13.2 Build And Publish

The workflow:

1. Runs npm clean install.
2. Runs formatting check, lint, typecheck, and backend tests.
3. Builds the production Docker image.
4. Pushes the image to GHCR with the exact Git commit SHA.
5. Does not deploy a mutable latest tag.

### 13.3 Server Deployment

The production server uses /opt/noter for:

- compose.yml
- Caddyfile
- deploy.sh
- .env
- the current immutable image reference

The workflow copies versioned deployment files to a staging location, then connects over SSH and invokes deploy.sh with the exact image reference.

deploy.sh:

1. Validates the image reference and required local files.
2. Records the currently running image.
3. Pulls the requested image.
4. Applies Docker Compose.
5. Waits for /health/ready.
6. Keeps the new image only after health succeeds.
7. Restores the previous image and Compose state when health fails.
8. Exits non-zero after rollback so GitHub reports deployment failure.

The server does not clone the repository, run npm install, or build source.

### 13.4 Deployment Credentials

Production activation requires:

- DEPLOY_HOST
- DEPLOY_PORT
- DEPLOY_USER
- DEPLOY_SSH_PRIVATE_KEY
- DEPLOY_KNOWN_HOSTS

The deployment user is non-root and limited to the Noter deployment directory and required Docker operations. GHCR read credentials are configured once on the server if the image is private.

Upstream API keys are not GitHub deployment secrets. They remain in the server .env.

The workflow and local deployment package are implemented and tested before credentials are available. After the user supplies server access, the server is bootstrapped, deployment secrets are configured, and PRODUCTION_DEPLOY_ENABLED is set to true. The first real deployment, DNS verification, HTTPS verification, and remote smoke test then complete the production activation.

## 14. Verification

### 14.1 Backend

- Unit tests for strict schemas, authentication, config validation, and error serialization.
- Route tests with Fastify injection for Chat, ASR, size limits, and health.
- Provider tests for model/key injection, tool-call parsing, transcript parsing, upstream failures, and timeout cancellation.
- Privacy tests proving sensitive headers and request content are absent from logs.
- Docker build and Compose configuration validation.
- Local smoke against a fake upstream, requiring no real key.

### 14.2 Android

- Unit tests for Noter Chat and ASR request/response mapping.
- Tests proving no request contains model ID or upstream key.
- Voice coordinator tests proving remote ASR is the first and only recognizer.
- Retry tests for foreground and WorkManager limits.
- Tests proving intermediate background failures do not notify.
- Tests preserving committed tool results without rerunning the agent.
- DataStore migration tests proving exact legacy-key removal and unrelated-setting preservation.
- ViewModel and Compose smoke tests proving provider configuration and model display are absent.

### 14.3 Project Gates

Run fresh:

- npm test, lint, typecheck, and Docker build for server/noter-api.
- ./gradlew testDebugUnitTest
- ./gradlew lintDebug
- ./gradlew assembleDebug
- ./gradlew assembleDebugAndroidTest because Settings and voice Compose smoke coverage changes.
- ./gradlew connectedDebugAndroidTest when a device or emulator is available for end-to-end confirmation.

Record fresh evidence under artifacts.

## 15. Acceptance Criteria

1. The installed App exposes no API key, provider, Chat model, or ASR model configuration or display.
2. Existing OpenRouter credentials and model preferences are removed from DataStore without changing unrelated settings.
3. AgentLoopRunner and all local alarm, permission, scheduling, and calendar tools remain Android-owned.
4. Every production Chat model request goes through POST /api/v1/agent/completions.
5. Every production remote transcription goes directly through POST /api/v1/asr/transcriptions; Android system speech recognition is not attempted.
6. Android requests contain no upstream endpoint, model, API key, token budget, or provider option.
7. The backend rejects unauthorized, unknown-field, oversized, and client-model requests before upstream access.
8. The backend injects independently configured Chat and ASR models and keys.
9. User, model, App, and server error ownership matches Section 10.
10. Foreground and background retry limits match Sections 10 and 12, and no committed local write is duplicated.
11. The backend persists and logs no user text, message content, tool arguments, audio, or transcript.
12. Docker Compose exposes only Caddy publicly and serves valid HTTPS on the configured direct-DNS domain.
13. A qualifying master push builds and publishes an immutable SHA image.
14. After deployment credentials are configured, the workflow deploys that image, verifies readiness, and restores the previous image on failed health.
15. All fresh verification gates in Section 14 pass, or any environment-blocked gate is reported explicitly.

## 16. Future Evolution

Future login adds an auth module and user/device token issuance behind the existing authentication plugin. Future data services add PostgreSQL and repositories without changing the public Chat and ASR contracts or moving Android-local tools.

The temporary static client token is removed when short-lived authenticated credentials are available.
