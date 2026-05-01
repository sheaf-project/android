# Publishing readiness: Google Play and F-Droid

Where we stand on getting Sheaf Android into the two distribution channels users will look for it in, and what's blocking each one. This is a working document; update it as items get checked off.

## Status today

- **GitHub releases:** working. Every push to `master` produces a cosign-signed APK at the `dev` tag.
- **Google Play:** not started. Multiple gating items below.
- **F-Droid (main repo):** blocked by a proprietary dependency. See the F-Droid section.
- **F-Droid (IzzyOnDroid third-party repo):** plausible without code changes.

## Cross-cutting prerequisites (needed for either store)

These are things both stores expect, none of which exist yet.

- [ ] **Privacy policy.** Required by Play, expected by users on F-Droid. Needs to be hosted at a stable URL (likely on the main project's site). Should cover: what data the app collects locally, what it transmits to a Sheaf instance, what an instance operator can see, that the app does no telemetry of its own.
- [ ] **Tagged release workflow.** Current CI publishes only to a rolling `dev` tag. Stores expect semver-tagged releases (`v0.1.0`, etc.) with a corresponding versionCode bump. Add a `release-on-tag.yml` workflow alongside the existing `dev-release.yml`.
- [ ] **versionCode strategy.** `app/build.gradle.kts` has `versionCode = 1` hardcoded. Play rejects re-uploads at the same versionCode, so this needs to be derived per-release (typical pattern: use the GitHub Actions run number, or compute from the tag).
- [ ] **App Bundle (AAB) build.** Play requires AAB for new submissions; APK is fine for sideload and F-Droid. Add `:app:bundleRelease` to the tagged-release workflow. Wear OS still ships as APK.
- [ ] **Store listing assets.** Icon (already present), feature graphic 1024x500, phone screenshots (recommend 4–8), Wear screenshots, short description (80 chars), full description (4000 chars). Same assets reused for both stores plus IzzyOnDroid where applicable.
- [ ] **Reproducible builds.** Not strictly required by either store but a F-Droid badge and a meaningful improvement to the verifiability story. Currently blocked by typical AGP non-determinism (timestamps, ZIP entry order, baseline profiles). Tracked in `docs/VERIFYING.md`.

## Google Play

### Required steps

- [ ] **Google Play Developer account.** One-time $25 registration fee, plus identity verification (org or individual). Decide which Lupine entity owns the listing.
- [ ] **Play App Signing.** When you upload an AAB, Google asks to manage the release signing key on your behalf; you keep an "upload key" locally. Tradeoff: simpler key management vs. Google having technical ability to sign APKs you didn't build. Cosign signatures pinned in `docs/VERIFYING.md` are independent of this and remain a useful counterweight.
- [ ] **Content rating.** IARC questionnaire in Play Console; for Sheaf this is straightforward (no violence, no in-app purchases, etc.) but must be completed.
- [ ] **Data Safety form.** Declares every category of data collected, retained, shared, and the purpose. Touchy for Sheaf because the app handles GDPR Article 9 special-category data, but the *app itself* doesn't collect it; the user's chosen Sheaf instance does. Wording matters; coordinate with the main repo's privacy posture.
- [ ] **App access information.** Play reviewers need a working test account on a publicly reachable Sheaf instance to actually exercise the app. Either the hosted tier (once live) or a dedicated review instance.
- [ ] **Target API level.** Play requires `targetSdk` to be within ~1 year of the latest stable Android. Currently `targetSdk = 35` (Android 15), so we're current.
- [ ] **Pre-launch report compliance.** Play runs the AAB on a test farm and flags crashes, accessibility issues, etc. Expect to need to fix at least a couple of edge cases before the first production release.

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
