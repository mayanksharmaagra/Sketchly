# Sketchly — AI Agent Context

> **A messaging app where every message is a hand-drawn note/doodle, delivered instantly and surfaced on the Home Screen via widgets.**

> **Last Updated:** **Block filter race-condition fix + viewer recipient name resolution.**
> - `getConnectedContacts()` in `ContactRepository` now calls `getBlockedIds(uid)` on every Firestore snapshot and filters before emitting — same pattern as `getSuggestedContacts()`. Blocked users disappear from the contact list and `SendToScreen` immediately, without waiting for the Cloud Function.
> - `DrawViewModel` injects `BlockRepository` and exposes `blockedIds: StateFlow<Set<String>>`. Both `contacts` and `contactGroups` flows `combine(…, blockedIds)` and filter — second line of defence on top of the repository-level fix.
> - `SketchlyViewerScreen` / `ViewerViewModel`: added `recipientDisplayNames: Map<String, String>` to `ViewerUiState`. After loading a sketch, if `isSender == true`, a one-shot `.first()` collect of `getConnectedContacts()` builds a `uid → displayName` map. Top bar now shows the real recipient name instead of the raw Firebase UID.
> - `BlockRepository` + `BlockViewModel` + `BlockedUsersScreen` added; `ProfileScreen` block/unblock flow wired up.
> - `BlockRepository.blockUser()` uses a single-doc write (client owns only `blocks/{uid}/entries/{targetId}`); cross-user connection teardown is deferred to the server-side Cloud Function to avoid `PERMISSION_DENIED` batch rollback.
> - `SketchlyViewerScreen` + `ContactHistoryViewModel` both call `sketchlyRepository.deleteReceivedSketchesFrom(targetUserId)` immediately after block to clean up Room.
> - `CircleScreen` unified for V1: `FeatureFlags.HIDE_SUGGESTED_TAB = true` hides the tab row; `UnifiedCircleScreen` composable renders Sync Contacts card + connected users list on one screen. Old tab composables (`SuggestedTab`, `RequestsTab`) are preserved for V2.
> - Avatar fallback convention: if `avatarUrl` is blank/null, render name-initials circle (`InitialsAvatar` composable) everywhere — `ProfileScreen`, `EditProfileScreen`, `ContactHistoryScreen`, `ContactHistoryHeader`.
> - NavGraph self-tap guard: tapping a sketch from the current user's own profile no longer navigates to `ContactHistoryScreen` (blank screen); route is silently dropped.

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| DI | Hilt |
| Navigation | Navigation Compose |
| Local DB | Room |
| Backend | Firebase (Auth, Firestore, Functions, Messaging) |
| Widget | Glance for Jetpack Compose |
| Background | WorkManager |
| Serialization | Gson (stroke JSON) |
| Build | Gradle KTS, KSP |

- **Min SDK**: 29 | **Target SDK**: 37
- **Application ID**: `com.jrprofessor.sketchly`
- **Package Root**: `com.jrprofessor.sketchly`

---

## 📁 Project Structure

