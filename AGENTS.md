# Sketchly — AI Agent Context

> **A messaging app where every message is a hand-drawn note/doodle, delivered instantly and surfaced on the Home Screen via widgets.**

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
│   ├── di/
│   │   └── AppModule.kt    # Hilt DI bindings
│   │
│   ├── ui/
│   │   ├── components/     # Reusable Compose components
│   │   │   ├── PenToolbar.kt
│   │   │   ├── RecipientPickerSheet.kt
│   │   │   ├── SkeletonLoader.kt
│   │   │   └── SketchlyThumbnail.kt
│   │   ├── navigation/
│   │   │   ├── Screen.kt              # Route definitions
│   │   │   ├── SketchlyBottomBar.kt
│   │   │   └── SketchlyNavGraph.kt    # Nav graph setup
│   │   ├── screens/
│   │   │   ├── contacts/                   # Contact permission gate
│   │   │   └── ContactPermissionScreen.kt  # Shown once after signup
│   │   ├── auth/
│   │   │   │   ├── AuthScreen.kt           # Phone number input
│   │   │   │   ├── AuthViewModel.kt
│   │   │   │   └── OtpVerificationScreen.kt
│   │   │   ├── draw/
│   │   │   │   ├── DrawScreen.kt           # Canvas drawing
│   │   │   │   └── DrawViewModel.kt
│   │   │   ├── inbox/
│   │   │   │   ├── InboxScreen.kt
│   │   │   │   └── InboxViewModel.kt
│   │   │   ├── circle/                     # Contacts/friends
│   │   │   │   ├── CircleScreen.kt
│   │   │   │   └── CircleViewModel.kt
│   │   │   ├── viewer/
│   │   │   │   └── SketchlyViewerScreen.kt # View received scribble
│   │   │   ├── archive/
│   │   │   │   └── ArchiveScreen.kt
│   │   │   ├── settings/
│   │   │   │   └── SettingsScreen.kt
│   │   │   ├── preview/
│   │   │   │   └── ScreenPreviewScreen.kt
│   │   │   └── started/
│   │   │       └── StartedScreen.kt        # Onboarding/splash
│   │   └── theme/
│   │       ├── Color.kt
│   │       ├── Shape.kt
│   │       ├── Spacing.kt
│   │       ├── Theme.kt
│   │       └── Type.kt
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

## 🔐 Authentication Flow

- **Primary**: Firebase Phone Auth (OTP via SMS)
- **Flow**: `StartedScreen` → `AuthScreen` (phone/email input) → `OtpVerificationScreen` → **`ContactPermissionScreen`** → `DrawScreen`
- **ViewModel**: `AuthViewModel` in `ui/screens/auth/`
- **Repository**: `AuthRepository` in `data/repository/`
- **Debug OTP**: `BuildConfig.DEBUG_OTP = "123456"` (only in debug builds)
- All Firestore access gated by Firebase Security Rules on `request.auth.uid`

---

## 📡 Firebase Backend

| Service | Usage |
|---|---|
| **Firebase Auth** | Phone OTP authentication |
| **Firestore** | User docs, Scribble metadata + stroke JSON, reactions, contacts |
| **Cloud Functions** | `onScribbleCreate` (fan-out FCM), `onReactionCreate`, `purgeInactiveData`, `validateScribblePayload` |
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

## 🚫 Common Gotchas / Conventions

- Stroke data is stored as **JSON inline** in Firestore doc (not Cloud Storage) — keep under Firestore's 1MB limit
- Widget updates are **push-driven** (FCM), NOT polling
- No custom WebSocket server — Firestore listeners handle real-time needs
- `DEBUG_OTP` field exists in `BuildConfig` — do NOT use in release builds
- All Firestore Security Rules are version-controlled in `firestore.rules`, never edited manually in console

---

## 📄 Reference Docs (read when needed)

- [`Architecture.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/Architecture.md) — full system architecture
- [`PRD.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/PRD.md) — product requirements
- [`SRS.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/SRS.md) — software requirements
- [`DESIGN.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/DESIGN.md) — design system
- [`UIUX.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/UIUX.md) — UI/UX guidelines
- [`Development-Plan.md`](file:///Users/mayanksharma/AndroidStudioProjects/Sketchly/Development-Plan.md) — milestone plan
