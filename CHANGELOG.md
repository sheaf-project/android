# Changelog

All notable changes to the Sheaf Android client are recorded here. Format loosely
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the project
uses semantic versioning (`MAJOR.MINOR.PATCH`).

## [0.1.5] - 2026-05-07

Watchface complications and a build-info surface in Settings.

### Added

- Wear OS complications (phase 1): six static slots for users to drop
  on their watchface. Open-app shortcut, quick switch, fronters
  (oldest-fronting first), fronters (newest-fronting first), current
  fronting duration, and last switch time. Each picks SHORT_TEXT or
  LONG_TEXT depending on the slot.
- Wear OS configurable per-member complication ("Is X fronting?"). On
  add, the watchface picker launches a member chooser; selection is
  stored per complication instance, so the same complication can fill
  multiple slots, each tracking a different member.
- About row at the bottom of phone Settings and wear Settings showing
  the app version, short git commit hash, and a "debug" suffix on
  debug builds. Useful for telling which build is actually installed
  on a device.

## [0.1.4] - 2026-05-07

Phone polish, wear OS substantive overhaul, and a couple of correctness
fixes that affect both clients.

### Added

- "Remember this device" checkbox on the TOTP login prompt: when ticked,
  successful TOTP earns a 30-day trusted-device cookie that lets future
  logins skip TOTP from the same install. Mirrors the iOS flow. Manage
  trusted devices server-side via the trusted-devices settings.
- Real Sheaf logo on the phone launcher icon, the wear launcher icon,
  and the login screen, replacing the placeholder vectors from initial
  bootstrap.
- Wear OS member rendering polish: circular member avatars in every
  chip slot, member emoji surfaced as a label prefix and as the
  fallback glyph in the avatar circle, markdown-stripping on member
  descriptions so `![alt](url)` and `**bold**` no longer leak as raw
  source.
- Wear OS Home and Members screens gained a manual Refresh chip near
  the top of each list.
- Wear OS Switch screen has a sticky bottom commit chip in a distinct
  mint-green tint, an "End existing fronts" ToggleChip at the top, and
  a persisted default for the toggle (Settings → End fronts on switch).
- Wear OS Currently Fronting tile actually renders now (an overlooked
  `onTileResourcesRequest` override was causing it to draw blank).

### Fixed

- "Fronting since" timer no longer resets on phone or wear when one
  fronter is added or removed. The remaining members keep their
  effective fronting-since (per the backend's `member_since` field
  introduced server-side, which walks each member's chain of
  contiguous fronts back to its earliest start).
- TOTP code field no longer auto-submits when the 6th digit is typed,
  so the new "Remember this device" checkbox is actually tickable.
- Trusted-device cookie persists across logout instead of being wiped,
  matching browser and iOS behaviour. The cookie belongs to the
  device, not the session.
- Wear Members screen no longer crashes parsing API responses on the
  release build. Wear is now on Moshi KSP codegen so R8 minification
  is back on.

### Build

- Phone and wear `versionCode` are now derived from the tag as
  `(M*10000 + m*100 + p) * 10 + form_factor_index`, where phone=0 and
  wear=1. The previous scheme collided across releases (phone v0.1.N+1
  shared a code with wear v0.1.N) and was the reason v0.1.2 phone
  couldn't be uploaded to Play.

## [0.1.0] - 2026-05-06

First public release. Phone + Wear OS, available via Google Play, GitHub
Releases (`.open` flavour), and IzzyOnDroid.

### Added

#### Core data model

- Members, groups, tags, custom fields, with the same privacy controls as the
  backend (visibility scopes, per-field visibility).
- Journals: read and write, including unified per-system, per-group, and
  per-member views.
- Member bio revision history with per-revision diff view, plus pin/unpin of
  significant revisions.
- Cofronter support (multi-member front).
- Simply Plural import: pull members, groups, and custom fronts from an SP
  data dump, with selectable categories and a pre-import summary.

#### Front tracking

- Front switch entry with prefill of currently-fronting members and a group
  filter.
- Infinite-scroll fronting history with edit support.
- Home-screen Glance widget surfacing current fronters.
- Background front sync with WorkManager.

#### Authentication and security

- Login with TOTP / 2FA support and Altcha captcha.
- App lock with biometrics or device passcode.
- Per-resource API key creation with scope matrix.
- Sessions screen with current-session highlighting via JWT `sid` claim.
- Admin step-up auth: only prompts for the credential the server actually
  requires.
- Admin account recovery tools and account-deletion flow with cancel window.

#### System Safety

- Settings screen mirroring the backend's grace-period and auth-tier controls.
- Revision retention configuration with pending-trim notice banner on home.
- Tags manager.
- Orphaned-file cleanup gated behind System Safety + step-up auth.
- Storage usage and account info surfaced on Settings detail screens.

#### Self-hosting

- Dynamic API base URL via Settings, no restart needed; defaults to `https://`
  when a bare host is entered.
- Support for self-hosted instances behind Cloudflare Access.
- Image requests routed through the instance's `file_cdn_base` when configured.
- Custom User-Agent header on avatar requests.

#### Wear OS companion

- Glanceable "Currently Fronting" tile.
- Quick switch from the watch (selectable member list, primary chip commits).
- Member browse, member profile, group browse, group detail.
- Add member from the watch.
- Credential pairing from the phone via the Wearable Data Layer (no QR / typing
  on the watch).
- Refresh-token rotation propagated from phone to watch.

#### Onboarding and content

- Post-signup onboarding screen with debug menu.
- Rich Markdown editor across journals, bios, system, and group descriptions.
- Announcements view.
- Invite codes and email verification flows.

#### Offline and resilience

- Offline caching for members, groups, history, and profile data.
- Error state suppression on home when offline.
- Improved error messages across the app.

#### Distribution and verification

- Two release flavours: `systems.lupine.sheaf` (Play, prod keystore) and
  `systems.lupine.sheaf.open` (GitHub Releases / IzzyOnDroid, CI keystore).
- Phone and Wear OS APKs share `applicationId` within a flavour, per Wear OS 3+
  multi-APK packaging guidance.
- Cosign keyless OIDC signatures (Sigstore + Rekor transparency log) on every
  released artefact, in addition to standard Android signing.
- `scripts/verify-release.sh` for end-user verification of the release-key
  fingerprint and cosign chain.

### Notes

- This is a 0.x release. The on-device schema and API client are still subject
  to breaking changes; back up before upgrading across minor versions.
- Wear OS app requires Wear OS 3+ and a paired Pixel Watch / Galaxy Watch /
  similar.