```
Sketchly/
├── app/src/main/java/com/jrprofessor/sketchly/
│   ├── MainActivity.kt
│   ├── SketchlyApplication.kt
│   │
│   ├── data/
│   │   ├── local/          # Room DB (DAO, Entities, Database)
│   │   │   ├── ContactDao.kt
│   │   │   ├── ContactEntity.kt
│   │   │   ├── SketchlyDao.kt
│   │   │   ├── SketchlyDatabase.kt
│   │   │   └── SketchlyEntity.kt
│   │   ├── model/          # Domain/Firestore models
│   │   │   ├── SketchlyModels.kt
│   │   │   └── User.kt
│   │   ├── repository/     # Data layer (single source of truth)
│   │   │   ├── AuthRepository.kt
│   │   │   ├── BlockRepository.kt       # Block / unblock user; writes to blocks/{uid}/entries/{targetId}
│   │   │   ├── ContactRepository.kt
│   │   │   └── SketchlyRepository.kt
│   │   ├── service/
│   │   │   └── SketchlyMessagingService.kt   # FCM service
│   │   ├── util/
│   │   │   └── NetworkMonitor.kt
│   │   └── worker/         # WorkManager workers
│   │       ├── SendSketchlyWorker.kt
│   │       └── WidgetUpdateWorker.kt
│   │
│   │   ├── ui/
│   │   │   ├── components/     # Reusable Compose components
│   │   │   │   ├── PenToolbar.kt
│   │   │   │   ├── RecipientPickerSheet.kt
│   │   │   │   ├── SkeletonLoader.kt
│   │   │   │   ├── SketchlyThumbnail.kt
│   │   │   │   └── SketchlyTopBar.kt
│   │   │   ├── navigation/
│   │   │   │   ├── Screen.kt              # Route definitions
│   │   │   │   ├── SketchlyBottomBar.kt
│   │   │   │   └── SketchlyNavGraph.kt    # Nav graph setup
│   │   │   ├── screens/
│   │   │   │   ├── auth/
│   │   │   │   │   ├── AuthScreen.kt           # Phone number input + OTP
│   │   │   │   │   ├── AuthViewModel.kt        # Shared ViewModel for all auth screens
│   │   │   │   │   ├── OtpVerificationScreen.kt
│   │   │   │   │   └── ProfileSetupScreen.kt   # Full Name + Username after OTP
│   │   │   │   ├── contacts/
│   │   │   │   │   └── ContactPermissionScreen.kt  # READ_CONTACTS gate + hash sync
│   │   │   │   ├── dashboard/
│   │   │   │   │   ├── DashboardScreen.kt      # Main home — greeting, RECENT ACTIVITY feed, Draw FAB
│   │   │   │   │   └── DashboardViewModel.kt   # exposes recentSketches, currentUserId
│   │   │   │   ├── draw/
│   │   │   │   │   ├── DrawScreen.kt           # Full-screen canvas (no bottom bar)
│   │   │   │   │   └── DrawViewModel.kt
│   │   │   │   ├── circle/
│   │   │   │   │   ├── CircleScreen.kt         # V1: UnifiedCircleScreen (Sync card + connections list, no tab row)
│   │   │   │   │   └── CircleViewModel.kt      # default tab = CONNECTED when HIDE_SUGGESTED_TAB=true
│   │   │   │   ├── send/
│   │   │   │   │   └── SendToScreen.kt         # Full-screen recipient picker + SentConfirmationDialog
│   │   │   │   ├── widget/
│   │   │   │   │   └── AddWidgetScreen.kt      # Home screen widget promo
│   │   │   │   ├── profile/
│   │   │   │   │   ├── ProfileScreen.kt        # Initials avatar, stats, Open Settings button
│   │   │   │   │   ├── EditProfileScreen.kt    # Edit display name + avatar upload
│   │   │   │   │   └── EditProfileViewModel.kt
│   │   │   │   ├── viewer/
│   │   │   │   │   ├── SketchlyViewerScreen.kt          # Redesigned: name/time header, canvas card, 5-emoji bar
│   │   │   │   │   ├── ContactHistoryScreen.kt          # Hero header (initials avatar), 2-col week-grouped scribble grid
│   │   │   │   │   └── ContactHistoryViewModel.kt       # Loads sketches for a contact, groups by week; injects SketchlyRepository for Room cleanup on block
│   │   │   │   ├── history/
│   │   │   │   │   ├── HistoryScreen.kt        # Full-screen History — date-grouped 2-col grid, filter chips
│   │   │   │   │   └── HistoryViewmodel.kt     # Pagination, HistoryFilter, sender list
│   │   │   │   ├── settings/
│   │   │   │   │   ├── SettingsScreen.kt       # WIDGET / NOTIFICATIONS / ACCOUNT sections
│   │   │   │   │   └── SettingsViewModel.kt    # Shared VM: currentUser, firestoreUser, contact-sync, signOut
│   │   │   │   ├── block/
│   │   │   │   │   ├── BlockedUsersScreen.kt   # List of blocked users + unblock
│   │   │   │   │   └── BlockViewModel.kt       # Wraps BlockRepository; exposes blockedUsers + blockUser/unblockUser
│   │   │   │   └── started/
│   │   │   │       └── StartedScreen.kt        # Onboarding / splash
│   │   │   └── theme/
│   │   │       ├── Color.kt
│   │   │       ├── Shape.kt
│   │   │       ├── Spacing.kt
│   │   │       ├── Theme.kt
│   │   │       └── Type.kt
│   │
│   ├── di/
│   │   └── AppModule.kt    # Hilt DI bindings
│   │
│   ├── utils/
│   │   ├── Utils.kt
│   │   └── FeatureFlags.kt   # Compile-time feature flags: ENABLE_CONNECTION_REQUESTS, HIDE_SUGGESTED_TAB
│   │
│   └── widget/             # Home screen App Widget (Glance)
│       ├── SketchlyWidget.kt
│       ├── SketchlyWidgetReceiver.kt
│       └── SketchlyWidgetState.kt
│
├── functions/              # Firebase Cloud Functions (Node.js)
├── firestore.rules         # Firestore Security Rules
├── Architecture.md         # Full system architecture doc
├── PRD.md                  # Product Requirements
├── SRS.md                  # Software Requirements Spec
├── DESIGN.md               # Design system doc
├── UIUX.md                 # UI/UX guidelines
└── Development-Plan.md     # Milestone-based dev plan
```

