# OpenRouter Device Debug Evidence

Date: 2026-04-24

## Scope

Debug the connected phone failure around AI alarm creation, where symptoms were described as either network connectivity trouble or `OpenRouter error 503: provider returned error`.

## Findings

- The connected Android device was visible through adb as `10AE4K0Z61003KU` / `V2307A`.
- The app manifest did not explicitly request `android.permission.INTERNET`, which can block OpenRouter calls from the app even when the phone itself has network access.
- Device network check from adb resolved and pinged `openrouter.ai` successfully with `0% packet loss`.
- Device global HTTP proxy was `null`.

## Changes

- Added `android.permission.INTERNET` to the app manifest.
- Added debug-only OpenRouter logcat instrumentation through tag `NoterOpenRouter`.
- Added a regression test that checks the main manifest source declares Internet permission.

## Verification

- RED: `./gradlew testDebugUnitTest --tests com.cory.noter.AndroidManifestPermissionTest` failed before adding the permission.
- GREEN targeted gate: `./gradlew testDebugUnitTest --tests com.cory.noter.AndroidManifestPermissionTest --tests com.cory.noter.ai.OpenRouterClientTest --tests com.cory.noter.di.AppContainerTest` passed.
- Full local gate: `./gradlew testDebugUnitTest lintDebug assembleDebug` passed.
- Device install attempt: `./gradlew installDebug` and `adb install -r -d -g app/build/outputs/apk/debug/app-debug.apk` both reached the device but failed with `INSTALL_FAILED_ABORTED: User rejected permissions`.

## Next Runtime Check

After approving debug APK installation on the phone, reproduce AI creation and watch:

```bash
adb logcat -v time -s NoterOpenRouter
```

Expected useful lines:

- `request.start ... model=... promptChars=... apiKeyChars=...`
- `request.networkFailure type=... message=...`
- `response.remoteFailure code=503 ... reason=... bodyPreview=...`
- `response.success code=200 ... contentChars=...`

## 2026-04-25 Runtime Follow-Up

The connected vivo device already had the debuggable app installed with `android.permission.INTERNET`.

Observed model outcomes from `adb logcat -v time -s NoterOpenRouter`:

- `minimax/minimax-m2.5:free`
  - `request.start` emitted immediately.
  - Response arrived after roughly two minutes.
  - HTTP result was `200`.
  - Assistant content length was `4`, and the UI showed `Invalid JSON`, consistent with a `{}`-style invalid alarm payload.
- `openai/gpt-oss-120b:free`
  - HTTP result was `503`.
  - OpenRouter error reason was `Provider returned error`.
  - Body metadata included upstream `raw=no healthy upstream` and `provider_name=OpenInference`.
- `qwen/qwen3-next-80b-a3b-instruct:free`
  - HTTP result was `429`.
  - OpenRouter error reason was `Provider returned error`.
  - Body metadata said the model was temporarily rate-limited upstream and named provider `Venice`.

Conclusion: this run does not point to device network failure. The app reaches OpenRouter, but the selected free providers are currently unreliable: one is slow and returns invalid assistant JSON, one has no healthy upstream, and one is upstream rate-limited.

## DeepSeek V3.2 Follow-Up

- Confirmed OpenRouter DeepSeek V3.2 model id as `deepseek/deepseek-v3.2`.
- Added it to the built-in model list for paid-model diagnosis.
- Installed a debug build successfully before the phone disconnected.
- Triggered AI creation through a debug-only broadcast instead of `adb shell input text` because the vivo Chinese IME rewrote English text during ADB input.
- Observed `deepseek/deepseek-v3.2` returning HTTP `200` in about 5.6 seconds with non-empty assistant content.
- The flow then reached alarm scheduling and exposed the exact alarm permission problem.

## Background Notification Change

Implemented locally after the device disconnected:

- AI create can now enqueue work into an application-level background scheduler instead of keeping the AI page spinning.
- The app posts an in-progress notification and then a success/failure notification when the background creation finishes.
- Missing exact alarm permission produces an "Alarm saved" notification with an action that opens exact alarm settings.
- Manual and AI creation screens now expose an "Open exact alarm settings" action when scheduling fails because the permission is missing.
- OpenRouter default client has a 60 second total call timeout so background work resolves to a result notification instead of hanging indefinitely.

Fresh local verification:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Result: passed on 2026-04-25 after the phone was no longer connected.

## 2026-04-25 Connected Device Retest

Device: `10AE4K0Z61003KU` / `V2307A`.

Installed the latest debug build after approving vivo's installer risk confirmation. Verified the debug receiver is discoverable:

```bash
adb shell cmd package query-receivers --brief -a com.cory.noter.DEBUG_AI_CREATE com.cory.noter
```

Result:

```text
1 receivers found:
  com.cory.noter/.debug.DebugAiCreateReceiver
```

ADB prompt entry note: prompt/model extras must be quoted inside the remote shell. Without remote quotes, `am broadcast` can split `tomorrow at 8...` and treat `at` as a package argument.

Missing exact-alarm-permission run:

```bash
adb shell 'am broadcast --user 0 --receiver-foreground -a com.cory.noter.DEBUG_AI_CREATE -n com.cory.noter/.debug.DebugAiCreateReceiver --es model "deepseek/deepseek-v3.2" --es prompt "tomorrow at 8 am remind me to take medicine"'
```

Observed logcat:

```text
NoterDebugAiCreate: enqueue.accepted
NoterAiCreateNotify: notify.started
NoterAiCreateNotify: notify.posted id=21001
NoterOpenRouter: request.start ... model=deepseek/deepseek-v3.2 ...
NoterOpenRouter: response.success code=200 ... contentChars=228
NoterAiCreateNotify: notify.result type=MissingSchedulingPermission
NoterAiCreateNotify: notify.posted id=21002
```

Notification manager showed the app posted two notifications and created channel `ai_alarm_creation`. Vivo app settings still report heads-up/keyguard disabled for this app, so notification banners may not visibly pop even though Android accepted the posts.

After enabling exact alarm appop:

```bash
adb shell appops set com.cory.noter SCHEDULE_EXACT_ALARM allow
```

Observed success run:

```text
NoterDebugAiCreate: enqueue.accepted
NoterAiCreateNotify: notify.started
NoterAiCreateNotify: notify.posted id=21001
NoterOpenRouter: response.success code=200 ... contentChars=228
NoterAiCreateNotify: notify.result type=Created
NoterAiCreateNotify: notify.posted id=21002
```

`dumpsys alarm` confirmed the scheduled alarm:

```text
tag=*walarm*:com.cory.noter.alarm.TRIGGER
type=RTC_WAKEUP origWhen=2026-04-26 08:00:00.000 window=0 exactAllowReason=permission
operation=PendingIntent{... com.cory.noter broadcastIntent}
```

Fresh verification after debug receiver/action-filter and notification logging changes:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
git diff --check
```

Result: passed on 2026-04-25.
