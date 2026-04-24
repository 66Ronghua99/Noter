# Manual Evidence

- Device or emulator: `Medium_Phone_API_36.0` AVD
- Android version: `16`
- Notification permission path observed: app `Settings` screen shows a `Notifications` guidance row; this MVP surfaces guidance instead of auto-launching the platform prompt on first open.
- Exact alarm permission path observed: app `Settings` screen shows an `Exact alarms` guidance row for the platform settings path.
- One-time alarm fired at: not manually captured end-to-end on the emulator during this run.
- One-time alarm removed after stop: not manually captured end-to-end on the emulator during this run.
- Repeating alarm fired at: not manually captured end-to-end on the emulator during this run.
- Repeating alarm remained after stop: not manually captured end-to-end on the emulator during this run.
- Notes:
  - Connected instrumentation smoke tests passed on the same emulator via `./gradlew connectedDebugAndroidTest`.
  - The ringing cleanup behavior is covered by unit tests in `AlarmRingingCoordinatorTest`.
  - During manual emulator interaction, the app home screen and settings screen rendered correctly. Screenshots were captured at:
    - `artifacts/2026-04-23-ai-alarm-android/home.png`
    - `artifacts/2026-04-23-ai-alarm-android/settings.png`
  - I attempted a fully manual one-time alarm setup on the emulator, but adb text injection into the editor's date/time fields became unstable after an earlier emulator `System UI isn't responding` interruption, so I am not claiming a manual ringing observation that I did not actually complete.
