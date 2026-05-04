# Publishing readiness: Google Play and F-Droid

Where we stand on getting Sheaf Android into the two distribution channels users will look for it in, and what's blocking each one. This is a working document; update it as items get checked off.

## Status today

- **GitHub releases:**
  - Rolling `dev` tag: every push to `master` produces a cosign-signed `.open` APK pair (phone + watch).
  - Tagged `vX.Y.Z` releases: produce signed `.open` APKs publicly *and* unsigned prod APK + AAB privately as a workflow artefact for the offline signing ceremony.
- **Google Play:** in progress. Developer account approved; tagged-release pipeline ready; first `v0.1.0` upload pending.
- **F-Droid (main repo):** blocked by a proprietary dependency. See the F-Droid section.
- **F-Droid (IzzyOnDroid third-party repo):** plausible without code changes.

## Build flavours and version scheme

Two `applicationId`s are produced from the same source tree:

| Flavour | `applicationId` | Distribution | Signing |
|---|---|---|---|
| `.open` (`-PopenBuild=true`) | `systems.lupine.sheaf.open` | GitHub releases, IzzyOnDroid | CI keystore (rotatable, kept in CI secrets) |
| prod (default) | `systems.lupine.sheaf` | Google Play | YubiKey-resident production key, signed offline |

The two install side-by-side on a device — different package names = different APK identities. The CI keystore never signs the `systems.lupine.sheaf` namespace, and the production key never touches CI.

`versionCode` is derived per-build:

- **Tagged releases** (`v*.*.*`): `MAJOR*10000 + MINOR*100 + PATCH`. v0.1.0 → 100, v1.0.0 → 10000. Caps at v99.99.99 → 999999. Bump the scheme before that bites.
- **Dev (`master` head)**: `1000000 + git rev-list --count HEAD`. Always strictly greater than any tagged release, so `.open` users can keep updating from dev without ever being blocked by a tagged release downgrade.

`versionName` mirrors the tag for tagged releases (`0.1.0`); dev uses `0.0.0-dev.<short-sha>`.

## What the tagged-release workflow produces

`tagged-release.yml` triggers on `v*.*.*` tag pushes and emits two sets of artefacts:

1. **Public GitHub release** (the tag): `.open` APKs (phone + watch), each cosign-signed (`*.sig` + `*.pem`). This is what IzzyOnDroid pulls and what direct-download users install.
2. **Private workflow artefact** (`prod-unsigned-vX.Y.Z`, 90-day retention): unsigned prod APK + AAB (phone + watch), each cosign-signed. Only repo collaborators with `actions:read` can fetch it via `gh run download`. Operational details for fetching, verifying, signing with the YubiKey, and uploading to Play live in the private release-ops repo.

The cosign signature over the *unsigned* prod bytes is the chain-of-custody from "CI built this commit" to "this is the exact byte sequence the offline ceremony then signed".

## Cross-cutting prerequisites (still open)

- [ ] **Privacy policy URL plumbing.** Policy text exists; in-app pointer + Play Console URL still needed.
- [ ] **Store listing assets.** Icon (already present), feature graphic 1024x500, phone screenshots (recommend 4–8), Wear screenshots, short description (80 chars), full description (4000 chars). Same assets reused for both stores plus IzzyOnDroid where applicable.
- [ ] **Reproducible builds.** Not strictly required by either store but a F-Droid badge and a meaningful improvement to the verifiability story. Currently blocked by typical AGP non-determinism (timestamps, ZIP entry order, baseline profiles). Tracked in `docs/VERIFYING.md`.

## Google Play

### Required steps

