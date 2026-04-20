# Sheaf Android

Native Android client for [Sheaf](https://github.com/your-org/sheaf) — open-source plural system tracking.

Built with **Kotlin + Jetpack Compose**, targeting Android 8.0+ (API 26).

---

## Features

| Tab | What it does |
|---|---|
| **Home** | Shows who is currently fronting, with elapsed time. Tap **Switch Front** to pick new fronters via a bottom sheet. |
| **Members** | Full list with avatar, display name, and pronouns. Tap any member to view/edit. Create members with name, pronouns, description, colour, birthday, and privacy level. |
| **Groups** | Create and manage groups with colour coding. Assign members to groups from within the group detail screen. |
| **History** | Infinitely-scrolling front history with stacked avatars, duration, and timestamps. |
| **Settings** | Edit your system name, change the API server URL at any time, export all data as JSON, and sign out. |

---

## Getting started

### Prerequisites

- **Android Studio Ladybug** (2024.2.1) or newer
- **JDK 17** (bundled with Android Studio)
- Android SDK platform **35**

### 1. Clone / open

```bash
git clone https://github.com/sheaf-project/android
```

Then in Android Studio: **File → Open** and select the `sheaf-android/` folder. Let Gradle sync.

> If you downloaded the zip from Claude, unzip it first — the root folder to open is `sheaf/`.

### 2. Download the Gradle wrapper JAR

The `gradle-wrapper.jar` is not included in source control. Run once:

```bash
# macOS / Linux
./gradlew --version

# Windows
gradlew.bat --version
```

Android Studio will also prompt you to download it automatically on first sync.

### 3. Build & run

Select a device or emulator in Android Studio and press **Run** (⇧F10), or:

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Architecture

```
app/
└── src/main/java/app/sheaf/
    ├── data/
    │   ├── api/          # Retrofit service, Auth + BaseUrl interceptors
    │   ├── model/        # Moshi-annotated request/response DTOs
    │   └── repository/   # PreferencesRepository (DataStore)
    ├── di/               # Hilt NetworkModule
    └── ui/
        ├── auth/         # LoginScreen + AuthViewModel
        ├── home/         # HomeScreen + HomeViewModel
        ├── members/      # MembersScreen, MemberDetailScreen, ViewModels
        ├── groups/       # GroupsScreen, GroupDetailScreen, ViewModels
        ├── history/      # HistoryScreen + HistoryViewModel
        ├── settings/     # SettingsScreen + SettingsViewModel
        ├── components/   # Shared composables (Avatar, EmptyState, etc.)
        ├── theme/        # Material3 colour scheme + typography
        └── SheafApp.kt   # Root NavHost + bottom navigation
```

### Key libraries

| Library | Purpose |
|---|---|
| Jetpack Compose BOM 2024.11 | UI toolkit |
| Navigation Compose | Single-activity navigation |
| Hilt 2.52 | Dependency injection |
| Retrofit 2.11 + Moshi | API client + JSON |
| OkHttp logging interceptor | Debug network logs |
| DataStore Preferences | Token + URL persistence |
| Coil 2.7 | Avatar image loading |

### Dynamic base URL

The `BaseUrlInterceptor` reads the saved server URL from DataStore at request time, replacing the placeholder `http://localhost/` set in `NetworkModule`. Changing the URL in Settings takes effect on the **next API call** — no restart needed.

### Authentication

Tokens are stored in DataStore (encrypted at rest on Android 6+). The `AuthInterceptor` attaches `Authorization: Bearer <token>` to every request. On logout, tokens are cleared and the nav stack resets to the login screen.

---

## Self-hosting

On the login screen, tap **Change** next to the server chip (or start fresh) to enter your self-hosted Sheaf API URL, e.g. `https://sheaf.yourdomain.com`. The app works identically against any compliant Sheaf API.

---

## Contributing

PRs welcome. Run `./gradlew lint` before submitting.

---

## Licence

AGPL-3.0
