# Software Requirements Specification (SRS)
## Sketchly — v1

**A messaging app where every message is a hand-drawn note or doodle, deliverable instantly and surfaced on the Home Screen via widgets.**

> **Changelog:** Auth simplified — phone + OTP only for V1. Email/password deferred to V2. Contact system split into two paths: phone hash sync + username search. **Follow-request system moved to V2** — gated by `FeatureFlags.ENABLE_CONNECTION_REQUESTS = false`; FR-4 is preserved in code but all UI is hidden. V1 connections form automatically via `onScribbleCreate` reverse-connection upsert. **Block system added** — FR-12; `blocks/{uid}/entries/{targetId}` Firestore sub-collection. **CircleScreen unified for V1** — `HIDE_SUGGESTED_TAB = true`; Suggested/Requests tabs hidden; single view. Username field added to UserProfile. Raw phone numbers must never leave the device. Auto-connect on receive: `reverseConnections` Firestore subcollection, `getReverseConnections()` repository flow, and two-section recipient picker (FR-11).

---

## 1. Purpose

This document defines functional and non-functional requirements for Sketchly V1 in testable form, to guide implementation and QA.

---

## 2. User Roles & Permissions

| Role | Description | Permissions |
|---|---|---|
| **Registered User** | Authenticated account holder (phone + OTP) | Draw/send/receive Scribbles, sync contacts, search users, send/accept follow requests, react, view history, manage widget |
| **Unauthenticated Visitor** | Pre-signup state | Onboarding and signup screens only; no messaging access |
| **System/Admin** (internal) | Backend operators | Content moderation, abuse reports, account suspension |

---

## 3. Functional Requirements

### FR-1: Authentication (Phone Only — V1)

- FR-1.1 Users must sign up using a phone number in E.164 format. No email/password option at signup in V1.
- FR-1.2 After phone entry, the system must send a 6-digit OTP via SMS. OTP expires in 5 minutes.
- FR-1.3 After OTP verification, new users must be prompted to enter Full Name and choose a unique Username before proceeding.
- FR-1.4 Username must be unique across all accounts, 3–20 characters, alphanumeric + underscore only, case-insensitive stored as lowercase.
- FR-1.5 Returning users (phone already registered) skip the name/username screen and go directly to Inbox after OTP verify.
- FR-1.6 Sessions persist via Firebase refresh token; access token expires in ≤ 1 hour, silently refreshed.
- FR-1.7 Users can log out, which clears local session tokens and cached data (Room DB wipe).
- FR-1.8 Email and password are NOT collected at signup. They are addable later from Profile → Settings (V2 scope, UI placeholder only in V1).

### FR-2: Contact Sync (Phone Hash Matching)

- FR-2.1 Users may opt in to sync device contacts. Requires READ_CONTACTS permission with explicit rationale shown before request.
- FR-2.2 Phone numbers must be normalized to E.164 format and SHA-256 hashed client-side before any network call. Raw numbers must never leave the device.
- FR-2.3 The client sends only hashes to the server (Cloud Function). The server matches against registered user hashes and returns matched UserProfiles (uid, displayName, username, avatarUrl only — no hash returned to client).
- FR-2.4 Matched users are stored in Room (ConnectionEntity, source = "contact_sync") and in Firestore under the user's connections subcollection.
- FR-2.5 Contact sync result must NOT auto-connect users. A follow request must still be sent and accepted.
- FR-2.6 Users who declined contact permission can sync later from Settings.
- FR-2.7 The system must not store raw phone numbers or contact names server-side at any point.

### FR-3: Username Search

- FR-3.1 Any registered user can search for other users by exact @username from the search screen.
- FR-3.2 Search must return a result only if the username matches exactly (case-insensitive). No partial/fuzzy match in V1.
- FR-3.3 Search result shows: avatar, display name, username, and a "Follow" or "Pending" or "Connected" action button.
- FR-3.4 Users with isSearchable = false must not appear in search results.

### FR-4: Follow Request System *(V2 — gated by `ENABLE_CONNECTION_REQUESTS = false`)*

> All FR-4 requirements are implemented in code but hidden from the UI for V1. Flip `FeatureFlags.ENABLE_CONNECTION_REQUESTS` to `true` to restore the full flow.