- [x] **Google Play Developer account.** Approved (LLC tier, skips the 20-day closed-testing gate that applies to personal accounts).
- [x] **Play App Signing decision.** Opted out — production signing key lives on YubiKeys held by the maintainers, AABs are signed offline (jarsigner + PKCS#11). The cosign chain attests to the exact unsigned bytes that get YubiKey-signed.
- [x] **Content rating.** IARC questionnaire complete.
- [x] **Data Safety form.** Submitted; mirrors the Android privacy policy (no app-side telemetry, instance-side data is the operator's responsibility).
- [x] **App access information.** Reviewer test account provided.
- [x] **Target API level.** `targetSdk = 35` (Android 15) — current.
- [ ] **Pre-launch report compliance.** First upload will surface this. Expect to need to fix at least a couple of edge cases.

### Tracks and rollout

Use the Play tracks ladder:

1. **Internal testing** (up to 100 testers, no review needed for first push) — wire up to the tagged-release workflow.
2. **Closed testing** (named groups) — useful for the broader plural community testers.
3. **Open testing** (any user can opt in) — public beta channel.
4. **Production** — staged rollout (start at 5–10%) so issues surface before they're at scale.

### Initial review

First-time submissions go through a more thorough review, often 3–7 days. Subsequent updates usually clear in 24–48 hours unless the listing changes.

## F-Droid (main repo)

### Hard blocker

The app and Wear module both depend on `com.google.android.gms:play-services-wearable` (the Wearable Data Layer SDK), which is **proprietary**. F-Droid's main repo policy excludes apps with non-libre runtime dependencies.

Three plausible paths:

1. **Product flavors.** Define `play` and `fdroid` flavors in `app/build.gradle.kts`, with a stub `play-services-wearable` shim in the `fdroid` flavor. The `fdroid` flavor would lose phone↔watch credential sync; users on F-Droid would have to log in on the watch directly. Practical but adds maintenance overhead.
2. **Drop Wear OS support from the F-Droid build.** Same as flavor approach but more aggressive: ship only the phone APK on F-Droid. Cleanest if the watch IPC is hard to abstract.
3. **Don't target F-Droid main, target IzzyOnDroid instead** (next section). Lowest effort.

Recommendation: **start with IzzyOnDroid**, plan the flavor split as a follow-on if F-Droid main is genuinely needed by the user base.

### Other F-Droid requirements (not blockers)

- [x] **FLOSS license.** AGPL-3.0-or-later.
- [x] **Public source.** Repo is public.
- [x] **Domain-derived application ID.** `systems.lupine.sheaf` (lupine.systems).
- [x] **No proprietary tracking/ads SDKs.** None present.
- [ ] **`fdroiddata` recipe.** YAML metadata file at `metadata/systems.lupine.sheaf.yml` in the `fdroiddata` repo, describing build commands, versions, and the `Binaries:` field pointing at our GitHub release APKs (so F-Droid can extract the upstream signature for the `AllowedAPKSigningKeys` check).
- [ ] **Anti-features declared.** Likely `NonFreeDep` on the Play-flavor build if we go the flavor route. Possibly `NonFreeNet` depending on F-Droid's interpretation of self-hostable network apps; usually fine because the server *is* libre.
- [ ] **Funding metadata.** F-Droid surfaces donation links if `funding.json` or `.github/FUNDING.yml` is present, or a clearly-marked donation link in the README. None set up yet.
- [ ] **`AllowedAPKSigningKeys` configured.** Once we have a stable release keystore, extract the cert SHA-256 with `apksigner verify --print-certs` and add it to the fdroiddata recipe.

### Reproducible builds (nice-to-have for F-Droid main)

If we eventually go the F-Droid main route, RB earns a green badge and means F-Droid's build matches our published APK byte-for-byte, so users can install ours directly without re-trusting F-Droid's. Steps:

- [ ] Pin all build-tool versions (NDK if any, AGP, Kotlin, Gradle).
- [ ] Disable PNG crushing and resource shrinker if they introduce non-determinism.
- [ ] Strip embedded build paths and timestamps. R8 mappings should be deterministic given a fixed seed.
- [ ] Use `apksigner` with explicit signing args.
- [ ] Run `diffoscope` between two builds on different machines to find remaining drift.

## F-Droid (IzzyOnDroid)

[IzzyOnDroid](https://apt.izzysoft.de/fdroid/) is a popular third-party F-Droid repo with looser rules: it allows some non-FLOSS dependencies, doesn't require source-built APKs, and accepts the upstream-signed releases directly from GitHub.

### Steps

- [ ] Open a request at <https://gitlab.com/IzzyOnDroid/repo/-/issues> referencing our GitHub releases.
- [ ] Provide an icon, short and long descriptions, and the cosign verification commands from `docs/VERIFYING.md` (Izzy users may want to verify upstream-signed APKs).
- [ ] Maintain a stable release cadence; Izzy auto-pulls new tagged releases once configured.

This is the **lowest-friction path** to a curated F-Droid listing, and it preserves the Wear OS functionality. Recommended first move once a real `v0.1.0` tagged release exists.

## Suggested ordering

1. Privacy policy + tagged-release workflow + versionCode strategy + AAB build + store listing assets. (Cross-cutting; gates everything else.)
2. Cut a `v0.1.0` tag, sanity-check the signed AAB and APK outputs.
3. Submit to **IzzyOnDroid** (low friction, no code changes).
4. Apply for **Google Play Developer account**, complete listing, push to internal track for soak.
5. Decide whether **F-Droid main** is worth the flavor split. If yes, do the work and PR `fdroiddata`.
6. Pursue **reproducible builds** as a separate, lower-priority track.
