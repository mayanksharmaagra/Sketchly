# System Architecture Document
## Scribble

**A messaging app where every message is a hand-drawn note or doodle, deliverable instantly and surfaced on the Home Screen via widgets.**

---

## 1. Architectural Principles

- Keep the backend as thin/managed as possible for MVP — avoid standing up custom infrastructure where a managed service does the job.
- Optimize for **perceived speed** (optimistic local writes) over strict real-time guarantees.
- Treat the Home Screen widget as a first-class render target, not an afterthought bolted onto the mobile app.
- Vector-first content (stroke data), not raster images, to keep payloads small and enable replay/animation later.

---

## 2. Recommended Tech Stack

| Layer | Choice | Why |
|---|---|---|
| **Mobile client (Android)** | Kotlin + Jetpack Compose | Modern, declarative, first-party Google support, pairs directly with Glance for widgets |
| **Widgets** | Glance for Jetpack Compose | Purpose-built Compose API for App Widgets, avoids legacy RemoteViews boilerplate |
| **iOS (future)** | Swift + SwiftUI + WidgetKit | Mirrors Android's Compose+Glance pairing conceptually |
| **Backend** | Firebase (Firestore, Cloud Storage, Cloud Functions, Cloud Messaging, Authentication) | Managed, scales automatically, avoids building custom real-time infra for MVP |
| **Local persistence** | Room (Android) | Standard offline cache, works cleanly with Compose + Coroutines/Flow |
| **Background work** | WorkManager | Reliable retry/backoff for sends and widget refresh jobs |
| **Push** | Firebase Cloud Messaging (FCM) | Wakes device to trigger widget refresh + notification |

This stack avoids over-engineering: no custom WebSocket server, no self-hosted database, no separate media pipeline — all unnecessary for a vector-stroke, small-payload MVP.

---

## 3. High-Level System Components

```
┌─────────────────┐         ┌──────────────────┐
│  Android Client  │◄───────►│  Firebase Backend │
│  (Compose UI)    │         │                    │
│                   │         │  - Auth            │
│  ┌─────────────┐ │         │  - Firestore       │
│  │ Draw Canvas │ │         │  - Cloud Storage   │
│  └─────────────┘ │         │  - Cloud Functions │
│  ┌─────────────┐ │         │  - Cloud Messaging │
│  │ Room (local)│ │         └──────────────────┘
│  └─────────────┘ │                  ▲
│  ┌─────────────┐ │                  │
│  │Glance Widget│◄┼──────────────────┘
│  └─────────────┘ │     (push triggers refresh)
└─────────────────┘
```

---

## 4. Data Flow — Send Path

1. User draws → strokes accumulate in ViewModel state (in-memory).
2. User taps Send → Scribble object is written to **Room** immediately (optimistic UI, instant feedback).
3. Repository asynchronously writes the Scribble document to **Firestore** (`scribbles/{id}`).
4. A **Cloud Function** (Firestore trigger, `onCreate`) fans out: for each `recipientId`, sends an **FCM data message**.
5. Recipient device receives FCM message in background → **WorkManager** job fetches the new Scribble from Firestore, renders it to a bitmap, writes to local Room, and calls `GlanceAppWidget.update()`.
6. Sender's own widget refreshes immediately client-side after step 2 (no round trip needed for the sender's own view).

## 5. Data Flow — Reaction Path

1. Recipient taps an emoji on the Scribble Viewer.
2. Client writes a `reactions/{scribbleId}/{userId}` document to Firestore.
3. If the sender's app is foregrounded, a live Firestore snapshot listener updates their UI in real time.
4. If backgrounded, a Cloud Function sends a lightweight FCM notification ("X reacted ❤️ to your Scribble").

---

## 6. Storage Model

