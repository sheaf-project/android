# Changelog

All notable changes to the Sheaf Android client are recorded here. Format loosely
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the project
uses semantic versioning (`MAJOR.MINOR.PATCH`).

## [Unreleased]

### Added

- **Import and export OpenPlural.** OpenPlural v0.1 is an interchange
  format shared across plural apps. You can now import an OpenPlural
  `.json` or `.openplural.zip` (with images) export from another app, and
  export your own data in OpenPlural format.

- **Export got a proper home.** Settings now has an Export data screen
  with a format picker (Sheaf or OpenPlural), a quick JSON-only export,
  and a "full backup with images" option. The full backup builds in the
  background (confirmed with your password, plus your authenticator code
  if you use 2FA) and then appears in a Recent backups list to download.

- **Support screen.** Settings now has a Support entry showing your
  instance operator's contact, service status, and policy links (when
  they've set them), plus links to the project source and security
  contact.

### Fixed

- **Front history editor rejects an end before the start.** Editing a
  front entry now warns and blocks saving when the end time isn't after
  the start time, instead of saving a backwards range.

## [1.1.1] - 2026-06-19

### Added

- **Member banners.** Members can now have a wide 3:1 banner image,
  shown as a header on the member profile. Member lists stay
  banner-free so they remain quick to scan. The pick-and-crop editor
  was reworked to match the web cropper: you can rotate (two-finger
  twist, quarter-turn buttons, or a fine slider) and zoom out below
  "fill" to frame a whole image edge-to-edge, with any uncovered area
  kept transparent. Avatars use the same editor, so they gain rotation
  and zoom-out too.

### Fixed

- **Member edit no longer loses unsaved changes silently.** Leaving the
  member editor (back arrow or system back) with pending edits now
  prompts to save, discard, or stay, instead of dropping the changes
  without warning. The editor is a long scroll with its Save button at
  the bottom, so this was easy to trip over.

- **Wear: the watch recovers a lost session without re-pairing.** If the
  watch's companion session goes away while the app is open (a token
  refresh that failed while disconnected, or a stale cached credential
  that the server rejects), the watch now asks the phone to re-mint and
  push fresh credentials instead of stranding on "Open Sheaf on phone".
  "Retry Sync" does the same, rather than re-applying the same stale
  cached credential that signed you out.

- **Wear: tiles and complications stop getting stuck after a reconnect.**
  Two fixes: they now refresh as soon as credentials arrive, so they
  leave the signed-out message immediately instead of at their next
  scheduled update; and tile avatars re-fetch when the cached set changes
  (after a re-pair, or when a download that failed on a not-yet-settled
  network later succeeds). Avatars previously stayed missing, or a member
  with no avatar showed as an invisible gap rather than their coloured
  initials, until the tile was deleted and recreated. A transient
  download failure also no longer overwrites a good cached avatar with
  initials.

## [1.1.0] - 2026-06-12

### Added

- **Two more import sources: PluralSpace and Prism.** PluralSpace imports
  from a `.zip` data export (no credential), mirroring the Simply Plural
  flow with a preview, member selection, and per-entity toggles. Prism
  imports a passphrase-encrypted `.prism` export; the passphrase is sent
  only as the import job's per-source credential and cleared from memory
  once the server holds the encrypted copy.

- **Import a complete Sheaf backup, with images.** The Sheaf import now
  accepts the complete-backup zip (`export.json` + `images/`), not just
  the plain JSON export. It auto-detects the archive, shows the image
  count in the preview, submits it so the server unpacks the images, and
  reports images imported in the result. Plain JSON exports work exactly
  as before.

- **Admin: import-job inspection and GDPR data export.** The account
  detail screen now lists a user's import jobs; opening one fetches the
  full event log behind a reason prompt (a logged, privacy-sensitive
  read). A "Data export" action builds the GDPR Article 15 metadata
  dossier and saves it as a JSON file via the system file picker.

### Changed