---

## 🔐 Authentication Flow (V1)

- **Primary**: Firebase Phone Auth (OTP via SMS). Email/password is V2.
- **V1 Flow (new user)**:
  `StartedScreen` → `AuthScreen` (phone entry) → `OtpVerificationScreen` → **`ProfileSetupScreen`** (Full Name + Username) → **`ContactPermissionScreen`** (hash sync) → **`DashboardScreen`**
- **V1 Flow (returning user)**:
  `StartedScreen` → `AuthScreen` → `OtpVerificationScreen` → **`DashboardScreen`** (skips Profile + Contact screens)
- **Post-Draw Send Flow**:
  `DrawScreen` →(Next)→ `SendToScreen` →(send)→ `[SentConfirmationDialog]` →(Continue)→ `AddWidgetScreen` →(Add/Skip)→ **`DashboardScreen`**
- **New vs returning detection**: `AuthRepository.syncUserProfile()` checks if Firestore doc has non-blank `username`. Returns `isNewUser: Boolean`.
- **ViewModel**: `AuthViewModel` in `ui/screens/auth/` — shared by `AuthScreen`, `OtpVerificationScreen`, and `ProfileSetupScreen` via `hiltViewModel()`
- **Repository**: `AuthRepository` in `data/repository/`
- **Debug OTP**: `BuildConfig.DEBUG_OTP = "123456"` (only in debug builds)
- All Firestore access gated by Firebase Security Rules on `request.auth.uid`

### Bottom Bar Visibility
The system bottom nav bar is **allowlist-based** in `MainActivity`. The `DashboardScreen` has its **own internal bottom bar** (Inbox | Draw FAB | History) so it is included in the allowlist but renders no system bar items on top of it.

| Route | Screen | System Bottom Bar |
|---|---|---|
| `dashboard` | DashboardScreen | In allowlist (has own bar) |
| `draw` | DrawScreen | Hidden |
| `send_to` | SendToScreen | Hidden |
| `add_widget` | AddWidgetScreen | Hidden |
| `profile` | ProfileScreen | Hidden |
| `edit_profile` | EditProfileScreen | Hidden |
| `viewer` | SketchlyViewerScreen | Hidden |
| `contact_history/{contactId}` | ContactHistoryScreen | Hidden |
| `history` | HistoryScreen | Visible (History tab selected) |
| `circle` | CircleScreen | Visible |
| `settings` | SettingsScreen | Visible |

### DashboardScreen internals
- **No tabs** — Dashboard always shows the Inbox (RECENT ACTIVITY) feed. The `DashTab` enum and `activeTab` state have been removed.
- **Top bar icons**: Circle icon (→ `CircleScreen`) + initials avatar (→ `ProfileScreen`)
- **Draw FAB**: gold pencil button floating above centre of bar → `DrawScreen`
- **Data**: driven by `DashboardViewModel` (recentSketches, currentUserId) + `SettingsViewModel` (user display name / initials)
- **Greeting**: time-based — Good morning / afternoon / evening / night + first name
- **onSketchTap callback** passes the full `Sketch` object — NavGraph decides whether to route to `SketchlyViewerScreen` (unread) or `ContactHistoryScreen` (already read).

### Smart sketch-tap routing (NavGraph)
- **Unread sketch** → `Screen.Viewer.createRoute(sketch.id)` — shows replay + marks as read.
- **Already-read sketch** → `Screen.ContactHistory.createRoute(sketch.senderId)` — shows full history with that contact.

