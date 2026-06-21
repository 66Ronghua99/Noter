# Debug Phone Install Verification

- Timestamp: 2026-06-19 23:08:33 CST
- Branch: `master`
- Synced commit: `7d9470c Improve manual alarm wheels and i18n`
- Pull command: `git pull --ff-only`
- Pull result: fast-forward from `e776662` to `7d9470c`
- Build command: `./gradlew assembleDebug`
- Build result: `BUILD SUCCESSFUL in 22s`
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- APK size: `14M`
- Device: `10AE4K0Z61003KU` (`model:V2307A`, `device:PD2307`)
- Install command: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Install result: `Success`
- Launch command: `adb shell monkey -p com.cory.noter -c android.intent.category.LAUNCHER 1`
- Foreground confirmation: `com.cory.noter/com.cory.noter.MainActivity`
- Final foreground recheck: relaunched after a transient system clock foreground state; confirmed `com.cory.noter/.MainActivity` in focus.
