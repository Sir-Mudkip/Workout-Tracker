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
