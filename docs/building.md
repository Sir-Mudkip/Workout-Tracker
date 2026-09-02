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

## Signature mismatches between build sources

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` means the installed app and the new
APK were signed with different keys. The only way to force it is
`adb uninstall`, **which deletes the app database** — including whatever
data a migration was about to be tested against (see
[`database.md`](./database.md)).

Studio's Flatpak sandbox keeps its own debug keystore at
`~/.var/app/com.google.AndroidStudio/config/.android/debug.keystore`,
separate from the CLI's `~/.android/debug.keystore`. Those two producing
different signatures is what makes a CLI install fail over a Studio build
and vice versa.

The fix in this project is that all three sources share one key: the CLI
keystore is a copy of Studio's, and CI signs releases with the same file
(see Signing below). Keep it that way — the alternative is an uninstall,
and an uninstall costs the training history, which JSON export does not
cover.

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

### Versioning

Three things must agree, and nothing checks them:

- `versionName` in `app/build.gradle.kts` — what the user sees, and what
  `UpdateChecker` compares numerically.
- `versionCode` — an integer Android uses internally. It must increase
  every release or the install fails with no useful message.
- The **tag** — `versionName` with a `v` prefix. Tag `v0.3.2` for
  `versionName = "0.3.2"`.

A tag that parses to no integers, such as `beta-tag`, makes the updater
report "up to date" forever.

### Installing during development

`adb` from the host works with the Flatpak Android Studio — the SDK lives
outside the sandbox at `~/Android/Sdk`. A distrobox container is not
needed.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` keeps existing data. The phone must be in **File transfer** USB mode;
charge-only mode does not enumerate at all, and `lsusb | grep 18d1` is the
quickest way to tell a cable or mode problem from an `adb` one.

Debug builds are signed with `~/.android/debug.keystore`, which is
**deliberately a copy of Android Studio's** so that CLI builds, Studio
builds and CI releases all share one key. Without that, each source
produces a different signature and none can update the others.

### Signing

Four repository secrets, set once:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_B64` | `base64 -w0 <keystore>` |
| `RELEASE_KEYSTORE_PASSWORD` | store password |
| `RELEASE_KEY_ALIAS` | key alias |
| `RELEASE_KEY_PASSWORD` | key password |

**Releases are signed with Android Studio's debug keystore**, copied to
`~/keystore/workout-signing.keystore` (alias `androiddebugkey`, password
`android`, valid until 2056). That is deliberate: the installed app was
signed with that key, and Android refuses an update signed with any
other one. Using it keeps in-app updates working without uninstalling
and destroying the training history, which JSON export cannot preserve.

The cost is a well-known password. The key material is still unique to
this machine, so it is not forgeable — but anyone who obtains the file
can sign an update. Acceptable for a single-user app installed from its
own GitHub releases; not acceptable if this is ever distributed more
widely, at which point the fix is `apksigner` key rotation with a
signing lineage rather than swapping the key outright.

**Back that keystore up.** It lives in the Flatpak config directory
originally, and a Flatpak reset would destroy it. Losing it means never
being able to update an installed copy again.

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

### Play Protect blocks the install

An in-app update can download correctly and then fail with the installer's
generic **"App not installed"**. Play Protect blocks sideloaded APKs, and
the message says nothing about why.

Play Store → profile → **Play Protect** → gear → turn off *Scan apps with
Play Protect*, install, then turn it back on.

The same block appears over adb as
`INSTALL_FAILED_VERIFICATION_FAILURE`, which is worth remembering because
that variant at least names the cause. For a one-off adb install:

```bash
adb shell settings put global verifier_verify_adb_installs 0
adb install -r app.apk
adb shell settings delete global verifier_verify_adb_installs
```

Before blaming Play Protect, rule out the causes that produce the same
message: a signature mismatch with the installed build, a `versionCode`
that is not higher, or a different `applicationId`.

### Release builds cannot update a debug install

A debug-signed install cannot be updated by a release-signed APK — the
signatures differ, and Android rejects the update. Moving from a local
Studio build to a CI release requires uninstalling first, which **deletes
the app database**.

JSON export carries programs only, not set logs
([`json-format.md`](./json-format.md)), so training history does not
survive that switch. There is currently no way to export it.
