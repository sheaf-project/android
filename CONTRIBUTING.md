# Contributing to Sheaf Android

Thanks for your interest in contributing. Sheaf handles sensitive identity data, so security and privacy aren't optional — please keep that in mind across changes.

## Development environment

- **Android Studio Ladybug** (2024.2.1) or newer
- **JDK 17** (bundled with Android Studio)
- Android SDK platform **35**

The app points to a user-configured base URL (set in Settings → API Server). For development, run the Sheaf API locally (`uvicorn sheaf.main:app --reload`) and point the base URL at your machine (e.g. `http://10.0.2.2:8000` for an emulator).

Run `./gradlew lint` before submitting a PR.

## Tech stack

- **Language:** Kotlin, Jetpack Compose (Material 3)
- **DI:** Hilt (KSP)
- **Networking:** Retrofit 2 + OkHttp 3, Moshi (KSP codegen)
- **Navigation:** Jetpack Navigation Compose
- **Storage:** DataStore Preferences (via `PreferencesRepository`)
- **Images:** Coil
- **Async:** Kotlin Coroutines + StateFlow
- **Wear OS:** Wear Compose Material, Wear Tiles, Wearable Data Layer

## Conventions

- **ViewModels use a single `UiState` data class** with `isLoading`, `error: String?`, and operation-specific flags (`isSaving`, `isDeleting`, etc.). State is a `MutableStateFlow` updated via `.update { it.copy(...) }`.
- **`runCatching` + `.onSuccess`/`.onFailure`** is the standard pattern for all API calls. Never throw from a ViewModel.
- **HTTP errors:** catch `HttpException` and check `.code()` to produce user-friendly messages. Don't surface raw status codes.
- **Navigation signals:** boolean flags on state (`saved`, `deleted`) observed with `LaunchedEffect` to trigger navigation after an operation completes.
- **Form state** lives in a separate `MutableStateFlow` from UI state, updated via `_form.update { copy(...) }`.
- **IDs are UUIDs** (strings in Kotlin, matching the API).
- **Moshi codegen:** request/response models need `@JsonClass(generateAdapter = true)`. Snake_case JSON fields use `@Json(name = "...")`.
- **Base URL is dynamic** — users configure it in settings. Never hardcode URLs. Retrofit is initialised with `http://localhost/` and `BaseUrlInterceptor` rewrites it.
- **American spelling** throughout — "color" not "colour", matching the iOS codebase.
- **Material Design 3** — use theme tokens (`MaterialTheme.colorScheme.*`, `MaterialTheme.shapes.*`), never hardcode colors or corner radii.

## Testing

Unit tests live in `sheaf/app/src/test/` and cover ViewModel logic.

**Stack:** JUnit 4, MockK, kotlinx-coroutines-test, Turbine (StateFlow assertions).

**Pattern:**
- `MainDispatcherRule` replaces `Dispatchers.Main` with `UnconfinedTestDispatcher` so coroutines run synchronously.
- `SheafApiService` and `PreferencesRepository` are mocked with MockK.
- `FrontNotificationHelper` is always `mockk(relaxed = true)` (Android dependency, side-effect only).
- ViewModels that call `load()` in `init` must have API stubs configured **before** constructing the ViewModel.
- Use `coEvery`/`coVerify` for suspend functions; `every`/`verify` for regular functions and Flow properties.

## Auth flow

1. User logs in → API returns access + refresh tokens.
2. `AuthViewModel` checks `totpEnabled` on the returned user.
3. If TOTP is required, tokens are held in `pendingAccessToken/RefreshToken` until the code is verified, then persisted.
4. `TokenAuthenticator` handles 401s automatically: refreshes and retries transparently.
5. Logout clears both tokens from DataStore.

## Wear OS credential sync

Credentials are pushed phone → watch via the Wearable Data Layer:

- Phone: `PhoneDataLayerService` listens for `/sheaf/credentials/request` messages, responds by writing a `DataItem` at `/sheaf/credentials` with `base_url`, `access_token`, `refresh_token`.
- Watch: `WearDataLayerService` watches for that `DataItem` and saves credentials via `WearAuthManager`.
- On first launch, `MainActivity` on the watch sends a request message to the phone if not yet authenticated.

## License

AGPL-3.0-or-later.
