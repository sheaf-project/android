# Testing the Sheaf Android app

## What's wired up today

JVM unit tests under `sheaf/app/src/test/`. Run them with:

```sh
cd sheaf && ./gradlew :app:test
```

CI runs `./gradlew :app:test` on every push to `master` before assembling the release APK; failure blocks the build. Test reports are uploaded as the `unit-test-reports` artifact on each run for inspection.

Current coverage:

- **`util/ErrorMessagesTest`** — `Throwable.toUserMessage()` mapping for `HttpException` (incl. Cloudflare body sniffing) and the IO-exception ladder.
- **`data/model/SystemSafetyParsingTest`** — Moshi parsing of `SystemSafetyResponse` and `SystemSafetyUpdateResponse`. Acts as a regression net for the kind of bug fixed in `4ad74c5`: a missing `@JsonClass` annotation causes parsing to fall through to reflection, which in release builds gets stripped by R8 even though debug builds appear fine.

Wired-but-unused dependencies in `app/build.gradle.kts`: `kotlinx-coroutines-test`, `mockk`, `turbine`. Reach for these when adding ViewModel or repository tests.

## Future testing layers

These are mapped out but not implemented; pick them up as time and pain warrant.

### More unit tests (same `src/test/` dir)

- **`AltchaSolver`** — pure crypto/PoW logic, high regression value. Wrinkle: it calls `android.util.Base64`, which throws "not mocked" on JVM. Either stub via `mockkStatic(Base64::class)`, or swap to `java.util.Base64` (functionally identical for `NO_WRAP`).
- **ViewModels** — `MembersViewModel`, `AuthViewModel`, `SystemSafetyViewModel`, etc. With `mockk` for the repo dependency and `turbine` for `StateFlow`/`Flow` assertions, these are straightforward.
- **Repositories** — once they exist as a thin layer; use a fake `SheafApiService` and assert mapping/caching behavior.

### Compose UI tests via Robolectric

Renders Compose on the JVM (no emulator) using `androidx.compose.ui:ui-test-junit4` + `org.robolectric:robolectric` + `@RunWith(AndroidJUnit4::class)` + `createAndroidComposeRule`. Good for screen-level smoke tests and routing/state assertions. Slower than unit tests (~5–10s startup) but still runnable in CI without an emulator.

### Screenshot tests (Paparazzi or Roborazzi)

Render Compose to PNG on JVM, diff against checked-in goldens. Catches accidental visual regressions that other test layers miss (typography, color, layout). Tradeoff: golden images need updating whenever the design intentionally changes — small maintenance burden, occasionally noisy.

- **Paparazzi** (Square): standalone, no Robolectric, `@Test` + `paparazzi.snapshot { … }`.
- **Roborazzi** (Robolectric-based): if we already adopt Robolectric for Compose tests, this folds in cheaply.

### Instrumented (Espresso) tests

Real Android runtime, real emulator. The gold standard for end-to-end behavior, but in CI it requires `reactivecircus/android-emulator-runner`, which is slow (~5 min cold) and prone to flakes on free GitHub runners. **Defer until there's a specific concern only an emulator can validate** — IPC with Wear, real `WorkManager` execution, real notification posting, etc.

### Release-build smoke test

The bug fixed in `4ad74c5` only manifested in release (R8-obfuscated) builds — unit tests on the debug variant would not have caught it. A useful addition is a minimal instrumented test that exercises the JSON-deserialization paths against an `assembleRelease` APK. Open question: is the cost (emulator + signed release APK in CI) worth it vs. keeping a strict `proguard-rules.pro` review discipline. Revisit after we've shipped a few more releases.

## What we explicitly don't have

- No mutation testing.
- No coverage gates. (Add `kover` if/when coverage thresholds become useful — premature today with two test files.)
- No fuzzing of API model parsers.