- **Wear: switch picker ordered by quick-switch ranking.** The watch's
  Switch Front screen now lists members in the same pins-then-recency
  order the phone uses for its quick-switch carousel (the top-fronters
  endpoint), so the members you switch to most sit at the top. Falls
  back to plain roster order if the ranking can't be fetched.

- **Imports stream the file instead of buffering it.** The Sheaf, Simply
  Plural, PluralKit, and Tupperbox file importers used to read the whole
  picked file into memory before upload. A complete Sheaf backup can run
  to 100MB, so that was a heap allocation the size of the file. They now
  stream straight from the picked URI, keeping memory flat regardless of
  file size.

### Fixed

- **Front history edit: marking a front ongoing now works, and time
  entry no longer jumps to the start.** Toggling a front back to ongoing
  sent an empty update the server read as "no change", so the old end
  time stuck; it now explicitly clears the end time. Editing a start or
  end time reset the cursor to the first character on every keystroke;
  the field is now the source of truth while you type.

### Security

- **Wear: quick-switch tile trampoline validates its intent.** The
  invisible activity the quick-switch tile launches to toggle and commit
  switches is necessarily exported (the tile host launches it), so its
  intent is now treated as untrusted: a toggle is accepted only for a
  member the tile was actually configured to show, and a commit
  intersects the selection with that configured set. A forged intent
  can no longer queue or commit a switch to an arbitrary member or
  against an unconfigured tile.

## [1.0.0] - 2026-06-11

First production release.

### Added

- **Editable notification channels.** Channel detail screen no longer
  shows triggers as read-only: name, all three trigger toggles,
  cofront redaction (when the cofront trigger is on), base set
  (all members vs none + include-private), group rules, member
  rules, payload sensitivity, debounce, aggregation window, and
  quiet hours (start / end / IANA tz) are all editable. Sticky
  Save / Discard bar at the bottom appears when a draft has
  pending changes. Overflow menu adds Pause / Resume, Send test,
  Duplicate, and Delete. Mirrors the web client's editor.

- **Admin audit log.** Settings → Account gains an "Admin activity"
  screen showing every administrative action taken on your account
  (who, when, why, and what changed), via the new
  `/v1/auth/admin-activity` endpoint. Admins additionally get a
  full audit-log viewer in the Admin panel, filterable by action
  type, over `/v1/admin/audit-events`. Each entry expands to show
  the reason and a before/after diff.

- **Admin account moderation.** The admin user editor gains suspend
  (with optional duration), permanent ban, and lift-suspension /
  lift-ban actions, each prompting for an audited reason. The user
  row shows suspended / banned status in the error colour.

- **Admin account detail / triage.** Each user row in the admin panel
  opens an account-detail screen: a one-shot dossier (status, tier,
  2FA, signup IP, session and API-key counts, system summary, recent
  admin actions), the user's active sessions with per-session revoke,
  and a revoke-all-API-keys action. Session and key actions prompt for
  an audited reason.

- **Admin maintenance jobs + ops.** A Maintenance jobs screen lists
  the backend's scheduled jobs with their last-run status and a
  manual Run button, plus the shared-Pushover monthly usage. The
  account-detail screen gains emergency operator overrides (reset
  system safety, finalize pending safety actions), and the pending-
  approvals list gains an Approve-all action. (The import-job event
  viewer and GDPR dossier export are tracked separately as they need
  file-handling UI.)

### Fixed

- **Widgets: profile pictures, stuck loading, and sizing.** After a
  real-device pass: avatars now display on all widgets (not just the
  recent-switches list); the member-tracker widget no longer hangs on
  "loading" forever; widgets resize responsively in width (columns) as
  well as height; and default placement sizes are smaller, so a widget
  drops in at a sane size and expands from there.

- **Widget avatar cache no longer grows without bound.** Stale avatar
  PNGs (from deleted members or removed widgets) are now swept after a
  two-week grace period. The sweep is age-based rather than keyed to
  any single widget's member list, so it can't wipe avatars another
  widget is still using.

