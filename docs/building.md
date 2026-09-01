# Building and running

## The wrapper is not committed

There is no `gradlew` or `gradle/wrapper/gradle-wrapper.jar` in this
repository — only `gradle-wrapper.properties`, which pins Gradle 8.7.
Android Studio generates the rest on first project sync.

The practical effect is that a fresh clone cannot build from the command
line until Studio has opened it once. If you need a CLI build before
that, use the distribution Studio downloaded:

```bash
export JAVA_HOME=$HOME/.jdks/jbr-21.0.11
export ANDROID_HOME=$HOME/Android/Sdk
GRADLE=$(find ~/.gradle/wrapper/dists/gradle-8.7-bin -name gradle -type f -path '*/bin/*' | head -1)
"$GRADLE" testDebugUnitTest --console=plain
```

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

## Distribution

Releases are built by `.github/workflows/release.yml` and published as
APKs on GitHub. The app checks for and installs its own updates — see
Updates in [`architecture.md`](./architecture.md).
