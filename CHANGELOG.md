# Changelog

All notable changes to the Sheaf Android client are recorded here. Format loosely
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the project
uses semantic versioning (`MAJOR.MINOR.PATCH`).

## [0.1.15] - 2026-05-22

### Added

- **Selection and active-state rings on the watch tiles.** The
  quick-switch tile draws a thin accent ring around members picked for
  the next switch (in addition to the existing ✓), so the pending set
  reads at a glance. The member-tracker tile rings members who are
  currently fronting. Both use the same green accent, so a ring means
  "active / selected" consistently across tiles. Avatars stay the same
  size selected or not, so toggling doesn't reflow the grid.

- **Watch switches survive an offline watch.** Switches committed on
  the watch used to hit the API directly and silently fail when the
  watch had no network. They now try the API first, hand the switch
  to the phone via DataLayer if direct fails (the phone has a
  durable PendingFrontSwitch queue and is the more reliable mover),
  and persist locally on the watch as a final fallback for replay
  from the next refresh. Each replay carries the original timestamp,
  so the resulting front history reflects when the user actually
  switched rather than when connectivity came back.

### Changed

- **Server-URL step defaults to `app.sheaf.sh`.** First-run login
  used to disable the Continue button until something was typed,
  forcing every hosted-service user to manually enter the domain
  that's right there in the placeholder. Continue is now always
  enabled; a blank field resolves to `app.sheaf.sh` on press, and
  the helper text says so explicitly. Self-hosters still type their
  own URL exactly as before.

- **Home pull-to-refresh actually shows a spinner now.** Refreshing
  against an already-populated screen used to flip `isLoading` to
  false the instant the call left the gate, so a fast cached response
  left the spinner invisible and users wondering whether the gesture
  registered at all. The spinner now stays up for the whole fetch
  with a 600ms minimum, so even sub-100ms refreshes read as a real
  refresh gesture.

- **Offline switches preserve their full history on sync.** A string
  of switches made while offline used to coalesce to the most recent
  one, and the resulting front got `startedAt = now` at sync time
  rather than when the user actually switched. Now every queued
  switch replays in order with its original timestamp, so the
  timeline reflects what actually happened across an offline period.
  Same fix applies to front-removals' `endedAt`.

### Fixed

- **`sheaf.sh/redeem` App Link opened the app to the home screen
  instead of the redemption flow.** The 0.1.13 instance-aware redeem
  threaded the link's `instance=` value through the nav route as a
  query arg, and a URL-encoded value (the instance URL contains
  encoded slashes) silently failed the route match — the redeem
  navigate threw inside its LaunchedEffect, the login effect's home
  navigate ran next, and the user ended up on home with the holder
  cleared. The instance hint now lives in PendingRedemptionHolder and
  is consumed by the redeem ViewModel, so the nav route stays a plain
  path-arg destination.

## [0.1.14] - 2026-05-22

Fixes a missing scrollbars flagged during Play Store review on the
WearOS app.

## [0.1.13] - 2026-05-19

Closes the loop on watchface freshness, lets the member-set tiles hold
arbitrarily long rosters, and wires proper App Links so magic links
open the app instead of bouncing through the browser.

### Added

- **Verified App Links for `sheaf.sh/redeem`.** Magic-link redemption
  URLs on the canonical domain now open the app directly (no browser
  hop, no chooser) via an `autoVerify` intent filter backed by the
  domain's `assetlinks.json`. The `sheaf://notifications/redeem`
  custom-scheme fallback stays for self-hosted instances on domains
  the project can't verify.
- **Instance-aware redemption.** The redeem link carries the instance
  it was minted for; the app now refuses a link aimed at a different
  Sheaf server than the device is signed into, naming both hosts,
  rather than redeeming against the wrong server and surfacing an
  opaque "expired or already redeemed".
- **Paginated member-set tiles.** The member-tracker and quick-switch
  tiles now page rosters larger than 8 behind a tap-to-advance page
  chip instead of truncating. The tracker previously capped at 8 with
  a "+N" badge that ate a grid slot (so picking 9 showed only 7);
  quick switch silently dropped everyone past 8. The tile picker
  states "shows 8 per page" up front and the Save button reflects the
  resulting page count.
- **Phone-to-watch front-status push.** After any front change on the
  phone (a switch, or an incoming push) the phone nudges a paired
  watch to re-sync, so watchface tiles and complications refresh
  without waiting for the wear app to be foregrounded. Previously the
  watch only synced in the foreground, leaving the watchface stale.

### Fixed

- **Four wear screens were still missing their scrollbar.** The 0.1.12
  PositionIndicator sweep missed the main menu, home, members, and
  group-detail screens (three even kept a dangling unused import where
  the wiring was dropped), so Play review rejected the build again for
  lists without a scroll affordance. All four now bind a list state and
  host a PositionIndicator like the rest.