- **Offline switch queue no longer wedges on an un-replayable entry.**
  The sync worker retried the whole pending-switch / removal queue on
  any failure, so a single entry that could never succeed (a member
  deleted server-side while offline, an empty member set, a rejected
  payload) retried forever under backoff and blocked every later
  queued switch behind it. Permanent failures (a 4xx that isn't auth
  or rate-limiting) now drop that one entry and move on; genuinely
  transient failures (no network, 5xx, auth, rate-limit) still retry
  the queue intact.

- **Wear: complications and tiles stopped updating until the app was
  opened.** When fronting changed, the phone sent the watch a
  content-free "re-sync" nudge and made the watch re-fetch the state
  over its own network. If that fetch failed (stale token, watch
  off-network, the listener process killed mid-fetch), the snapshot
  and the complication/tile refresh were both skipped, so the
  watchface froze on old data even though the watch was paired. The
  phone now ships the current fronter snapshot in the nudge payload;
  the watch applies it to its tile/complication cache and refreshes
  immediately, with no dependence on its own backend connectivity. A
  best-effort network re-sync still runs afterwards to fill in the
  roster, history and avatars. The push apply runs even when the
  watch's session token has gone stale, since it's just rendering
  data the phone already computed.

- **Wear: member names with commas corrupted the watch snapshot.**
  The fronter / member data the watch hands to its tiles and
  complications was encoded with a hand-rolled JSON writer/parser
  that split objects on commas and only escaped quotes, so a member
  named e.g. "Bob, Jr." mangled the entire snapshot (wrong or
  missing names across every tile and complication). Both sides now
  go through Moshi, which escapes correctly. Existing snapshots
  still decode, so no resync is forced.

- **Wear: fronters complications wasted the slot on a "FRONT"
  label.** The currently-fronting text complications rendered a
  static "front" / "Fronting" title that, on a narrow SHORT_TEXT
  slot, pushed the actual member names out of view (you'd see just
  "FRONT" with no names). Dropped the title so the names fill the
  slot; the accessibility description is unchanged.

- **Wear: switch-confirm button clipped on round watches.** The
  pinned "Switch (N)" / "Clear Front" action chip on the Switch
  Front screen used a fixed bottom-edge layout that worked on
  rectangular wears but had its rounded corners cut off by the
  curved bezel on Pixel Watch and other round devices (Play
  caught this in review). On round screens the chip now lifts
  ~18dp clear of the bottom edge and narrows to ~62% width so
  its corners stay inside the circle; rectangular wears keep the
  original bottom-pinned look.

### Security

- **No request/response logging in release builds.** The OkHttp
  logging interceptor was set to BODY level unconditionally, so
  release builds wrote full request and response bodies (bearer /
  refresh tokens, watch-token activation codes, member names) to
  logcat, where adb / a captured bug report could read them.
  Release now logs nothing; debug keeps body logging for local work
  but redacts the Authorization / Cookie headers.

- **Wear credentials encrypted at rest.** The watch stored its
  session access + refresh tokens in plaintext SharedPreferences,
  readable from a rooted watch or over adb. They now live in
  EncryptedSharedPreferences backed by a hardware-bound Keystore
  key. Existing watches migrate their tokens transparently on
  upgrade (no re-pair needed) and the old plaintext copy is wiped.

## [0.1.19] - 2026-06-04

### Added

- **Per-member custom field values.** Members edit screen gains a
  Custom fields section with type-appropriate editors: text /
  number / date picker / boolean switch / select dropdown /
  multiselect chip group / freeform tag input. Member profile
  surface displays them read-only with sensible formatting (date
  → localised, boolean → Yes/No, multiselect → comma-joined). The
  per-field privacy gate is enforced server-side; the profile
  hides the whole section when the viewer has no visibility.