### HistoryScreen internals
- **Route**: `Screen.History` (`"history"`) — navigated to by tapping the History tab in `SketchlyBottomBar`
- **Top bar**: profile avatar ✦ (left) → `ProfileScreen` · *"History"* italic title (center) · settings gear (right) → `SettingsScreen`
- **Filter chips**: All / Sent / Received + one chip per unique sender (from Room cache)
- **Body**: date-grouped 2-column grid with sticky section headers (Today / Yesterday / This Week / Earlier)
- **Empty state**: mirrors DashboardScreen empty state — clock emoji, "No history yet", Draw a Scribble button
- **ViewModel**: reuses `ArchiveViewModel` (pagination, `HistoryFilter`, sender list)
- **Bottom bar**: shown with **History tab selected** (`isHistorySelected = true`)

### SketchlyBottomBar API (updated)
- Signature: `isHistorySelected: Boolean`, `onInboxTap: () -> Unit`, `onHistoryTap: () -> Unit`, `onDrawTap: () -> Unit`
- `isHistorySelected` is derived in `MainActivity` from `currentRoute == Screen.History.route` — no separate state variable needed.

### ContactHistoryScreen
- **Route**: `Screen.ContactHistory` (`"contact_history/{contactId}"`) — navigated to from Dashboard when tapping an **already-read** sketch.
- **Self-tap guard**: if `contactId == currentUserId`, the NavGraph silently skips navigation (no blank screen).
- **Header (`ContactHeroHeader`)**: large initials-avatar circle (fallback: name initials — NOT the last sketch thumbnail), contact display name, scribble count + since-label.
- **Body**: week-grouped 2-column grid (THIS WEEK / LAST WEEK / MMM YYYY), each card shows sketch thumbnail + day-of-week chip.
- **ViewModel**: `ContactHistoryViewModel` — calls `SketchlyRepository.getSketchesWithContact(contactId)`. Also injects `SketchlyRepository` to call `deleteReceivedSketchesFrom(contactId)` when the user blocks from this screen.

### SketchlyViewerScreen (redesigned)
- **Top bar**: back arrow (left) · contact name centred · "Sent at HH:MM AM" subtitle · ⋮ more (right).
- **Canvas**: large `PaperIvory` card with rounded corners, full sketch replay.
- **Emoji bar**: 5 circular buttons (❤️ 😂 ✨ 😮 😢) with warm-sand background and gold ring on selected.
- **Recipient name resolution**: when `isSender == true`, `ViewerViewModel.loadSketch()` does a one-shot `.first()` collect of `ContactRepository.getConnectedContacts()` to build a `uid → displayName` map stored in `ViewerUiState.recipientDisplayNames`. The top bar resolves the first recipient UID via this map; falls back to the raw UID if not found. Do NOT use `recipientIds.firstOrNull()` directly for display.

---

## 📡 Firebase Backend

| Service | Usage |
|---|---|
| **Firebase Auth** | Phone OTP authentication |
| **Firestore** | User docs, Scribble metadata + stroke JSON, reactions, contacts |
| **Cloud Functions** | `onScribbleCreate` (fan-out FCM), `onReactionCreate`, `onFollowRequestCreate`, `onFollowRequestAccept`, `purgeInactiveData`, `sendEmailOtp`, `verifyEmailOtp`, `matchContactsByHash` |
| **Cloud Messaging (FCM)** | Push to trigger widget refresh + notifications |
| **Cloud Storage** | Optional pre-rendered PNG thumbnails |
| **Crashlytics** | Crash/ANR tracking |
| **Performance** | Cold start + network latency monitoring |

---

## 🔄 Key Data Flows

### Send Path
1. User draws → strokes in ViewModel (in-memory)
2. Tap Send → write to **Room** (optimistic UI)
3. **Repository** async writes to **Firestore** (`scribbles/{id}`)
4. **Cloud Function** `onScribbleCreate` sends **FCM** to recipients
5. Recipient: FCM → **WorkManager** fetches from Firestore → renders bitmap → updates Room → calls `GlanceAppWidget.update()`

### Reaction Path
1. Tap emoji on viewer → write `reactions/{scribbleId}/{userId}` to Firestore
2. If sender foregrounded: Firestore snapshot listener updates UI
3. If backgrounded: Cloud Function sends FCM notification

---

## 🧩 Architecture Patterns

- **MVVM** with Repository pattern
- **Unidirectional Data Flow**: UI → ViewModel → Repository → Data Source
- **Optimistic writes**: Room first, then Firestore async
- **Hilt** for dependency injection throughout
- **Coroutines + Flow** for async operations
- **Glance** for Home Screen widget (not RemoteViews)
- **WorkManager** for reliable background sends and widget refreshes

---

## 🧑‍💼 User Model (`data/model/User.kt`)