- **Front complications looked stuck on "FRONT".** With no fronters
  the SHORT_TEXT complication rendered a lone em-dash body next to
  the constant "front" title, reading as a blank slot that never
  refreshed. The empty state now reads "None" so a genuine
  no-fronters result is legible.

## [0.1.12] - 2026-05-18

Tracks the backend's mobile-push unification, lands the wear OS Play-
review fixes, sharpens up the various "no data" UX surfaces that were
all reading the same regardless of whether something was loading,
failed, or just empty, and a couple of perf wins on the way through.

### Added

- **One Android NotificationChannel per redeemed Sheaf channel.**
  Already mostly shipped client-side in v0.1.11; this release tracks
  the backend `6481d8a` that unified the per-platform `fcm` /
  `apns_dev` / `apns_prod` channels into a single `mobile_push`.
  Create-channel form now sends `mobile_push` (was `fcm` and 4xx-ing
  on submit), with "Mobile push" copy that calls out the per-account
  fan-out so senders aren't surprised when deliveries reach the
  recipient's iPad. Stale `fcm` / `apns_*` rows map to the same
  display label for back-compat.
- **"Paused by sender" tri-state on Receiving.** Channels the sender
  paused now read as paused (neutral) instead of disabled (error),
  so recipients can tell "the sender will resume this" from "this
  destination is dead, you need to take action".
- **Re-pair watch shortcut.** New entry under Settings → Account
  below Active Sessions. Drops the cached watch session and asks the
  server for a new one via `force=true`, then writes the credentials
  DataItem the watch consumes. Use when the watch is stuck on
  "Open Sheaf on phone" despite the phone being signed in (typical
  after a backup-restored reinstall or environment switch).

### Fixed

- **Wear OS Play review rejection.** Two reasons in one commit:
  - Background was a dark blue (`0xFF13121E`) rather than the pure
    black Play guidelines call for on AMOLED watches. Both background
    and surface now flip to `Color.Black`.
  - Lists had no scroll affordance. Every ScalingLazyColumn screen
    (menu, home, members, groups, add member, history, member
    profile, settings, login, group detail, switch, member-tracker
    config, complication picker) now hosts a `PositionIndicator`
    via the new `SheafScalingLazyScaffold` helper.
- **Wear tiles treated three different "no data" states the same.**
  Quick switch, fronting variants, history, and member tracker all
  fell through to terse positive-empty messages or "Open Sheaf on
  phone" regardless of whether they were loading, had failed to
  sync, or were genuinely empty. WearStore now writes a load-status
  marker (`loading` / `ok` / `failed`) into the existing tile_data
  prefs, and each tile branches:
  - signed out → "Open Sheaf on phone to sign in"
  - signed in + loading → "Loading…"
  - signed in + failed → "Couldn't load — open app to retry"
  - signed in + ok + empty → existing positive-empty messages
- **Recent fronts viewer's "no history" state was misleading.** Same
  bug, wear-side: rendered "No history yet" whether refreshing,
  failed, or actually empty. Now four explicit states: loading
  spinner (no data + loading), failed banner + retry chip (no data
  + last refresh failed), the existing empty message (no data +
  successful refresh), and the list with a quiet inline spinner +
  "showing cached" advisory when a background refresh fails after
  earlier success.
- **Deep-link entry stacked the menu under the target screen.**
  Tapping a tile that opens a specific wear screen used to land on
  `[NAV_MENU, NAV_HISTORY]`, so swipe-back went menu → exit instead
  of straight exit. Now `popUpTo(NAV_MENU) { inclusive = true }` so
  swipe-back from a deep-link entry exits to the watchface, matching
  expected gesture behaviour.

### Performance

- **Front history avatar cache.** Same member's avatar was
  re-decoded on every history row instead of hitting Coil's cache.
  Two-part fix: bumps memory cache to 30% and disk cache to a
  100MB hard cap (was the implicit 2%-of-free default that landed
  small on new installs); prefetches every distinct avatar URL the
  moment the member roster arrives so subsequent AsyncImage requests
  hit warm. respectCacheHeaders flipped off since avatar URLs are
  content-addressed and don't need 304 validation round trips.

## [0.1.11] - 2026-05-13

Polish pass: home screen renders instantly from cache then refreshes,
the groups screen doubles as a viewer, front history gets numbered
pagination, the persistent fronting notification finally responds to
taps, and each redeemed Sheaf channel gets its own Android
NotificationChannel so users can tune them independently.

### Added

- **Inline member expansion on the groups screen.** Tapping a group
  card now expands a member list (avatar + name + pronouns) inline
  instead of jumping straight to the editor. The rightmost edit
  icon is now the only path to the actual edit screen, so the list
  doubles as a viewer. Members are fetched lazily per-group on
  first expand and cached for the session.
