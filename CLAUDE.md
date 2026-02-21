# AccBot — Project Notes

## Video Recording for Google Play (Foreground Service Demo)

### Environment Setup
- **JAVA_HOME**: `/c/Program Files/Android/Android Studio/jbr` (JDK 21 bundled with Android Studio)
- **ADB**: `$LOCALAPPDATA/Android/Sdk/platform-tools`
- Both must be on PATH for Gradle and adb commands to work

### How to Record the Video

**Single command — run both tests together in one Gradle invocation:**
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$LOCALAPPDATA/Android/Sdk/platform-tools:$PATH"
cd accbot-android

./gradlew connectedAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.accbot.dca.recording.EmulatorSetupTest,com.accbot.dca.recording.ForegroundServiceDemoTest'
```

**Pull the video:**
```bash
adb pull "//sdcard/Movies/foreground_service_data_sync_demo.mp4" foreground_service_data_sync_demo.mp4
```

### Critical Rules (Learned the Hard Way)

1. **Both tests MUST run in a single Gradle invocation** (comma-separated classes).
   - Gradle reinstalls APKs on each `connectedAndroidTest` run, wiping all app data (SharedPreferences, DB, permissions).
   - EmulatorSetupTest sets up onboarding, sandbox mode, and credentials. If ForegroundServiceDemoTest runs in a separate invocation, all that setup is gone.

2. **Permissions must be granted programmatically before Activity launch.**
   - `GrantPermissionRule` from `androidx.test:rules` is NOT in the project dependencies — don't use it.
   - Instead, use a custom `TestWatcher` rule (order before ComposeTestRule) that calls:
     ```kotlin
     device.executeShellCommand("pm grant com.accbot.dca android.permission.POST_NOTIFICATIONS")
     device.executeShellCommand("dumpsys deviceidle whitelist +com.accbot.dca")
     ```
   - Without this, `MainActivity.onCreate()` triggers a notification permission dialog and battery optimization Settings activity, which blocks the Compose hierarchy and causes "No compose hierarchies found" errors.

3. **`onNodeWithText("Create DCA Plan")` is ambiguous** — matches both the TopAppBar title and the button (both use the same string resource). Use `onNode(hasText("Create DCA Plan") and hasClickAction())` instead.

4. **adb paths on Git Bash (Windows)** — use `//sdcard/...` (double slash) to prevent Git Bash from converting `/sdcard/` to a Windows path like `C:/Program Files/Git/sdcard/`.

5. **No force-stop needed between tests** when running in a single invocation. The ComposeTestRule launches a fresh Activity instance that reads the updated preferences.