| Field | Type | Notes |
|---|---|---|
| `id` | String | Firebase UID |
| `displayName` | String | Full name — set in ProfileSetupScreen |
| `username` | String | Unique, lowercase, 3–20 chars, alphanumeric + _ |
| `phoneNumberHash` | String | SHA-256 of E.164 phone number — used for contact sync |
| `avatarUrl` | String | Optional profile picture |
| `isSearchable` | Boolean | If false, hidden from username search |
| `authProvider` | String | "phone" (V1) \| "email" (V2) |
| `fcmToken` | String | FCM registration token for push delivery |
| `widgetPreviewEnabled` | Boolean | Widget doodle preview toggle |
| `notificationsEnabled` | Boolean | Push notification toggle |
| `email` | String | **V2 — hidden in V1 UI, kept in model** |
| `isEmailVerified` | Boolean | **V2 — hidden in V1 UI, kept in model** |

---

## 🚫 Common Gotchas / Conventions

- Stroke data is stored as **JSON inline** in Firestore doc (not Cloud Storage) — keep under Firestore's 1MB limit
- Widget updates are **push-driven** (FCM), NOT polling
- No custom WebSocket server — Firestore listeners handle real-time needs
- `DEBUG_OTP` field exists in `BuildConfig` — do NOT use in release builds
- All Firestore Security Rules are version-controlled in `firestore.rules`, never edited manually in console
- **Email auth UI is HIDDEN in V1** — `EmailView`, `toggleAuthMode`, `submitEmailAuth`, `sendEmailOtp`, `verifyEmailOtp` are all preserved in code for V2. Do NOT delete them.
- **Raw phone numbers never leave the device** — `AuthRepository.sha256()` hashes before any network call. `User.phoneNumberHash` stores the hash, not the raw number.
- **ProfileSetupScreen shares AuthViewModel** — it reads `phoneNumber`/`countryCode` from state to compute the phone hash on save.
- **Username is immutable in V1** — once saved to Firestore, it cannot be changed (V2 feature).
- **`verifyPhoneOtp()` in AuthViewModel has two callbacks** — `onNewUser` and `onReturningUser`. NavGraph uses these for smart routing. Do not collapse them into a single callback.
- **`syncUserProfile()` returns `Boolean`** — `true` = new user needs ProfileSetup, `false` = returning user.
- **`DashboardScreen` is the home screen** — `MainActivity.startDestination` is `Screen.Dashboard.route` for authenticated users. Do NOT navigate to `Screen.Draw` or `Screen.Inbox` as the root after auth.
- **`DashboardScreen` has NO internal tabs** — `DashTab` enum is gone. The Dashboard only shows the Inbox feed. History lives on `Screen.History` / `HistoryScreen`.
- **`SketchlyBottomBar` is shown on both `dashboard` and `history` routes** — do NOT add it to Draw, SendTo, Profile, Viewer, or any other route.
- **`SketchlyBottomBar` tab selection is route-based** — `isHistorySelected = currentRoute == Screen.History.route`. There is no `activeTab` state variable in `MainActivity`.
- **`SettingsViewModel` is shared** — both `SettingsScreen` and `ProfileScreen` (and `DashboardScreen` for greeting/initials) inject it via `hiltViewModel()`. File: `ui/screens/settings/SettingsViewModel.kt`. Full public API:
  - `currentUser: StateFlow<FirebaseUser?>` — Firebase auth state (uid, phone; displayName is always blank for phone auth).
  - `firestoreUser: StateFlow<User?>` — Firestore `users/{uid}` doc; **the real source of truth** for `displayName`, `username`, `avatarUrl`. Reloaded on every auth state change.
  - `isContactSyncEnabled: StateFlow<Boolean>` — whether the periodic WorkManager contact-sync job is active.
  - `isSyncing: StateFlow<Boolean>` — true while a manual "Sync contacts now" call is in-flight.
  - `syncMessage: StateFlow<String?>` — one-shot Toast message after `syncContactsNow()` completes; consume then call `clearSyncMessage()`.
  - `enableContactSync()` — schedules 24-h periodic `ContactSyncWorker` + fires an immediate one-time sync.
  - `disableContactSync()` — cancels the periodic WorkManager job.
  - `syncContactsNow()` — triggers an immediate sync via `ContactRepository.syncContacts()`; no-op if already syncing.
  - `clearSyncMessage()` — resets `syncMessage` to null after UI consumes the Toast.
  - `signOut(onSignedOut: () -> Unit)` — calls `AuthRepository.signOut()` then invokes the callback for nav.
