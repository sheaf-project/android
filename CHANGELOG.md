# Changelog

All notable changes to the Sheaf Android client are recorded here. Format loosely
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the project
uses semantic versioning (`MAJOR.MINOR.PATCH`).

## [0.1.9] - 2026-05-11

Catches up the phone client to the backend features that landed between
0.1.7 and now: scratchpad notes, polls, board messages.

### Added

- **Scratchpad notes on members and on the system.** Free-form text
  (max 5000 chars) for the running-thought stuff that doesn't want
  the bio's revision-history machinery: trigger lists, current meds,
  favourites, system-level reference info. Edit field on the member
  edit screen and on the system edit screen, Markdown-rendered card
  on the member profile.
- **Polls** under the Home top app bar HowToVote icon. Lists open
  and closed polls; create with single-choice or multi-choice, live
  or hidden-until-close results, configurable close date (default
  one week out, quick chips for 1 day / 1 week / 1 month). Vote
  detail screen lets the user pick which member of their system is
  casting the vote (pluralism affordance), pre-fills the option set
  from any existing vote so amending feels like editing, and offers
  withdraw. Closed and live-tally polls show a per-option progress
  bar tally; end-only polls show a "hidden until close" notice
  while open.
- **Board messages** under the Home top app bar Forum icon. Lists
  the system board plus every member's wall in one place, ordered
  by most-recent message, with unread badges from the currently-
  fronting member's perspective. Per-board view shows the message
  history bottom-up with a sticky composer; "Posting as" dropdown
  attributes the post to a specific member of the system. Auto-marks
  the board seen on load for the caller member.

### Notes

- Owner-side message edit/delete and the front-start-prompt
  auto-prompt are deliberately deferred to a future slice. Replies
  render the parent-message backlink but don't yet have a "reply"
  CTA on each row.
- Polls audit log, retention-days customisation, and the
  include_custom_fronts toggle are also follow-up polish — the
  basic create/vote/results loop is in.

## [0.1.8] - 2026-05-10

Push notifications land. Subscribe to other systems' front changes,
get pinged on your phone. Set up reminders that fire on a schedule or
in response to fronting events. Create channels from the phone to
share with people you want to keep in the loop.

### Added

- **Push notifications (.play flavour only).** Front-change events
  from systems you're watching land as native Android notifications.
  Built on the backend's mobile-push pipeline (FCM) with the
  per-account device-token model so token rotations stay invisible to
  the user.
- **Settings → Notifications hub.** Five entries:
  - **Receiving**: subscriptions delivering to this account, with
    per-row unsubscribe.
  - **Channels you own**: notification channels for sharing your
    system's front updates. Create a channel, copy/share the magic
    link, recipient redeems on their device. Channel detail screen
    lets you re-issue the activation link if you lose it, enable
    or disable a channel, and delete.
  - **Reminders**: full CRUD for scheduled or front-event-triggered
    reminders. Automated: pick a member + event + delay. Scheduled:
    daily / weekly / monthly with time and day pickers.
  - **Your devices**: registered push devices for this account, with
    current-device marker.
  - The existing Fronting Notification and App Lock toggles stay
    where they were.
- **Magic-link redemption deep link**: `sheaf://notifications/redeem`
  opens the app, redeems the activation code against the server, and
  prompts for POST_NOTIFICATIONS permission at the moment the user is
  opting in (Android 13+).
- Three Android notification channels for incoming push: Front change,
  Reminders, System. Default importance HIGH for the first two so
  they heads-up by default.

### Changed

- **Build system**: migrated from a `-PopenBuild=true` gradle property
  to product flavours `play` and `open`. New build commands are
  `./gradlew :app:assemblePlayRelease` and `:app:assembleOpenRelease`
  (or the wear equivalents). Both flavours can coexist on the same
  device side-by-side now.
- AuthTierSelector in System Safety split into per-row composables to
  side-step a Compose-compiler pathological case that GC-thrashed on
  CI even at 4 GB Kotlin daemon heap.
- Gradle daemon bumped to 4 GB, Kotlin daemon to 4 GB explicit.

### Notes

- The .open flavour ships without Firebase / FCM. UnifiedPush
  integration is tracked as future work so the .open distribution can
  still receive pushes via a user-installed distributor like ntfy.
- iOS push (APNS) is wired on the backend with `apns_dev` / `apns_prod`
  variants so iOS clients can land the same channel infrastructure
  when their messaging service catches up. The Android client surfaces
  iOS-targeted channels in the Receiving list as "iOS push" for
  cross-platform awareness.

## [0.1.7] - 2026-05-09

Major wear OS expansion (six fronting tiles, history viewer screen) and
a long-standing phone auth bug fixed.

### Added

- Wear OS tiles, big haul:
  - **Currently Fronting (with avatars)**: row of fronter avatars over
    a comma-joined name list, "+N" overflow when more than four are
    fronting.
  - **Currently Fronting (avatars only)**: glanceable face arrangement
    with no text — solo (1), row (2-3), or 2x2 grid (4) with overflow
    badge.
  - **Member tracker**: pick a roster of members at tile-add time;
    each shows a fronting (✓) / not (✗) indicator. Layout adapts to
    count: solo big-avatar through 4x2 compact grid for 7+.
  - **Quick switch**: pre-pick a roster, tap avatars to toggle their
    selection in-place (✓ marker), tap the "End existing" toggle, hit
    the mint-green Switch button to commit. Shortcut for the in-app
    Switch screen with the same mental model.
  - **Recent fronts**: timeline of the last few front transitions
    with avatars + relative time, "+" suffix on ongoing entries. Tap
    opens the new history viewer screen.
- Wear OS front history viewer screen, accessed from the main menu's
  new History chip or by tapping the Recent fronts tile. Shows recent
  fronting-set entries, newest first, each row with avatars + names +
  relative time.
- Main menu reorder: Currently Fronting / Switch Front / Members /
  Groups / History / Settings. Switch is the most-used action so it's
  in the second slot now.
- Avatar caching pipeline for tiles: members' avatars are downloaded
  + cropped to circles + cached as 80x80 PNGs on each sync, so tiles
  can render them without re-fetching.

### Fixed

- Phone app no longer gets silently logged out when the wear app is
  in active use. The watch now provisions its own companion session
  via `POST /v1/auth/sessions/secondary` and rotates an independent
  refresh JWT, so refreshes on either device can't trip the other's
  reuse-detection path.
- Last-switch complication shows the actual switch time on first sync
  instead of "1m ago" right after install. Derives the timestamp from
  the freshest `started_at` across current fronts when no cached
  signature is present.
- Switch screen's floating commit chip moved closer to the bottom
  edge and shortened so the round bezel doesn't waste space below it.
- Home screen's Switch Front chip text now centred to match the in-
  page commit chip on Switch.

## [0.1.6] - 2026-05-07

Play developer console is full of footguns and very easy to burn a version code. Kick off a new build again /headdesk

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
