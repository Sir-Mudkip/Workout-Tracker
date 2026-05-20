# Installing Workout Tracker on your phone

Two paths. The first is the easiest for a one-shot install; the second is what you want if you'll keep iterating.

## Easiest: build APK, sideload via USB cable

1. In Android Studio: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
   When it finishes, a notification at the bottom-right says *"APK(s) generated successfully"* → click **locate**. That opens to:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```
2. Plug phone in via USB. Pull down the notification shade → tap the USB notification → switch to **"File transfer (MTP)"** mode (not "charging only").
3. On your computer, open Files → your phone shows up as a device → copy `app-debug.apk` to `Downloads/` on the phone.
4. On the phone, open the Files / My Files app → tap the APK in Downloads → it'll say *"For your security, your phone isn't allowed to install unknown apps from this source."* Tap **Settings** → enable for the file manager → back → **Install**.
5. Open the app from the launcher.

The debug APK is signed with Android's debug key, which is fine for personal use — no developer account, no Play Store, no fees.

## For repeated installs: adb over USB

If you'll be rebuilding regularly, sideloading every time is annoying. Set up `adb` so you can push directly to your phone from a terminal.

### One-time setup

Flatpak Android Studio's sandbox can't see USB cleanly, so run `adb` from a distrobox container instead:

```bash
# Create the container (skip if you already have an 'android' distrobox)
distrobox-create --name android --image fedora:40
distrobox-enter android

# Inside the container
sudo dnf install -y android-tools
```

On the phone:
1. Settings → About phone → tap **Build number** 7 times.
2. Back → Developer options → enable **USB debugging**.
3. Plug into USB → tap **Allow** on the "Allow USB debugging?" prompt that appears.

Verify it's wired up:
```bash
adb devices    # should list your phone
```

### Every time you want to deploy a new build

From inside the distrobox, in the project directory:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` reinstalls, **keeping your data** (programs, sessions, 1RMs). New versions update in-place.

You can also configure Android Studio itself to use this host `adb` instead of its bundled one (**Settings → Build, Execution, Deployment → Debugger → Android Debug Bridge → Use custom path**), but running `adb install` from the terminal after each `./gradlew assembleDebug` is usually simpler.

## A note on long-term use

The debug APK works fine forever for personal use, but two small caveats:

- It's marked debuggable, so it runs slightly slower than a release build.
- If you ever uninstall and try to reinstall later, Android may warn *"this app was signed with a debug certificate"* — just tap install anyway.

If those bug you later, generating a personal release keystore and switching the build to use it is about 10 minutes of work. For daily use as a personal app, the debug APK is genuinely fine.

---

# In-app updates via GitHub Releases

Once set up, you push a git tag and your phone offers the update from inside the app (Settings → App version → Check for updates). No more dragging APKs around.

## One-time setup

### 1. Generate a release keystore

Once. Keep it forever — if you lose it, you have to uninstall and reinstall to switch to a new key.

```bash
keytool -genkey -v \
  -keystore release.keystore \
  -alias workout-tracker \
  -keyalg RSA -keysize 2048 -validity 10000
```

Pick a store password and key password (can be the same). Answer the name/org prompts however you want — they don't matter for a personal app.

**Back this file up somewhere safe (password manager, encrypted USB, whatever).** Don't commit it to git.

### 2. Add four GitHub secrets

GitHub repo → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_B64` | output of `base64 -w0 release.keystore` |
| `RELEASE_KEYSTORE_PASSWORD` | the store password you set |
| `RELEASE_KEY_ALIAS` | `workout-tracker` |
| `RELEASE_KEY_PASSWORD` | the key password you set |

### 3. First install (one-time uninstall required)

Your phone currently has a debug-signed build. The first CI release will be signed with the new release key, and Android refuses cross-key updates. So:

1. Tag and push `v0.2.0` (see below) — wait for the GitHub Action to finish and attach the APK.
2. On your phone: **Settings → Apps → Workout Tracker → Uninstall**. *(Your data is in app storage; if you've configured anything important, JSON-export your programs first.)*
3. Download `workout-tracker-v0.2.0.apk` from the GitHub Release page and sideload it once.
4. From here on, in-app updates work.

## Cutting a release

```bash
# Bump the version
# edit app/build.gradle.kts: versionCode = 3, versionName = "0.3.0"
git commit -am "Release v0.3.0"
git tag v0.3.0
git push && git push --tags
```

The `Release APK` workflow fires on the tag push, builds the signed APK, and attaches it to a new GitHub Release.

On your phone: open the app → **Settings → App version → Check for updates → Download → Install**. The first time you try, Android will redirect you to grant "Install unknown apps" permission for Workout Tracker; do that once, then come back and tap Check again.

## Versioning rules

- `versionName` is what users see and what the in-app checker compares (`0.3.0` > `0.2.0`).
- `versionCode` is an integer that Android uses internally — bump it every release, or installs will fail.
- Tag must match `versionName` with a `v` prefix: tag `v0.3.0` for `versionName = "0.3.0"`.