- **Custom field choices editor.** Select / multiselect field types
  now expose a choices editor in the create / edit dialogs.
  Empty choices = freeform tag mode (server accepts any string);
  non-empty = strict pick-from-list. Choices persist server-side
  in the field definition's options blob.

- **Polls: restrict voting to currently-fronting members.** Poll
  create form gains a "Who can vote" section with a switch. When
  on, voting (and withdrawing votes) is gated to members in the
  active front; the detail screen shows an AssistChip explaining
  the rule and an inline error when the picked voter isn't
  currently fronting. Default off — matches the journals /
  messages "any member may author" model.

- **Markdown code-block syntax highlighting.** Journals, member bios,
  board messages, and scratchpad notes now colour code blocks per
  language. Built-in support for Kotlin, Java, Python, JavaScript /
  TypeScript, JSON, YAML, bash, SQL, HTML, CSS. Unknown languages
  still render monospaced on the themed background. Token colours
  pull from the active palette so theme swaps recolour code live.

- **Theme sync toggle.** Settings → Appearance gains a "Sync theme
  across my Android devices" row. ON (default): theme mode + palette
  follow the account so every Android device on the same account
  converges on the latest choice. OFF: this device keeps its own
  pin and the backend is untouched, leaving other devices on
  whatever they had.

- **Reply to board messages.** Each message gets a Reply icon at the
  trailing edge of its header. Tapping it puts the composer into
  reply mode with a "Replying to X" banner above the text field and
  an × to clear. The quoted-parent card on a reply now scrolls to
  the parent message in the loaded page when tapped (no-op when the
  parent is older than the current batch — paginated load is a
  follow-up).

- **Widgets follow the in-app palette.** All six home-screen widgets
  (Fronting, FrontingWithAvatars, FrontingAvatarsOnly, MemberTracker,
  QuickSwitch, RecentFronts) now render in the user's selected
  Sheaf palette rather than dynamic Material You colours. Material
  You stays as a sentinel that falls through to wallpaper-derived
  dynamic colours.

- **Widget display-mode toggle.** QuickSwitch and MemberTracker
  config screens gain a three-way choice (Avatar + name / Avatar
  only / Name only) mirroring the wear tile. Per-widget-instance —
  multiple widgets on the same home screen can each have a
  different mode. Avatars-only MemberTracker still surfaces the
  fronting indicator as a small primary dot.

- **Recent-fronts widget shows avatars** for the leading member of
  each row, alongside more responsive sizing — all three list
  widgets (RecentFronts / QuickSwitch / MemberTracker) now use the
  actual host-allocated space (SizeMode.Exact) instead of snapping
  to three discrete buckets and wasting half the area. Smaller
  default cell footprint (3×2) too, with the same resize-up
  behaviour.

- **Front custom status.** Front entries support an optional
  per-entry note (server-side `custom_status`). Surfaces:
  - "Custom status (optional)" input on the home switch sheet and
    the history add / edit sheet.
  - Italicised quoted line under the Fronting-for stamp on home
    current-fronts cards.
  - Same quoted italics under the time range on history rows.
  Empty / whitespace trim → null on submit. Offline-queue replay
  carries the status through to the eventual createFront call
  (DB v2 → v3 migration nullable-by-default).

### Fixed

- **Quick-switch carousel no longer clips into the system nav bar.**
  Scaffold's bottomBar slot doesn't auto-inset for the system
  navigation bar, so the chip row was getting clipped a few pixels
  by the gesture / 3-button nav. Now lifted with
  navigationBarsPadding + a small breathing-room gap.

## [0.1.18] - 2026-06-02

### Added

- **Avatar crop / pan editor.** Picking a photo for a member or system
  avatar now opens a full-screen crop dialog with pinch-to-zoom and
  drag-to-pan inside a circular preview window. The image is clamped
  to always cover the crop area (no white space pannable into the
  avatar) and EXIF rotation is honoured on decode so portrait photos
  arrive right-side-up. Output is a square 512×512 JPEG, encoded off
  the UI thread so the save tap doesn't stutter. Replaces the previous
  pipe-it-straight-to-upload flow that left the display layer to
  square-crop arbitrary inputs.