| Store | Contents | Notes |
|---|---|---|
| **Firestore** | User docs, Scribble metadata + stroke JSON, reactions, contact relationships | Stroke data stored inline as JSON within the Scribble doc (well under Firestore's 1MB doc limit given the 2MB stroke cap defined in SRS is enforced pre-compression; compress/cap tightly, or move very large payloads to Storage) |
| **Cloud Storage** | Optional pre-rendered PNG thumbnails (for widget display and faster inbox thumbnails) | Generated client-side or via Cloud Function on write |
| **Room (on-device)** | Cached Scribbles, contacts, user settings | Source of truth for offline/instant UI; synced from Firestore |

---

## 7. APIs / Interfaces

MVP uses **Firestore + Cloud Functions directly** from the client (via the official SDK) rather than a custom REST layer — reduces backend surface area to maintain.

Cloud Functions (server-side logic only, not general CRUD):
- `onScribbleCreate` — fan-out push notifications to recipients
- `onReactionCreate` — notify sender of new reaction
- `purgeInactiveData` — scheduled function for data retention policy (SRS BR-5)
- `validateScribblePayload` — server-side enforcement of size/format limits before accepting writes (via Firestore rules + a validating Function for anything rules can't express)

---

## 8. Authentication

- Firebase Authentication: phone/OTP as primary method, email/password as fallback.
- Client obtains a Firebase ID token on auth; all Firestore access is gated by **Firestore Security Rules** keyed off `request.auth.uid`.
- No custom session/token infrastructure needed — Firebase handles refresh transparently via SDK.

---

## 9. Security

- Firestore Security Rules enforce read/write scoping exactly as defined in the SRS (§7: Authentication & Authorization).
- All traffic over TLS by default (Firebase SDKs).
- Cloud Storage rules restrict thumbnail read access to the sender + recipients of the associated Scribble only.
- Rate limiting enforced via Cloud Functions (App Check + custom counters) to satisfy SRS §10 (max sends/hour).
- No stroke/drawing content is sent to third-party analytics — only anonymized event names (e.g., `scribble_sent`) with no payload content.

---

## 10. Deployment

| Environment | Purpose |
|---|---|
| **dev** | Firebase project for active development, emulator suite for local Firestore/Functions testing |
| **staging** | Mirrors prod config, used for pre-release QA and widget behavior testing across OS versions |
| **prod** | Live Firebase project, restricted access, monitored |

- CI/CD: GitHub Actions — lint/test on PR, deploy Cloud Functions on merge to `main`, Play Store internal testing track for app builds via Fastlane or Gradle Play Publisher.
- Firestore Security Rules and indexes version-controlled and deployed via `firebase deploy` in CI, never edited manually in console for prod.

---

## 11. Monitoring

- **Crashlytics** — crash/ANR tracking for the Android client.
- **Firebase Performance Monitoring** — cold start time, network request latency (validate SRS §11 performance targets).
- **Cloud Functions logs + alerting** — error rate alerts on fan-out/notification functions.
- **Custom analytics events** (privacy-safe): `scribble_sent`, `scribble_received`, `widget_updated`, `reaction_added` — used to track PRD success metrics (D1/D7 retention, sends/week, etc.).

---

## 12. Scalability Considerations (kept practical, not speculative)

- Firestore scales horizontally by default — no manual sharding needed at MVP scale.
- Fan-out to many recipients (bounded to 10 by business rule) keeps Cloud Function execution time predictable and cheap.
- Widget update frequency is push-driven, not polling — avoids unnecessary battery/network drain and backend load at scale.
- If/when payload sizes or user base outgrow Firestore's document model, stroke data can be moved to Cloud Storage with only a metadata pointer left in Firestore — noted as a **future migration path**, not built preemptively.

---

## 13. Explicitly Avoided (Over-Engineering Guardrails)

- No custom WebSocket/real-time server — Firestore listeners cover the real-time needs that exist (reactions, live inbox).
- No microservices split — a handful of Cloud Functions is sufficient at this scope.
- No custom media/CDN pipeline — Cloud Storage + Firebase's built-in CDN behavior is sufficient for small PNG thumbnails.
- No multi-region active-active setup for MVP — single-region Firestore is sufficient until usage data says otherwise.
