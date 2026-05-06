# Changelog

All notable changes to the Sheaf Android client are recorded here. Format loosely
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the project
uses semantic versioning (`MAJOR.MINOR.PATCH`).

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