- FR-4.1 Any user can send a follow request to any other user they found via contact sync or search.
- FR-4.2 Recipient receives a push notification when a follow request is received.
- FR-4.3 Recipient can accept or decline a request. Declining removes the request silently (no notification to sender).
- FR-4.4 On accept, both users are added to each other's connections list in Room + Firestore.
- FR-4.5 Sender receives a push notification when their request is accepted.
- FR-4.6 ~~Users must be mutually connected before sending~~ — V1: sending is allowed to any user surfaced via contact sync or reverse-connection (auto-connect on receive).
- FR-4.7 A user can cancel a pending outgoing request before it is accepted.
- FR-4.8 Maximum 1 pending request between any two users at a time.

### FR-5: Drawing

- FR-5.1 The draw canvas must capture freehand stroke input via touch (finger or stylus).
- FR-5.2 Users must be able to select pen color from a predefined palette (minimum 6 colors).
- FR-5.3 Users must be able to select pen thickness (minimum 3 preset sizes).
- FR-5.4 Users must be able to undo the last stroke and clear the entire canvas.
- FR-5.5 Canvas state must persist if the app is backgrounded mid-draw.

### FR-6: Sending

- FR-6.1 The recipient picker shows two sections:
  - **"Your Contacts"** — users found via contact sync who are on Sketchly.
  - **"Sent you a Scribble"** — users who have sent the current user a Scribble and are not already in "Your Contacts" (auto-populated via `reverseConnections`, see FR-11). Each row shows a gold "\u21a9 Reply" badge.
  - Search filters across both sections simultaneously.
- FR-6.2 Users must be able to select 1 or more recipients (max 10) before sending.
- FR-6.3 On send, Scribble is written to Room immediately (optimistic UI) and queued for Firestore upload.
- FR-6.4 If upload fails, retry with exponential backoff (max 5 attempts); show "failed to send — tap to retry" if all fail.
- FR-6.5 Each Scribble sent to multiple recipients is tracked as delivered/read independently per recipient.

### FR-7: Receiving & Inbox

- FR-7.1 Push notification delivered to each recipient within 5 seconds of successful send under normal network conditions.
- FR-7.2 Inbox displays received Scribbles in reverse-chronological order.
- FR-7.3 Unread Scribbles are visually distinguished (bold name + unread dot).
- FR-7.4 Opening a Scribble marks it as read and sends a read receipt to the sender.

### FR-8: Home Screen Widget

- FR-8.1 Widget displays the most recent unread Scribble (thumbnail + sender name).
- FR-8.2 Widget updates within 10 seconds of a new Scribble arriving on-device.
- FR-8.3 Tapping the widget deep-links to the Scribble Viewer for that item.
- FR-8.4 Empty state shown when no unread Scribbles — never blank/broken.
- FR-8.5 Widget doodle preview is toggleable off in Settings (falls back to sender name only).

### FR-9: Reactions

- FR-9.1 Users can react to a received Scribble with one emoji from a fixed set (5 options: ❤️ 😂 😮 👍 🎉).
- FR-9.2 One reaction per user per Scribble; changing replaces the previous one.
- FR-9.3 Sender sees reactions update in real-time (Firestore listener) or via notification if backgrounded.

### FR-10: History

- FR-10.1 Users can view all sent and received Scribbles grouped by date.
- FR-10.2 Users can filter history by contact.
- FR-10.3 History loads with pagination (Paging 3).

### FR-11: Auto-Connect on Receive (Reverse-Connection)

- FR-11.1 When a Scribble is sent, the Cloud Function `onScribbleCreate` must upsert a document in `reverseConnections/{recipientId}/senders/{senderId}` for every recipient who is not the sender (self-send guard).
- FR-11.2 The upsert must be idempotent: if the document already exists, only `lastReceivedAt` is updated; `firstReceivedAt` is never overwritten.
- FR-11.3 The document must copy `displayName`, `username`, and `avatarUrl` from the sender’s `users/{senderId}` profile at write time, so clients can render the row without an extra lookup.
- FR-11.4 Client must listen to `reverseConnections/{uid}/senders` via a Firestore snapshot listener (`ContactRepository.getReverseConnections()`) and surface matching users in the "Sent you a Scribble" section of the recipient picker.
- FR-11.5 Users in "Sent you a Scribble" who are already present in "Your Contacts" (by userId) must be deduplicated — they should appear only under "Your Contacts".
- FR-11.6 The `reverseConnections` subcollection must be writable by Admin SDK only. Client writes must be blocked by Firestore Security Rules (`allow write: if false`).
- FR-11.7 Group Scribbles (multiple recipients) must create reverse-connection entries for **every** eligible recipient, not just the first.

