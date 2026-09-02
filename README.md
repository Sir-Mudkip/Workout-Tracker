# Workout Tracker

Local-only Android app to author multi-week workout programs, log set-by-set during workouts, and chart per-exercise volume progress.

## Setup (one-time)

1. Install Android Studio (bundles JDK + SDK + emulator):
   - Flatpak: `flatpak install flathub com.google.AndroidStudio`
   - Or tarball: https://developer.android.com/studio
2. On first launch, install **Android SDK Platform 34**, **Build Tools 34.0.0**, **Platform Tools**, and create an AVD (e.g. Pixel 7, API 34).
3. Add to `~/.bashrc`:
   ```bash
   export ANDROID_HOME=$HOME/Android/Sdk
   export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator
   ```
4. Open this directory in Android Studio — it will auto-generate the Gradle wrapper (`gradlew`, `gradle-wrapper.jar`) on first sync.

## Build & run

```bash
./gradlew assembleDebug         # build APK
./gradlew installDebug          # install to running emulator/device
./gradlew test                  # unit tests
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Install on a phone

Build the APK, then move it across:

1. Android Studio: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**, then
   click **locate** in the notification. Or `./gradlew assembleDebug`, which
   writes to `app/build/outputs/apk/debug/app-debug.apk`.
2. Plug the phone in and set the USB mode to **File transfer**, not charging
   only — a charge-only cable or mode is invisible to the computer.
3. Copy the APK to the phone's `Downloads/`, open it in the Files app, and
   allow installs from that source when prompted.

For repeated installs, `adb` avoids the copying:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` keeps your existing data. Enable **Developer options → USB debugging** on
the phone first (tap *Build number* seven times in About phone), and accept the
authorisation prompt.

Once a signed release exists, the app updates itself from GitHub Releases
instead — see [`docs/building.md`](docs/building.md).

## Bulk JSON import

See `samples/ppl-6week.json` for the schema. From the Program list → "Import JSON" → paste contents.

## Architecture

- **UI**: Jetpack Compose + Material 3, single-activity, Navigation-Compose
- **DI**: Hilt
- **Persistence**: Room (SQLite)
- **JSON**: kotlinx.serialization
- **Progress**: per-week volume bars (no external chart lib — easy to swap in Vico/MPAndroidChart later)

## Documentation

Maintainer documentation is in [`docs/`](docs/README.md) — architecture, the
data model, database migrations, build and emulator setup, testing, and the
program JSON schema.

Contributors and agents should read [`CLAUDE.md`](CLAUDE.md) (also `AGENTS.md`)
for the rules, and [`CONTRIBUTING.md`](CONTRIBUTING.md) to get started.