- **PluralKit and Tupperbox importers.** Brings Android importer
  coverage in line with iOS / the backend's supported sources. Three
  new entries under Settings → Data:
  - **PluralKit (file)**: pick a `pk;export` JSON; system profile,
    groups, and front-history toggles. Front history is off by default
    since switch logs can run thousands of entries.
  - **PluralKit (API)**: paste a PK token (show/hide toggle); preview
    fetches the system from PK live, then submit enqueues the import
    via `/v1/imports/api`. Token is request-scoped server-side:
    encrypted at rest while the job runs, wiped on finalize, never
    logged.
  - **Tupperbox**: smallest surface of the three — TB exports carry
    only tuppers and groups (no system metadata, no fronting history),
    so the import screen is correspondingly stripped down.

- **Import history viewer.** Settings → Data → Import history shows the
  user's past and pending imports (any source). Each row carries a
  source label, status badge, short counts summary, and a relative
  timestamp. Tap to see the full counts table, warnings/errors, and
  any failure message; the detail screen polls every 1.5 s for jobs
  that aren't yet terminal so a running import lands without manual
  refresh. Pending jobs can be cancelled, terminal-not-archived jobs
  can be archived; running jobs return a 409 from the backend
  (cooperative mid-flight cancel is server v2) and surface that as an
  inline message.

### Fixed

- **Analytics percentages no longer 100× too large.** The
  `percent_of_window` field on `/v1/analytics/fronting` is already a
  percent value server-side (e.g. `52.93` for "53% of window"), but
  the stats screen was multiplying by 100 again, producing labels
  like "5293.0% of window" for a member with ~53% real share. Now
  uses the value as-is; can still legitimately exceed 100 % under
  co-fronting (two members fronting together for the whole window
  each come back at 100 %).

- **Simply Plural and Sheaf imports no longer 404.** Backend retired
  the per-source synchronous submit endpoints (`/v1/import/simplyplural`,
  `/v1/import/sheaf`) in favour of an async job runner at
  `/v1/imports/file`. The clients hadn't migrated, so every
  "run import" tap landed on a 404 (previews still worked since
  those routes were preserved). Both importers now submit via the
  new endpoint, poll the job until terminal status, and decode
  counts/events back into the existing result UI. Warnings collect
  the per-record `events` rows at level=warning; failures surface
  the job's `last_error`.

## [0.1.17] - 2026-05-31
Six new themes: Crimson, Goldenrod, Plural, Bi, Pan, Asexual    

## [0.1.16] - 2026-05-30

Theme picker — choose between ten palettes (Purple, Classic, OLED,
Material You, Mint, Ocean, Sepia, Pride, Trans, Non-binary) under
Settings → Appearance, orthogonal to the existing light / dark /
system mode.

### Added

- **Theme palette picker.** Settings → Appearance gains a Palette
  section below the existing mode rows, rendered as a FlowRow of
  swatch cards. Each card previews the palette's background plus
  three accent dots. Tapping a card applies the palette live across
  the app and persists the choice in DataStore.

- **Real User-Agent on API requests.** Phone and watch were both
  sending OkHttp's default `okhttp/<lib version>` as User-Agent on
  API calls (the existing `UserAgentInterceptor` only ran on image
  loading). The Trusted Devices list reads this header to label
  sessions, so the user's own device showed up as `okhttp/4.12.0`
  next to actual browser sessions. Phone now sends
  `Sheaf Android/<version>`, watch sends `Sheaf Android Wear/<version>`
  so the two stay distinguishable on the same account. New sessions
  pick up the new label; existing sessions on the server keep what
  was recorded when they were minted.