### FR-12: Block User

- FR-12.1 From `SketchlyViewerScreen` (via the ⋮ more menu) or `ContactHistoryScreen`, the current user can block the sender/contact.
- FR-12.2 On block, the client writes `blocks/{uid}/entries/{targetId}` in Firestore with `targetUserId`, `displayName`, `avatarUrl`, and `blockedAt`.
- FR-12.3 The client must NOT write to `blocks/{targetId}/entries/{uid}` — each user owns only their own `blocks/{uid}` subtree. Reverse connection teardown is handled server-side.
- FR-12.4 Immediately after a successful block, `sketchlyRepository.deleteReceivedSketchesFrom(targetUserId)` is called to purge the blocked user’s sketches from the local Room cache.
- FR-12.5 Blocked users appear in a dedicated **Blocked Users** screen (accessible from Settings), where the current user can unblock them.
- FR-12.6 Unblock writes a Firestore delete to `blocks/{uid}/entries/{targetId}`.
- FR-12.7 The block action must not be available on the current user’s own profile (self-block guard).

---

## 4. Business Rules

- BR-1: A Scribble cannot be edited after it is sent.
- BR-2: Sender can delete a Scribble from their own view only; it does not retract from recipients.
- BR-3: Maximum 10 recipients per single send.
- BR-4: Maximum stroke data size per Scribble: 2 MB.
- BR-5: Inactive accounts (no login for 12 months) may have cached remote data purged per retention policy.
- BR-6: Username, once chosen, cannot be changed in V1 (V2 feature).
- BR-7: A user cannot send a Scribble to themselves.
- BR-8: Raw phone numbers must never be stored server-side — hashes only, used for matching then discarded.

---

## 5. Data Requirements

| Entity | Key Fields |
|---|---|
| **UserProfile** | uid, displayName, username (unique, lowercase), phoneNumberHash, avatarUrl, isSearchable, createdAt, authProvider ("phone") |
| **Connection** | userId, connectedUserId, displayName, username, avatarUrl, connectedSince, source ("contact_sync" \| "search" \| "invite") |
| **FollowRequest** | id, fromUserId, fromDisplayName, fromAvatarUrl, toUserId, status ("pending" \| "accepted" \| "declined"), createdAt |
| **Scribble** | id, senderId, recipientIds[], strokes[], backgroundColor, createdAt, deliveryStatus (per recipient), readStatus (per recipient) |
| **Stroke** | points[] {x, y, pressure}, colorHex, widthDp |
| **Reaction** | scribbleId, userId, emoji, createdAt |
| **ReverseConnection** | (Firestore only) addedVia: "received_scribble", senderId, displayName, username, avatarUrl, firstReceivedAt, lastReceivedAt |

---

## 6. Validation Rules

- Phone number must match E.164 format before OTP is issued.
- OTP must be exactly 6 digits, expire after 5 minutes, max 3 attempts before lockout.
- Display name: 1–30 characters, no leading/trailing whitespace.
- Username: 3–20 characters, alphanumeric + underscore only, must be unique (checked against Firestore before account creation).
- At least 1 connected recipient required before "Send" is enabled.
- Canvas must contain at least 1 stroke before "Send" is enabled.
- Reaction emoji must be one of the 5 server-defined options.

---

## 7. Authentication & Authorization

- Firebase Authentication with phone/OTP as the sole V1 auth method.
- All Firestore requests must include a valid Firebase ID token.
- Firestore Security Rules:
  - User can read/write their own UserProfile document only.
  - User can read another UserProfile only if they are connected OR searching (isSearchable = true, read limited to public fields only).
  - User can read a Scribble only if their UID is in recipientIds or matches senderId.
  - User can write a Scribble only if their UID matches senderId.
  - User can write a FollowRequest only if fromUserId matches their own UID.
  - User can update a FollowRequest status only if toUserId matches their own UID.
  - User can write a Reaction only if the reaction's userId matches their own UID.
  - User can **read** `reverseConnections/{userId}/senders/{senderId}` only if their UID matches `userId`.
  - User can **never write** to `reverseConnections` — this collection is Admin SDK only (`allow write: if false`).

