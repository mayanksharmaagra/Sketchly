# System Architecture Document
## Sketchly — v1

**A messaging app where every message is a hand-drawn note or doodle, deliverable instantly and surfaced on the Home Screen via widgets.**

> **Changelog:** Auth updated — phone/OTP only for V1. Email/password deferred to V2. UserProfile model updated with username field. Contact system now has two paths: hash-based phone sync + username search. Follow-request collection added to Firestore. Cloud Function for hash matching added. **Auto-connect on receive added** — `onScribbleCreate` now upserts `reverseConnections/{recipientId}/senders/{senderId}` so senders become available as reply targets even when not in a recipient's synced contacts. **Block system added** — `blocks/{uid}/entries/{targetId}` Firestore sub-collection; client writes own block entries only; server Cloud Function handles reverse teardown. **CircleScreen unified for V1** — `FeatureFlags.HIDE_SUGGESTED_TAB = true`; Suggested/Requests tabs hidden; single view shows Sync card + connected users.

---

## 1. Architectural Principles

- Keep the backend as thin/managed as possible — avoid standing up custom infrastructure where a managed service does the job.
- Optimize for perceived speed (optimistic local writes) over strict real-time guarantees.
- Treat the Home Screen widget as a first-class render target, not an afterthought.
- Vector-first content (stroke data), not raster images — small payloads, resolution-independent.
- Raw phone numbers never leave the device — hash client-side, send only hashes.

---

## 2. Recommended Tech Stack

| Layer | Choice | Why |
|---|---|---|
| **Mobile client** | Kotlin + Jetpack Compose | Modern, declarative, first-party Google support |
| **Widgets** | Glance for Jetpack Compose | Purpose-built Compose API for App Widgets |
| **Backend** | Firebase (Firestore, Cloud Storage, Cloud Functions, Cloud Messaging, Authentication) | Managed, scales automatically, no custom real-time infra needed |
| **Local persistence** | Room | Offline cache, works with Coroutines/Flow |
| **Background work** | WorkManager | Reliable retry for sends and widget refresh |
| **Push** | Firebase Cloud Messaging (FCM) | Wakes device for widget refresh + notifications |

---

## 3. High-Level System Components

```
┌──────────────────────────┐       ┌─────────────────────────┐
│     Android Client        │◄─────►│    Firebase Backend      │
│     (Compose UI)          │       │                          │
│                            │       │  - Auth (Phone/OTP)      │
│  ┌──────────────────────┐ │       │  - Firestore             │
│  │ Auth (Phone OTP)     │ │       │  - Cloud Storage         │
│  └──────────────────────┘ │       │  - Cloud Functions       │
│  ┌──────────────────────┐ │       │  - Cloud Messaging (FCM) │
│  │ Draw Canvas           │ │       └─────────────────────────┘
│  └──────────────────────┘ │                   ▲
│  ┌──────────────────────┐ │                   │
│  │ Room (local cache)   │ │      (push triggers widget refresh)
│  └──────────────────────┘ │
│  ┌──────────────────────┐ │
│  │ Glance Widget         │◄┤
│  └──────────────────────┘ │
└──────────────────────────┘
```

---

## 4. Data Flow — Signup Path

```
1. User enters phone number → client sends to Firebase Auth
2. Firebase sends OTP SMS
3. User enters OTP → Firebase verifies → returns UID + ID token
4. New user? → prompt Full Name + Username
5. Check username uniqueness → Firestore users collection (exact match query)
6. Create UserProfile document in Firestore: users/{uid}
7. Store session locally, navigate to Contact Sync screen
```

---

## 5. Data Flow — Contact Sync Path

```
1. User grants READ_CONTACTS permission
2. Client reads device contacts → extracts phone numbers
3. Normalize to E.164 → SHA-256 hash each number CLIENT-SIDE
4. POST hashes[] to Cloud Function: matchContactsByHash
5. Cloud Function queries Firestore users collection for matching phoneNumberHash fields
6. Returns matched UserProfiles (uid, displayName, username, avatarUrl only)
7. Client stores matches in Room (ConnectionEntity, source="contact_sync")
8. Also writes to Firestore: users/{uid}/suggestedConnections/{matchedUid}
9. Matched users shown in "People You May Know" — follow request still required
```

---

## 6. Data Flow — Username Search Path

```
1. User types @username in Search screen
2. Client calls Firestore: users where username == query.lowercase()
3. Returns matching UserProfile if isSearchable = true
4. Show result with Follow / Pending / Connected button
```

---

## 7. Data Flow — Follow Request Path *(V2 — gated by `ENABLE_CONNECTION_REQUESTS = false`)*

```
V1: Follow requests are hidden. Connections form automatically:
  • Via Contact Sync — matchContactsByHash Cloud Function surfaces matched users
  • Via Scribble Send — onScribbleCreate upserts reverseConnections for auto-connect

V2 flow (when ENABLE_CONNECTION_REQUESTS = true):
  1. User taps Follow on a search result or suggested contact
  2. Client writes to Firestore: follow_requests/{requestId}
     { fromUserId, toUserId, status: "pending", createdAt }
  3. Cloud Function onFollowRequestCreate → sends FCM to toUserId
  4. Recipient opens notification → sees request in Follow Requests screen
  5. Recipient taps Accept:
     - follow_requests/{id} status → "accepted"
     - Cloud Function onFollowRequestAccept:
         writes users/{fromUid}/connections/{toUid}
         writes users/{toUid}/connections/{fromUid}
         sends FCM to fromUserId ("X accepted your request")
  6. Both clients update Room (ConnectionEntity) on next sync
```

