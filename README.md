# Sheaf Android

[![CI](https://img.shields.io/github/actions/workflow/status/sheaf-project/android/dev-release.yml?branch=master&style=plastic&logo=github&label=CI)](https://github.com/sheaf-project/android/actions)
[![Latest dev build](https://img.shields.io/github/v/release/sheaf-project/android?include_prereleases&style=plastic&label=dev%20build)](https://github.com/sheaf-project/android/releases/tag/dev)
[![License: AGPL-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue?style=plastic)](LICENSE)

![transrights](https://pride-badges.pony.workers.dev/static/v1?label=trans%20rights&stripeWidth=6&stripeColors=5BCEFA,F5A9B8,FFFFFF,F5A9B8,5BCEFA)
![enbyware](https://pride-badges.pony.workers.dev/static/v1?label=enbyware&labelColor=%23555&stripeWidth=8&stripeColors=FCF434%2CFFFFFF%2C9C59D1%2C2C2C2C)
![pluralmade](https://pride-badges.pony.workers.dev/static/v1?label=plural+made&labelColor=%23555&stripeWidth=8&stripeColors=2e0525%2C553578%2C7675c3%2C89c7b0%2Cf4ecbd)

Native Android client for [Sheaf](https://github.com/sheaf-project/sheaf), the open-source plural system tracker. Built with Kotlin and Jetpack Compose; targets Android 8.0+ (API 26) and includes a Wear OS companion.

> **Status:** in active development. Use the dev build to try it; report issues on the [main repo's issue tracker](https://github.com/sheaf-project/sheaf/issues).

## Features

- **Members, groups, tags, custom fields** with the same privacy model as the backend.
- **Front tracking** with switch entry, cofronters, custom fronts, and an infinite-scroll history view.
- **Member bio history** with per-revision diff view.
- **Journals** (read/write).
- **System Safety** settings, including the grace-period and auth-tier controls.
- **Wear OS** companion: glanceable fronting tile, quick switch, member browse, credential sync from the phone.
- **Home-screen widget** (Glance) showing current fronters.
- **Self-hosting first.** Point the app at any Sheaf API base URL via Settings; the URL is dynamic and takes effect on the next request without a restart.
- **2FA / TOTP** supported on login.

## Install

Latest dev build: <https://github.com/sheaf-project/android/releases/tag/dev>.

The release contains:

- `app-release.apk` (phone)
- `wear-release.apk` (watch, sideload via ADB)
- `*.sig` and `*.pem` files: cosign keyless OIDC signatures, see [Verifying](#verifying-a-build).

Stable releases (`v*` tags) and store-distributed builds (Play Store, F-Droid) are not yet available; see [`docs/PUBLISHING.md`](docs/PUBLISHING.md) for the readiness plan.

### Verifying a build

Every APK is signed twice: by the project's release keystore (Android's standard signing, enforced by the OS at install/update time), and by Sheaf's CI workflow via cosign keyless OIDC. The cosign signature ties the APK to the exact GitHub Actions run that produced it and is recorded in the public Sigstore transparency log.

Step-by-step verification: [`docs/VERIFYING.md`](docs/VERIFYING.md).

## Build from source

### Prerequisites

- Android Studio Ladybug (2024.2.1) or newer (or the standalone Android SDK + a JDK 17 install).
- JDK 17. Android Studio bundles one; on Linux, `openjdk17` from your distro works.
- Android SDK platform 35.

### Build

```sh
git clone https://github.com/sheaf-project/android
cd android/sheaf
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

For the wear module: `./gradlew :wear:assembleDebug` and install via `adb -s <watch-serial> install ...`.

The first build downloads the Gradle wrapper JAR and a couple of GB of Android SDK / dependencies, so be patient.

### Run tests

```sh
cd sheaf && ./gradlew :app:test
```

See [`docs/TESTING.md`](docs/TESTING.md) for what's covered today and the layers that are mapped out for later.

## Architecture

Single-activity, Compose-only UI with Hilt for DI, Retrofit + Moshi (KSP codegen) for the API client, DataStore for token / settings persistence, Room for local caching, and WorkManager for background sync. Wear OS uses the Wearable Data Layer for credential transfer from the phone.

For module layout, ViewModel conventions, and the auth/Wear flows, see [`CONTRIBUTING.md`](CONTRIBUTING.md). The inner `sheaf/README.md` has a more detailed package-level breakdown.

## Contributing

PRs welcome. Read [`CONTRIBUTING.md`](CONTRIBUTING.md) for conventions (state management, error handling, Wear OS sync, naming) before opening one. CI runs unit tests on every push; please add tests for new ViewModels and parsers.

Bug reports, feature requests, and broader product discussion happen on the [main Sheaf repo](https://github.com/sheaf-project/sheaf). Android-specific bugs go [here](https://github.com/sheaf-project/android/issues).

## License

[AGPL-3.0-or-later](LICENSE).