- **`DrawViewModel` is scoped to the Draw back-stack entry** — `SendToScreen` retrieves it via `hiltViewModel(navController.getBackStackEntry(Screen.Draw.route))` so contacts and send state are unified across both screens.
- **`SentConfirmationDialog` is an overlay, not a nav destination** — it is rendered inside `SendToScreen` as a `Box` overlay (dimmed scrim + card) controlled by `DrawViewModel.showSentDialog`. Do NOT convert it into a separate route.
- **`AddWidgetScreen` uses Android pin API** — calls `AppWidgetManager.requestPinAppWidget()` on Android 8.0+ (API 26+). On older devices or if pinning is not supported, the "Add Widget" button falls back to navigating to `DashboardScreen`.
- **`ContactRepository` V1 constructor requires 4 args** — `firestore`, `fireAuth`, `functions`, `context` (in that order). `contactDao` is **commented out** in V1. `AppModule.provideContactRepository` must NOT pass `contactDao`. When restoring for V2, add `contactDao: ContactDao` as the **first** param and re-enable in `AppModule`.
- **`ContactHashUtil` is the ONLY place raw phone numbers are touched** — `ContactHashUtil.getHashedPhoneNumbers(context)` reads device contacts, normalizes to E.164, SHA-256 hashes, and returns hashes only. Raw numbers never leave this utility. Do NOT read `ContactsContract` anywhere else in the codebase.
- **Contact sync results are stored in Firestore, NOT Room** — `syncContacts()` writes to `users/{uid}/suggestedContacts` via `saveToFirestore()`. `ContactDao` / `ContactEntity` (Room `contacts` table) is for manually-added contacts — **this feature is hidden in V1** (no Add Friend UI). `ContactEntity` data class is still imported in `CircleScreen`/`CircleViewModel` for type safety — only `ContactDao` (the DAO) is disabled.
- **`SketchlyContactEntity` in `ContactModels.kt` is dead code** — it defines a `sketchly_contacts` table that is not registered in `@Database`. It is not used by any code path. Do NOT register it in `SketchlyDatabase` unless a future feature explicitly requires local caching of sync results.
- **`matchContactsByHash` is a Firebase Functions v2 `onCall`** — defined in `functions/index.js` using `onCall({ region: "us-central1" }, ...)` (NOT the v1 `functions.https.onCall`). The Android client calls it via `FirebaseFunctions.getInstance("us-central1").getHttpsCallable("matchContactsByHash")`. Auth context is in `request.auth` (v2), not `context.auth` (v1).
- **Contact sync is rate-limited to 5 per user per 24 h** — enforced server-side in `matchContactsByHash` via `rateLimits/contactSync_{uid}` Firestore doc. Client receives `functions/resource-exhausted` error if limit is exceeded — surface this to the user in `ErrorState`.
- **`ContactSyncViewModel.onPermissionGranted()` uses `getSuggestedContacts().first()`** — this collects the first emission of the Firestore snapshot listener. It is correct only because `saveToFirestore()` has already committed before this call. Do not reorder these operations.
- **`onFollowRequestCreate` fires on `followRequests/{requestId}` onCreate** — sends an FCM notification (channelId: `"social"`) to `toUserId`. Expected doc fields: `fromUserId`, `toUserId`, `fromUserName`, `status: "pending"`, `createdAt`.
- **`onFollowRequestAccept` fires on `followRequests/{requestId}` onWrite** — only acts when `status` flips to `"accepted"`. Creates two symmetric docs in `connections/`: `{fromUserId}_{toUserId}` and `{toUserId}_{fromUserId}`, each with `userAId`, `userBId`, `createdAt`. Then sends FCM (channelId: `"social"`) to `fromUserId`. The `toUserName` field must be present in the follow-request doc for the notification body.
- **`purgeInactiveData` is a scheduled function** — uses `onSchedule` from `firebase-functions/v2/scheduler`. Cron: `"0 3 1 * *"` (1st of month, 03:00 UTC). Deletes: expired `emailOtps` (where `expiresAtMs < now`), stale `rateLimits` windows (where `windowStart < 30 days ago`), and old declined/cancelled `followRequests` (where `createdAt < 90 days ago`). Uses batch deletes of 500 docs to avoid Firestore limits.
- **`sendEmailOtp` and `verifyEmailOtp` are V2 callables** — preserved in `functions/index.js` for the V2 email auth flow. Do NOT delete them. They are NOT called by any V1 Android code path.
- **`social` FCM notification channel** — `SketchlyMessagingService.createNotificationChannels()` now creates three channels: `"reactions"` (HIGH), `"sketches"` (DEFAULT), and `"social"` (DEFAULT). The `"social"` channel is required by `onFollowRequestCreate` and `onFollowRequestAccept` Cloud Functions — without it, follow-request push notifications are silently dropped by the OS. Constant `CHANNEL_SOCIAL = "social"` is defined in the `SketchlyMessagingService` companion object.
- **`POST_NOTIFICATIONS` runtime permission (Android 13+ / API 33+)** — declaring the permission in `AndroidManifest.xml` alone is NOT sufficient on API 33+. `MainActivity.onCreate` uses `registerForActivityResult(ActivityResultContracts.RequestPermission())` to request `Manifest.permission.POST_NOTIFICATIONS` at runtime if not already granted. The result callback is a no-op — the app degrades gracefully if the user denies. Do NOT remove this launcher or skip the `Build.VERSION.SDK_INT >= TIRAMISU` guard.
- **V1 Circle screen is a unified single view** — `FeatureFlags.HIDE_SUGGESTED_TAB = true` causes `FriendsListScreen` to render `UnifiedCircleScreen` instead of `CircleScreenContent`. `UnifiedCircleScreen` shows: search bar → Sync Contacts card → connected users list. The old `SuggestedTab` and `RequestsTab` composables are **preserved** in code but not rendered. Flip flag to `false` to restore V2 tab layout.
- **`CircleUiState.selectedTab` defaults to `CONNECTED`** when `HIDE_SUGGESTED_TAB = true`. Do NOT change this default — the V1 screen depends on it.
- **`ContactRepository.getContacts()` returns `emptyFlow()` in V1** — `CircleViewModel.contacts` is always an empty `StateFlow<List<ContactEntity>>`. The "Add Friend" FAB + `AddFriendSheet` are commented out.
- **`ContactDao` provider is commented out in `AppModule`** — `SketchlyDatabase` + `SketchlyDao` are still active (needed by `SketchlyRepository` for draft). Do NOT comment out `provideSketchlyDatabase()` or `provideSketchlyDao()` — only `provideContactDao()` is V1-hidden.
- **`AuthRepository` does NOT clear Room on sign-out in V1** — `db.clearAllTables()` is commented out. Since no local contact/sketch cache exists in V1, this is safe. Re-enable for V2 when Room cache is active.
- **`firebase-storage` and `coil-compose` are now required dependencies** — added to `app/build.gradle.kts` and `gradle/libs.versions.toml`. `firebase-storage` is used by `EditProfileRepository` (avatar upload). `coil-compose` is used by `EditProfileScreen` (`AsyncImage`).
- **`UserProfile` is defined inside `EditProfileRepository.kt`** — it lives in `com.jrprofessor.sketchly.data.repository` package at the bottom of the file. Do NOT import it from `data.model` — there is no `data.model.UserProfile` class.
- **`SketchlyTopBar` is the uniform top bar for all screens with back navigation** — background is `ToolBarBgColor`, height is 56.dp, back button uses `AppNameColor`, title is centered. It calls `.background(ToolBarBgColor).statusBarsPadding().height(56.dp)` so status bar color matches the bar. Outer columns must NOT apply `statusBarsPadding()` to avoid double padding. `MainActivity` sets `SystemBarStyle.light()` for transparent status bar with dark icons.
- **First scribble title in `DrawScreen`** — checks `DrawViewModel.hasDrawnBefore`. First time displays "Draw your first Scribble", subsequent times display "New Scribble". Mark occurs when sketch is sent or if sent sketches exist in Room.
- **Avatar fallback is always name initials** — wherever `avatarUrl` is blank/null, render the `InitialsAvatar` composable (a colored circle with the first 1–2 initials of `displayName`). This applies to `ProfileScreen`, `EditProfileScreen`, `ContactHistoryScreen` (hero header), and any other avatar site. Do NOT render a generic placeholder icon.
- **`reverseConnections` is a TOP-LEVEL Firestore collection** — path is `reverseConnections/{recipientId}/senders/{senderId}`. Do NOT confuse with the flat `connections/{id}` collection used by the follow-request system (`onFollowRequestAccept`). These two collections are completely separate.
- **`reverseConnections` is Admin SDK write-only** — `firestore.rules` has `allow write: if false` on this path. No client SDK call should ever attempt to write here. Writes come exclusively from `onScribbleCreate` Cloud Function.
- **`ContactGroups` is defined in `DrawViewModel.kt`** — it is a top-level `data class` in that file (not in a separate models file). Do NOT look for it in `data/model/`.
- **`DrawViewModel.contactGroups` uses four combined flows** — `getSuggestedContacts()` + `getConnectedContacts()` + `blockedIds` (from `BlockRepository`). Both `contacts` and `contactGroups` StateFlows filter out any contact whose `userId` is in `blockedIds`. Do NOT regress to a two-stream combine or omit the block filter.
- **`ContactSource.RECEIVED_SCRIBBLE` is the enum value for reverse contacts** — used in `ContactRepository.getReverseConnections()` and in `SendToScreen` preview code. Do NOT remove it.
- **`SendToScreen` now takes a `reverseContacts: List<SketchlyContact>` parameter** — this is passed from `SketchlyNavGraph` via `drawViewModel.contactGroups.scribbledYou`. Do NOT remove this parameter or make it optional with a default — callers must always supply it.
- **`ReverseContactRow` shows a gold `ButtonGold`-colored "↩ Reply" pill** — this is a `Surface` with `color = ButtonGold.copy(alpha = 0.15f)` and text `color = ButtonGold`. The badge communicates to the user that this contact auto-appeared because they sent a Scribble. Do NOT remove the badge without a design decision.
- **Self-send guard in `onScribbleCreate`** — `.filter((recipientId) => recipientId !== senderId)` is applied before the reverse-connection upsert loop. If you add new logic inside that loop, make sure it stays inside the filtered list.
- **Reverse-connection upsert is idempotent** — the function checks `existing.exists` before writing. If the doc already exists it only calls `update({ lastReceivedAt })`, never touching `firstReceivedAt`. Do NOT replace this with `set(..., { merge: true })` for both cases — that would silently overwrite `firstReceivedAt`.
- **Sender profile fetch in `onScribbleCreate` is non-fatal** — if the `users/{senderId}` doc fetch fails, the function falls back to `{ displayName: "Sketchly User", username: "", avatarUrl: null }` and still upserts the reverse-connection entries. Never make the profile fetch block the upsert.
- **Block system — client-side scope** — `BlockRepository.blockUser(targetId)` writes ONE doc to `blocks/{uid}/entries/{targetId}`. It does NOT write to the reverse path (`blocks/{targetId}/entries/{uid}`) — that would cause `PERMISSION_DENIED` (each user owns only their own `blocks/{uid}` sub-tree). Reverse connection teardown (removing the target from the sender's `connections/`) is handled by the server-side Admin SDK Cloud Function, not the client.
- **Block filter is applied client-side immediately** — `ContactRepository.getConnectedContacts()` calls `getBlockedIds(uid)` on every Firestore snapshot and filters out blocked users before emitting. `DrawViewModel` further combines with a `blockedIds: StateFlow<Set<String>>` from `BlockRepository`. Blocked users disappear from the contact list / `SendToScreen` **instantly** on block — no need to wait for the Cloud Function to remove the connection document.
- **Block + Room cleanup** — after `blockUser()` succeeds, both `SketchlyViewerScreen` and `ContactHistoryViewModel` call `sketchlyRepository.deleteReceivedSketchesFrom(targetUserId)` to immediately purge the blocked user's sketches from the local Room cache, keeping the dashboard feed clean.
- **`BlockViewModel` is scoped per screen** — inject with `hiltViewModel()` in `ProfileScreen` and `BlockedUsersScreen`. Do NOT share it as a singleton.
- **`FeatureFlags` lives in `utils/FeatureFlags.kt`** — two flags today: `ENABLE_CONNECTION_REQUESTS = false` (follow-request UI hidden), `HIDE_SUGGESTED_TAB = true` (unified CircleScreen for V1). Add future V2 flags here. NEVER delete gated code — gate it, don't gut it.

---

## 📄 Reference Docs (read when needed)

- [`Architecture.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/Architecture.md) — full system architecture
- [`PRD.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/PRD.md) — product requirements
- [`SRS.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/SRS.md) — software requirements
- [`DESIGN.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/DESIGN.md) — design system
- [`UIUX.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/UIUX.md) — UI/UX guidelines
- [`Development-Plan.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/Development-Plan.md) — milestone plan