---

## 8. Data Flow — Send Path

```
1. User draws → strokes accumulate in ViewModel (in-memory)
2. User taps Send → Scribble written to Room immediately (optimistic)
3. Repository writes Scribble to Firestore: scribbles/{id}
4. Cloud Function onScribbleCreate:
   a. Fan-out FCM data message to each recipientId
   b. Fetch sender's public profile (displayName, username, avatarUrl)
   c. For each recipientId ≠ senderId:
        Upsert reverseConnections/{recipientId}/senders/{senderId}
        (idempotent — only updates lastReceivedAt if doc already exists)
5. Recipient device: FCM → WorkManager job → fetch Scribble → render bitmap
   → write to Room → GlanceAppWidget.update()
6. Sender's own widget refreshes immediately client-side (no round trip needed)
```

> **Reverse-connection effect:** after step 4c, the sender automatically appears in the
> recipient's "Sent you a Scribble" section of the recipient picker — no contact sync required.

---

## 9. Firestore Collection Structure

```
users/
  {uid}/
    displayName, username, phoneNumberHash, avatarUrl,
    isSearchable, createdAt, authProvider

    suggestedConnections/
      {suggestedUid}/
        displayName, username, avatarUrl, source: "contact_sync"

follow_requests/
  {requestId}/
    fromUserId, fromDisplayName, fromAvatarUrl,
    toUserId, status, createdAt

connections/
  {connectionId}/
    userAId, userBId, createdAt
    (written by onFollowRequestAccept Cloud Function)

blocks/                              ← NEW — block system
  {uid}/
    entries/
      {targetUserId}/
        targetUserId, displayName, avatarUrl, blockedAt
        (client writes own subtree only; reverse teardown via Cloud Function)

scribbles/
  {scribbleId}/
    senderId, recipientIds[], strokes[], backgroundColor,
    createdAt, deliveryStatus{}, readStatus{}

    reactions/
      {userId}/
        emoji, createdAt

reverseConnections/              ← auto-connect on receive
  {recipientId}/
    senders/
      {senderId}/
        addedVia: "received_scribble"
        senderId, displayName, username, avatarUrl
        firstReceivedAt, lastReceivedAt
        (written by onScribbleCreate Cloud Function — Admin SDK only)
```

---

## 10. Cloud Functions

| Function | Trigger | Purpose |
|---|---|---|
| `matchContactsByHash` | HTTP call (authenticated) | Accepts phone hashes[], returns matched UserProfiles |
| `onScribbleCreate` | Firestore onCreate | Fan-out FCM to all recipientIds **+ upsert `reverseConnections/{recipientId}/senders/{senderId}`** for auto-connect |
| `onFollowRequestCreate` | Firestore onCreate | FCM push to toUserId *(V2 — gated)* |
| `onFollowRequestAccept` | Firestore onUpdate (status→accepted) | Create connection docs for both users + FCM to fromUserId *(V2 — gated)* |
| `onBlockUser` | Firestore onCreate (`blocks/{uid}/entries/{targetId}`) | Tears down reverse connection + removes from `connections/` |
| `onReactionCreate` | Firestore onCreate | FCM push to Scribble sender |
| `purgeInactiveData` | Scheduled (monthly) | Data retention cleanup |

---

## 11. Room Database Schema

```kotlin
// Tables
UserProfileEntity      // cached own profile + settings
ConnectionEntity       // accepted connections
FollowRequestEntity    // pending incoming + outgoing requests
SuggestedContactEntity // contact sync results (pre-follow)
ScribbleEntity         // sent + received Scribbles
StrokeEntity           // strokes per Scribble
ReactionEntity         // reactions per Scribble
```

---

## 12. Security

- Firestore Security Rules enforce all read/write access as defined in SRS §7.
- Phone number hashing done client-side with SHA-256 before any network call.
- `matchContactsByHash` Cloud Function requires a valid Firebase ID token (authenticated users only).
- Hashes used only for matching — not stored permanently in the users collection after initial account creation.
- All traffic over TLS (Firebase SDKs default).
- Rate limiting via Cloud Functions + App Check.

---

## 13. Deployment

| Environment | Purpose |
|---|---|
| **dev** | Local Firebase emulators (Auth, Firestore, Functions) |
| **staging** | Mirrors prod config, used for pre-release QA |
| **prod** | Live Firebase project, restricted access, monitored |

CI/CD: GitHub Actions — lint/test on PR, deploy Cloud Functions on merge to main, Play Store internal track via Fastlane.

---

## 14. Monitoring

- Crashlytics — crash/ANR tracking
- Firebase Performance Monitoring — cold start, network latency
- Cloud Functions logs + error rate alerts
- Custom events (privacy-safe): `signup_completed`, `contact_sync_completed`, `follow_request_sent`, `follow_request_accepted`, `scribble_sent`, `widget_updated`

---

## 15. Explicitly Avoided (Over-Engineering Guardrails)

- No custom WebSocket server — Firestore listeners cover real-time needs
- No microservices — a handful of Cloud Functions is sufficient
- No custom media pipeline — Cloud Storage + Firebase CDN for thumbnails
- No custom auth server — Firebase Auth handles phone/OTP natively
- No full-text search infrastructure — username is exact-match only in V1
