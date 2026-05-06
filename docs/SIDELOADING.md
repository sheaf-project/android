# Installing the Sheaf Android app

Three install paths depending on where you got the build from and which device you're targeting. Most users want the first one and can stop reading there.

## Phone install

### Via Google Play (recommended)

If you signed up at app.sheaf.sh or got linked here from your instance operator: install **Sheaf** from the Play Store. The Play listing covers phone *and* watch; if your phone is paired to a Wear OS watch, the watch app installs automatically once Play sees the matching listing for that watch's account.

### Via GitHub Releases (the `.open` flavour)

If you'd rather not go through Play (FOSS preference, sideload-only device, etc.), grab `sheaf-X.Y.Z-open.apk` from the [latest release](https://github.com/sheaf-project/android/releases). This is the **`.open`** flavour:

- Different `applicationId` (`systems.lupine.sheaf.open`) so it coexists with the Play version on the same device.
- Signed by the project's CI keystore plus a Sigstore cosign signature (see [VERIFYING.md](VERIFYING.md)).
- Same source code as the Play AAB; only the package name and signing key differ.

```sh
adb install sheaf-X.Y.Z-open.apk
```

After install, configure the server URL via Settings > Server (or on first launch). HTTPS is added automatically if you type a bare domain.

### Via IzzyOnDroid

[IzzyOnDroid](https://apt.izzysoft.de/fdroid/) tracks the `.open` GitHub releases. Add the IzzyOnDroid repo to your F-Droid client, search for Sheaf, install. (Wear OS isn't covered by IzzyOnDroid; that's still a sideload.)

## Watch install

Pixel Watch, Galaxy Watch (Wear OS 4+), or any Wear OS 3+ device.

### Via Google Play (recommended)

Once the phone has the Play version of Sheaf installed and the watch is paired to the same Google account, the wear app shows up in the watch's Play Store under "Apps on your phone" and installs automatically. No manual step on the watch.

### Via ADB sideload (the `.open` flavour, dev only)

For pre-release builds or the `.open` track, you can sideload the wear APK directly to the watch over ADB. The Pixel Watch supports ADB over Wi-Fi:

1. **On the watch**: Settings > System > About > Versions, tap **Build number** seven times to enable Developer options. Back out to System > Developer options, enable **ADB debugging** and **Wireless debugging**.
2. **On a computer with `adb`**:
   ```sh
   # First time: pair. The watch shows a 6-digit pairing code.
   adb pair <watch-ip>:<pairing-port>
   # Then connect on the persistent debugging port.
   adb connect <watch-ip>:<debugging-port>
   adb devices                       # confirm the watch is listed
   adb -s <watch-id> install sheaf-wear-X.Y.Z-open.apk
   ```

The watch shows the new app under the launcher app list.

### Don't `adb install` the wrong APK on the wrong device

Phone and wear APKs **share an `applicationId`** within a flavour (Google's recommended Wear OS 3+ packaging). That means:

- Installing the *wear* APK on a *phone* will replace the phone APK or fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Even if the install "succeeds", the resulting app won't run because the wear APK declares `<uses-feature android:name="android.hardware.type.watch">` and uses Wear-only Compose / Tiles APIs.
- Installing the *phone* APK on a *watch* will appear to install but the launcher won't show it (same feature gate, opposite direction).

When you have both a phone and a watch connected to ADB, always pass `-s <device-id>` so you're targeting the right one:

```sh
adb devices
# List of devices attached
# 1A111JEHN02123  device      <- phone
# adb-XXXXXXXX-YYYYYY._adb-tls-connect._tcp  device  <- pixel watch over wifi

adb -s 1A111JEHN02123 install sheaf-X.Y.Z-open.apk           # phone
adb -s adb-XXXXXXXX-... install sheaf-wear-X.Y.Z-open.apk    # watch
```

## Mixing flavours on one watch

If you've sideloaded the `.open` watch APK and later switch to a Play-installed (prod) phone app, you'll have:

- Phone with `systems.lupine.sheaf` (prod, from Play)
- Watch with `systems.lupine.sheaf.open` (dev, sideloaded)

The two **won't pair via the data layer** — Wear OS keys off matching `applicationId`. Either install the prod wear APK to the watch (Play will do this for you when it sees the match) and uninstall the `.open` one, or stay on the `.open` flavour on both ends. You can have both flavours installed on each device side-by-side — they're different packages.

## Verifying what you installed

Both the phone and watch APKs published by GitHub Releases are cosign-signed via Sigstore keyless OIDC. To check before installing, see [VERIFYING.md](VERIFYING.md).
