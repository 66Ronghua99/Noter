# 2026-06-26 Voice Debug Verification

## Chinese ASR Language Routing

- The language-hint-only APK still sent `language=zh` but `nvidia/parakeet-tdt-0.6b-v3` transcribed Chinese speech as English, e.g. `So we need around Julian and now`.
- The fix adds `qwen/qwen3-asr-flash-2026-02-10` as the Chinese default ASR route when the stored setting is still the generic default `nvidia/parakeet-tdt-0.6b-v3`.
- Connected vivo evidence after reinstall:
  - `voice.remoteAsr.request model=qwen/qwen3-asr-flash-2026-02-10 selectedModel=nvidia/parakeet-tdt-0.6b-v3 language=zh`
  - `asr.response.transcribed ... transcriptPreview=定一个明天早上九点的闹。`
  - `Worker result SUCCESS` for the first Qwen-routed voice alarm creation.
- A second Qwen-routed attempt also transcribed Chinese (`设置一下，，明天还有后天早上九点的闹钟。`) but the downstream DeepSeek chat completion hit `Software caused connection abort`; that is a network/LLM create-stage issue, not an ASR language issue.

## Evidence

- `artifacts/2026-06-26-voice-debug/qwen-language-routing-logcat.log`
- `artifacts/2026-06-26-voice-debug/qwen-language-routing-jobscheduler.txt`
- `artifacts/2026-06-26-voice-debug/qwen-language-routing-dumpsys-alarm.txt`

## Local Gate

- `./gradlew --no-daemon --console=plain testDebugUnitTest lintDebug assembleDebug` passed.