---

## 8. Error Handling

- OTP expired/invalid → clear input, show inline error "Code expired — tap to resend", offer resend after 30-second cooldown.
- Username already taken → inline error shown immediately on blur, before form submit.
- Contact sync permission denied → skip sync silently, show non-blocking banner "Add contacts later in Settings."
- No search result found → empty state "No user found with that username."
- Follow request already sent → button shows "Pending" and is non-tappable.
- Network failure during send → optimistic copy retained, retry queued, error toast after final failure.
- Auth token expiry → silent refresh; if refresh fails, force re-login with "Session expired" message.

---

## 9. Edge Cases

- User searches for themselves → "That's you!" state shown, no follow button.
- User tries to send Scribble to a disconnected user (connection deleted after send initiated) → graceful error, Scribble not sent to that recipient.
- OTP resend requested before cooldown → button disabled with countdown timer shown.
- Device offline during contact sync → sync deferred and retried on next foreground; no error shown to user.
- Two users send each other a follow request simultaneously → system accepts both, establishes connection, no duplicate.
- Widget on Home Screen, app force-stopped → widget shows last-cached state, does not crash or blank.
- Sender sends a Scribble to themselves (if allowed via group send) → `onScribbleCreate` self-send guard prevents a `reverseConnections` entry for that sender.
- Sender appears in both "Your Contacts" and "Sent you a Scribble" after a sync → deduplication logic in `DrawViewModel` keeps them in "Your Contacts" only.

---

## 10. Security Requirements

- All network traffic over TLS.
- Phone number hashed with SHA-256 client-side; hash sent to server; raw number never transmitted.
- Hashes used only for matching during contact sync; not stored permanently server-side after match completes.
- Stroke data and metadata encrypted at rest (Firebase default encryption).
- No third-party analytics SDK receives raw stroke content or phone numbers.
- Rate limiting: max 60 Scribbles/hour/user, max 10 follow requests/hour/user.
- OTP lockout after 3 failed attempts; 15-minute cooldown before retry.

---

## 11. Performance Requirements

- Draw canvas input latency: < 16ms per frame (60fps).
- Send action (local optimistic commit): < 200ms perceived latency.
- Widget update after push delivery: < 10 seconds.
- Contact sync (hash + match + store): < 5 seconds for up to 500 contacts on a 4G connection.
- Username search result: < 1 second.
- Inbox initial load: < 1 second for first 20 items on a typical 4G connection.
- App cold start: < 2 seconds on a mid-tier device.

---

## 12. Acceptance Criteria

- [ ] User can sign up with phone + OTP and choose Full Name + Username in under 60 seconds
- [ ] Returning phone number skips name/username screen and goes directly to Inbox
- [ ] Contact sync hashes numbers client-side — confirmed by network inspection showing no raw numbers transmitted
- [ ] Contact sync finds existing Sketchly users and stores them locally without auto-connecting
- [ ] Username search returns correct user on exact match; returns empty state on no match
- [ ] Follow request send → recipient notification → accept → both users connected, confirmed in Room + Firestore
- [ ] Recipient picker shows "Your Contacts" and "Sent you a Scribble" sections separately
- [ ] After receiving a Scribble, the sender appears in recipient's "Sent you a Scribble" section without contact sync; verified via Firestore emulator and on-device test
- [ ] Group Scribble (2+ recipients) creates a `reverseConnections` entry for every eligible recipient
- [ ] Self-send guard: `onScribbleCreate` never creates a `reverseConnections` entry for `senderId == recipientId`
- [ ] Client cannot write to `reverseConnections` — Firestore Security Rules block it (verified via emulator)
- [ ] Scribble sent and received end-to-end in under 20 seconds
- [ ] Widget updates within 10 seconds of Scribble received without opening the app
- [ ] All Firestore security rules verified via emulator test suite
- [ ] All error/edge cases produce a defined, non-crashing UI state
- [ ] Performance targets verified on a mid-tier reference device
