# Sketchly — AI Agent Context

> **A messaging app where every message is a hand-drawn note/doodle, delivered instantly and surfaced on the Home Screen via widgets.**

> **Last Updated:** **Firestore connections query composite index error fixed.** Root cause: `ContactRepository.getConnectedContacts()` ran `firestore.collection("connections").whereEqualTo("userAId", uid).orderBy("displayName")` which requires a composite Firestore index. Fixed by querying `connectionsRef(uid)` without server-side `orderBy` and sorting client-side in Kotlin (`sortedBy { it.displayName.lowercase() }`), and adding the composite index definition to `firestore.indexes.json`. Build verified (`assembleDebug` SUCCESS).

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
│   │   │   │   └── SketchlyThumbnail.kt
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
│   │   │   │   │   └── DashboardScreen.kt      # Main home — greeting, RECENT ACTIVITY inbox feed, Draw FAB (no history tab)
│   │   │   │   ├── draw/
│   │   │   │   │   ├── DrawScreen.kt           # Full-screen canvas (no bottom bar)
│   │   │   │   │   └── DrawViewModel.kt
│   │   │   │   ├── inbox/
│   │   │   │   │   ├── InboxScreen.kt          # Standalone inbox (accessible via Circle/Settings)
│   │   │   │   │   └── InboxViewModel.kt
│   │   │   │   ├── circle/
│   │   │   │   │   ├── CircleScreen.kt         # Redesigned — search + colorful avatars + Add Friend sheet
│   │   │   │   │   └── CircleViewModel.kt
│   │   │   │   ├── send/
│   │   │   │   │   └── SendToScreen.kt         # Full-screen recipient picker + SentConfirmationDialog
│   │   │   │   ├── widget/
│   │   │   │   │   └── AddWidgetScreen.kt      # Home screen widget promo
│   │   │   │   ├── profile/
│   │   │   │   │   └── ProfileScreen.kt        # Initials avatar, stats, Open Settings button
│   │   │   │   ├── viewer/
│   │   │   │   │   └── SketchlyViewerScreen.kt
│   │   │   │   ├── history/
│   │   │   │   │   └── HistoryScreen.kt        # [NEW] Full-screen History — date-grouped 2-col grid, filter chips, reuses ArchiveViewModel
│   │   │   │   ├── archive/
│   │   │   │   │   └── ArchiveScreen.kt        # Legacy archive (kept for deep-link compat; ArchiveViewModel shared with HistoryScreen)
│   │   │   │   ├── settings/
│   │   │   │   │   └── SettingsScreen.kt       # Redesigned — WIDGET / NOTIFICATIONS / ACCOUNT sections
│   │   │   │   ├── preview/
│   │   │   │   │   └── ScreenPreviewScreen.kt
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
│   │   └── Utils.kt
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
| `viewer` | SketchlyViewerScreen | Hidden |
| `history` | HistoryScreen | Visible (History tab selected) |
| `inbox` | InboxScreen | Visible |
| `circle` | CircleScreen | Visible |
| `settings` | SettingsScreen | Visible |
| `archive` | ArchiveScreen | Visible |
| `screen_preview` | ScreenPreviewScreen | Visible |

### DashboardScreen internals
- **No tabs** — Dashboard always shows the Inbox (RECENT ACTIVITY) feed. The `DashTab` enum and `activeTab` state have been removed.
- **Top bar icons**: Circle icon (→ `CircleScreen`) + initials avatar (→ `ProfileScreen`)
- **Draw FAB**: gold pencil button floating above centre of bar → `DrawScreen`
- **Data**: driven by `InboxViewModel` (sketches, unread count, online status) + `SettingsViewModel` (user display name / initials)
- **Greeting**: time-based — Good morning / afternoon / evening / night + first name

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
- **`SettingsViewModel` is shared** — both `SettingsScreen` and `ProfileScreen` inject it via `hiltViewModel()`. It exposes `currentUser: StateFlow<FirebaseUser?>` and `signOut()`.
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
- **`social` FCM notification channel** — client-side Android code must create a `NotificationChannel` with id `"social"` at app startup (alongside `"reactions"`) to receive follow-request notifications.
- **V1 Circle screen shows empty contacts list** — `ContactRepository.getContacts()` returns `emptyFlow()` in V1. `CircleViewModel.contacts` is always an empty `StateFlow<List<ContactEntity>>`. The "Add Friend" FAB + `AddFriendSheet` are commented out. Users find connections only via Contact Sync (`ContactPermissionScreen` → `matchContactsByHash`).
- **`ContactDao` provider is commented out in `AppModule`** — `SketchlyDatabase` + `SketchlyDao` are still active (needed by `SketchlyRepository` for draft). Do NOT comment out `provideSketchlyDatabase()` or `provideSketchlyDao()` — only `provideContactDao()` is V1-hidden.
- **`AuthRepository` does NOT clear Room on sign-out in V1** — `db.clearAllTables()` is commented out. Since no local contact/sketch cache exists in V1, this is safe. Re-enable for V2 when Room cache is active.
- **`firebase-storage` and `coil-compose` are now required dependencies** — added to `app/build.gradle.kts` and `gradle/libs.versions.toml`. `firebase-storage` is used by `EditProfileRepository` (avatar upload). `coil-compose` is used by `EditProfileScreen` (`AsyncImage`).
- **`UserProfile` is defined inside `EditProfileRepository.kt`** — it lives in `com.jrprofessor.sketchly.data.repository` package at the bottom of the file. Do NOT import it from `data.model` — there is no `data.model.UserProfile` class.

---

## 📄 Reference Docs (read when needed)

- [`Architecture.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/Architecture.md) — full system architecture
- [`PRD.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/PRD.md) — product requirements
- [`SRS.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/SRS.md) — software requirements
- [`DESIGN.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/DESIGN.md) — design system
- [`UIUX.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/UIUX.md) — UI/UX guidelines
- [`Development-Plan.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/Development-Plan.md) — milestone plan
