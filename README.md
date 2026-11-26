# Kids Launcher

A kid-safe home screen for Android tablets (Android 9 or newer).

- **Big icons** for only the apps a parent has chosen
- **Parent PIN**: hold the lock button (top-right) for 3 seconds, then enter the PIN
- **Custom wallpaper** from any picture on the tablet
- **Volume limit**: media volume can't go above the level you set, even inside games and videos
- **Locked in**: the child can't leave the app (full lock needs one setup step, see below)
- Icon size slider, 5-wrong-tries PIN lockout, parent screen closes itself after 3 minutes idle

## 1. Build and install

1. Install [Android Studio](https://developer.android.com/studio).
2. **File → Open** and choose this `KidsLauncher` folder. Let Gradle sync finish (first time takes a few minutes).
3. On the tablet: **Settings → About tablet → tap "Build number" 7 times** to turn on Developer options, then
   **Settings → Developer options → USB debugging: ON**.
4. Plug the tablet in by USB, accept the "Allow USB debugging?" prompt, then press the green ▶ **Run** button in Android Studio.
5. The app opens and asks a grown-up to set a PIN.
6. Press the tablet's Home button and choose **Kids Launcher → Always**.

At this point you have **basic mode**: the kid home screen works, Back does nothing, and the Home button always returns to it.
A determined child could still use the recent-apps button or pull down the notification bar.

### Or: let GitHub build the APK

`.github/workflows/build-apk.yml` builds an installable APK on GitHub every time you change `main`
(or press **Actions → Build APK → Run workflow**). Download it from the run's page under **Artifacts**.
Push a tag such as `v1.0` and it is also attached to a **Release**.

Without your own signing key, each build is signed with a throwaway key, so a new APK won't install over
the old one (uninstall first). To fix that, create a key once and add four repository secrets:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Keep the key file and passwords safe:
if you lose them you can't update the installed app.

## 2. Full lock (recommended)

This makes the app the tablet's *Device Owner*, which lets it block recent apps, the notification bar
and everything else, so the child can only use the apps you picked.

**Requirement:** the tablet must have **no accounts** on it (no Google account, Samsung account, etc.).
The easiest way is a fresh tablet or a factory reset: during setup, skip signing in to Google, then install the app as above.
(You can still use apps that don't need the Play Store, or install apps first and remove the account afterwards via **Settings → Accounts**.)

With the tablet plugged in, run:

```powershell
adb shell dpm set-device-owner com.kidslauncher.app/.KidsAdminReceiver
```

You should see `Success: Device owner set to package ...`. Open the app again: the full lock is now on.

`adb` is installed with Android Studio (`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`).

## 3. Using it

| To do this | Do this |
|---|---|
| Open parent settings | Hold the 🔒 button for 3 seconds, enter PIN |
| Choose which apps show | Parent settings → **Choose apps** |
| Change wallpaper | Parent settings → **Choose picture** |
| Limit volume | Parent settings → **Limit the maximum volume**, then drag the slider |
| Make icons bigger/smaller | Parent settings → **Icon size** |
| Use the tablet normally | Parent settings → **Turn kid mode OFF**. A bar appears with buttons for Android settings and switching the home app. Tap **Turn kid mode ON** to lock again |
| Uninstall | Parent settings → **Remove full lock**, then uninstall as normal |

## Notes and limits

- The volume limit applies to media (games, videos, music). Ringtone/alarm volumes aren't limited.
- When a new app is installed, it stays hidden until you tick it in **Choose apps**.
- If an app you allow can open other apps (for example a browser opening YouTube), those other apps are blocked in full-lock mode unless you allow them too.
- Forgotten PIN: in basic mode, uninstall/reinstall the app or clear its data from Android settings (you'll need to reach them via recent apps or safe mode).
  In full-lock mode, run `adb shell dpm remove-active-admin com.kidslauncher.app/.KidsAdminReceiver` (this usually works for copies installed with Android Studio's Run button, because those are marked test-only) or factory-reset the tablet. **Write the PIN down somewhere safe.**

## Code map

| File | What it does |
|---|---|
| `MainActivity.kt` | Kid home screen: wallpaper, big icon grid, hold-to-open parent button, starts/stops the lock |
| `PinActivity.kt` | Set / check the parent PIN (stored as a salted PBKDF2 hash) |
| `SettingsActivity.kt` | Parent settings screen |
| `AppPickerActivity.kt` | Tick-list of installed apps |
| `VolumeGuardService.kt` | Background service that pulls the volume back down to the limit |
| `Kiosk.kt` | Device Owner / lock-task rules |
| `WallpaperStore.kt` | Saves the chosen picture, shrunk and rotated correctly |
| `Prefs.kt` | All saved settings |
