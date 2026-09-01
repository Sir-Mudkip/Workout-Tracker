# Building and running

## The wrapper

`gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` are
committed, pinning Gradle 8.7. A fresh clone builds from the command line
without opening Android Studio first.

They were absent until v0.3.0, which meant the release workflow — whose
first step is `chmod +x ./gradlew` — could never run. Committing the
wrapper is what a CI job needs; do not remove it.

## Use JDK 21, not Studio's bundled JBR

Android Studio ships a JetBrains Runtime at
`/var/lib/flatpak/app/com.google.AndroidStudio/current/active/files/extra/jbr`
which is currently **Java 25**. Gradle 8.7 does not support Java 25 and
fails with a bare version number as the entire error message:

```
* What went wrong:
25.0.2
```

That is the whole message — no explanation, no mention of Java. Use the
JDK 21 Studio downloads alongside it (`~/.jdks/jbr-21.0.11`).

## Commands

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # APK -> app/build/outputs/apk/debug/
./gradlew installDebug        # install to a running device or emulator
```

## Installing over a Studio build fails

If the app was last installed by Android Studio, a CLI `installDebug`
fails:

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package
com.luke.workouttracker signatures do not match newer version
```

Studio's Flatpak sandbox uses a different debug keystore than a CLI
build. The only way to force it is `adb uninstall`, **which deletes the
app database** — including whatever data a migration was about to be
tested against (see [`database.md`](./database.md)).

Install from Android Studio instead. This is not a papercut to work
around; it is the reason schema changes get verified in Studio.

## Emulator on Wayland with the Flatpak Studio

If Android Studio was installed as a Flatpak and the session is Wayland,
the emulator dies immediately with only:

> The emulator process for AVD `<name>` has terminated.

The real error appears when the emulator is run inside the sandbox
directly:

```
Could not find the Qt platform plugin "wayland"
could not connect to display
Fatal: no Qt platform plugin could be initialized
Available platform plugins are: offscreen, linuxfb, minimal, xcb, vnc
```

The emulator's bundled Qt ships **no Wayland plugin**, so it needs X11.
Flatpak grants Android Studio `fallback-x11`, which hands over the X11
socket *only when Wayland is unavailable* — so on a Wayland session it
is deliberately withheld, and Qt aborts.

Grant X11 explicitly, once:

```bash
flatpak override --user --socket=x11 com.google.AndroidStudio
```

Restart Studio afterwards so the new permission is picked up. The same
setup works untouched on an X11 session, which is why this can look
machine-specific when it is not.

Two related traps:

- **`hw.gpu.mode=software`** in an AVD's `config.ini` routes rendering
  through Mesa's lavapipe software Vulkan driver, which segfaults. Set
  `hw.gpu.mode=host`, or in Studio: *Device Manager → Edit → Show
  Advanced Settings → Graphics: Hardware*.
- **Stale `.lock` files.** After a crash, `hardware-qemu.ini.lock` and
  `multiinstance.lock` remain in the `.avd` directory and the next launch
  reports the AVD is already running. Delete them once no emulator
  process is alive.

Debugging any emulator failure starts by running it from the CLI, since
the dialog never carries the actual error:

```bash
~/Android/Sdk/emulator/emulator -avd <name> -no-audio -no-snapshot
coredumpctl info        # if it crashed rather than exiting
```

## Releases

`.github/workflows/release.yml` triggers on a `v*` tag, builds a signed
release APK, and publishes it as a GitHub Release. The app then offers
that build to itself — see Updates in
[`architecture.md`](./architecture.md).

```bash
git tag -a v0.3.1 -m "..."
git push origin v0.3.1
gh run watch $(gh run list --workflow=release.yml --limit 1 --json databaseId --jq '.[0].databaseId')
```

The tag must start with `v`. `UpdateChecker` strips that prefix and
compares the rest numerically against `BuildConfig.VERSION_NAME`, so a
tag that parses to no integers — `beta-tag`, for example — makes the app
report "up to date" forever. Bump `versionCode` and `versionName` in
`app/build.gradle.kts` in the same commit as the tag.

### Signing

Four repository secrets, set once (`INSTALL.md:89`):

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_B64` | `base64 -w0 release.keystore` |
| `RELEASE_KEYSTORE_PASSWORD` | store password |
| `RELEASE_KEY_ALIAS` | `workout-tracker` |
| `RELEASE_KEY_PASSWORD` | key password |

`app/build.gradle.kts` reads these from the environment and **falls back
to the debug keystore when they are absent**, so a misconfigured release
build produces a debug-signed APK rather than failing. A wrong password
does fail, with `KeytoolException: keystore password was incorrect`.

Verify a keystore before trusting a secret:

```bash
~/.jdks/jbr-21.0.11/bin/keytool -list -keystore release.keystore
```

`keytool` ships with any JDK; there is one at `~/.jdks/` and another
inside the Android Studio Flatpak.

**Losing the keystore is unrecoverable.** Android refuses updates signed
with a different key, so every future release must use the same file.
Back it up off the machine.

### Release builds cannot update a debug install

A debug-signed install cannot be updated by a release-signed APK — the
signatures differ, and Android rejects the update. Moving from a local
Studio build to a CI release requires uninstalling first, which **deletes
the app database**.

JSON export carries programs only, not set logs
([`json-format.md`](./json-format.md)), so training history does not
survive that switch. There is currently no way to export it.