- **Ten-palette catalog.** Purple (the iOS-aligned default) plus
  nine alternatives:
  - **Classic** — the pre-iOS-alignment palette (`#534AB7` darker
    purple) for users who preferred the older look.
  - **OLED** — pure `#000000` background and surface, closes the
    AMOLED-friendly dark task. Light mode mirrors Purple.
  - **Material You** — wallpaper-derived dynamic colour scheme on
    Android 12+. Pre-S devices fall through to Purple.
  - **Mint** — Tailwind emerald scale, calm green chrome.
  - **Ocean** — Tailwind sky scale, clean blue chrome.
  - **Sepia** — Tailwind amber on stone-warm surfaces, easier on
    the eyes at night.
  - **Pride** — pink-led, rainbow-spanning accents on neutral
    surfaces.
  - **Trans** — flag pink + flag blue.
  - **Non-binary** — flag purple-led, with the flag yellow as a
    vivid secondary.

  Palette selections are persisted in DataStore as `theme_palette`
  alongside the existing `theme` key for mode; the two are
  orthogonal (mode picks light vs dark, palette picks which hues).
  Selection mirrors to the server's per-client settings bag like
  the mode choice does, so future cross-device sync can read it.

## [0.1.15] - 2026-05-28

### Added

- **Analytics screen.** New screen reached from a stats icon next to
  the "Add entry" button on History (matching where the iOS app
  surfaces it). Consumes the backend `/v1/analytics/fronting`
  endpoint and shows: a 7d / 30d / 90d / 1y window picker, a totals
  card (total front time + active member count over the window), a
  per-member breakdown with member-coloured "% of window" bars and
  session count + longest session, and a 24-bar hour-of-day chart
  aggregated across all members in the device's local timezone.
  Hand-rolled on Canvas rather than pulling in a charting library
  for one bar chart.

- **Quick-switch carousel on home.** A horizontal row of one-tap
  member chips pinned to the bottom of the home screen, above the
  navigation bar — always visible regardless of scroll position.
  Populated from the backend's new `/v1/members/top-fronters`
  endpoint (pinned members first, then a recency-weighted score with
  a 30-day half life), falling back to the alphabetical member list
  if that endpoint isn't reachable so the carousel never disappears
  purely because of a deployment skew. Tap a chip to switch using the
  system's default replace-fronts behaviour; long-press to choose
  "Switch (end current)" vs "Add to front" explicitly, so the
  override is one gesture away. The full switch sheet with group
  filtering still lives behind the Switch FAB.

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

- **About row stamps build timestamp + flavour + versionCode.** Used
  to read `Sheaf 0.1.15 · abc1234 · debug`; now also carries the UTC
  build time and which distribution flavour the APK was assembled
  from, so the "wait, is this build the one I just installed" moment
  is answerable at a glance without digging through aapt2 or Play
  Console. Local dev builds also derive their versionName from the
  most recent git release tag with a `-dev` suffix (e.g.
  `0.1.14-dev`), so About no longer reads `Sheaf 0.1.0 · …` forever
  on every working-tree build.

- **Notifications icon on the home top bar.** Notifications (owned
  channels, your subscriptions, your devices, reminders) was buried
  two taps under Settings, despite being a first-class entry in the
  web sidebar. Now reachable in one tap from Home, in the row of
  top-bar icons next to Messages and Settings.

- **Polls is now a top-level bottom-nav tab.** Was a top-bar action on
  the home screen, easy to miss; now sits next to Home / Members /
  History / Journals in the bottom navigation, matching the iOS app's
  five-tab layout. The redundant home-screen polls icon is gone.

- **Purple theme aligned to the iOS app.** Light-mode primary is now
  violet-500 (`#8B5CF6`) where it was a darker, more saturated
  `#534AB7`. Dark-mode background is the iOS purple-tinted `#0F0C29`
  (was a more neutral grey `#13121E`), and dark surface is `#1A1535`.
  Same brand colour across both clients now. A theme picker covering
  alternate palettes is on the todo list.

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