- **Numbered + load-more pagination on front history.** Two view
  modes toggleable from a chip pair at the top of the History
  screen. "Load more" (default) keeps the existing cursor-based
  infinite-scroll behaviour and adds an explicit "Load older
  entries" button. "Pages" mode uses offset-based pagination with
  a footer bar (first / prev / "Page X of N" / next / last). Page
  size picker (25/50/100/200) to the right. Both choices persist
  across launches and mirror the web client's pref keys so the
  view stays consistent across surfaces.
- **One Android NotificationChannel per redeemed Sheaf channel.**
  Each subscription the user redeems now gets its own entry in
  system settings (id `sheaf_ch_<server_id>`, label "<channel
  name> · <system label>"). The fixed broad-category channels
  (front_change / reminders / system) stay as fallbacks, renamed
  "...(general)" in system settings so it's clear they're catch-
  alls. Channels for unsubscribed Sheaf channels are pruned on
  next sync so the settings page doesn't grow forever. Routing
  by `data["channel_id"]` is wired client-side and will activate
  the moment the backend includes that field in the push payload
  (separate ask filed on the backend side).
- **Build stamp on the wear unsynced screen.** Already shipped in
  v0.1.10 but worth flagging — when pairing isn't working the
  "Open Sheaf on phone / Sign in manually" screen shows
  `Sheaf <version> · <commit>` at the bottom to confirm whether
  the watch is on a stale build.

### Fixed

- **Home screen is slow to paint.** The seven endpoints `load()`
  fired (`getMe`, `getOwnSystem`, `getCurrentFronts`, `listMembers`,
  `getAnnouncements`, `getSystemSafety`, `getRetention`) ran
  sequentially. None of them depend on each other, so the chain
  was paying the round-trip cost N times in series. Now they fan
  out via `async {}` inside a `coroutineScope`, with
  `getCurrentFronts` queued first so the most user-visible piece
  of data lands earliest on the wire. The screen also paints from
  the local cache immediately on entry rather than waiting for
  the network round-trip, with a new "Showing cached data - tap to
  retry" chip surfacing when the refresh failed but cache had
  data. The switch sheet's N+1 group-members fetch got the same
  fan-out treatment.
- **Persistent fronting notification did nothing when tapped.** The
  ongoing Currently Fronting notification had no content intent.
  Now wires a `PendingIntent.getActivity` that brings the existing
  MainActivity task to the foreground (SINGLE_TOP | CLEAR_TOP, not
  a duplicate launch) using FLAG_IMMUTABLE per API 31+ rules.

## [0.1.10] - 2026-05-12

Home-screen widgets paralleling the wear tile set, plus two critical
fixes for first-launch failure modes that bricked phone+watch pairing
and the magic-link redemption flow.

### Added

- **Home-screen widgets.** Five Glance widgets covering the same
  ground as the wear tiles:
  - **Currently Fronting** with real avatars next to names
  - **Avatars-only** compact strip (single circle up to a 6-up row)
  - **Member tracker** — pick members at add-time, watch the widget
    flip a "Fronting now / Not fronting" label per row
  - **Quick switch** — picked members are tappable; each tap opens
    a confirm dialog before POSTing the front change
  - **Recent fronts** — newest-first ledger with relative time
  All five share a 96px circular-avatar cache in filesDir so adding
  multiple widgets doesn't multiply the network cost. Member tracker
  and quick switch ship config activities behind APPWIDGET_CONFIGURE
  so each widget instance has its own member selection.
- **Build stamp on the wear unsynced screen.** When pairing isn't
  working, the "Open Sheaf on phone / Sign in manually" screen now
  shows `Sheaf <version> · <commit>` at the bottom so users can
  rule out "watch app is on a stale version" without uninstalling.

### Fixed

- **Pairing silently broken after reinstall from Play Store.**
  Android Auto Backup was restoring DataStore (phone) and the wear
  auth SharedPreferences (watch) from cloud snapshots, bringing back
  revoked access/refresh tokens. The local "watch token already
  cached → skip mint" fast-path then treated those zombie tokens as
  valid, the phone pushed dead tokens to the watch via the Data
  Layer, and neither side ever called `/v1/auth/sessions/secondary`.
  Auth-bearing prefs are now excluded from both Auto Backup and
  device-transfer on phone and watch. A watch-side
  `/sheaf/credentials/request` message is also now treated as a
  force-re-mint signal so anyone with a backup-restored bad install
  self-heals on the next watch retry. Diagnostic logs under tag
  `SheafPairing` at every checkpoint of the flow — previously this
  path logged nothing at all.
- **Magic-link redemption looped to ProtocolException.** When the
  redeem endpoint returned 401, the OkHttp authenticator refreshed
  the access token and retried — got 401 again — refreshed —
  retried — twenty rounds until OkHttp aborted with "Too many
  follow-up requests". User saw a generic failure with no actionable
  signal. `/notifications/redeem` is now in the no-retry exempt list
  alongside `/auth/refresh` and friends, so a 401 surfaces to the UI
  immediately. The 401 itself is a backend-side bug (filed
  separately); a server fix is required for end-to-end redemption to
  succeed.

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
