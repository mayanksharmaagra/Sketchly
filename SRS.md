# Software Requirements Specification (SRS)
## Scribble

**A messaging app where every message is a hand-drawn note or doodle, deliverable instantly and surfaced on the Home Screen via widgets.**

---

## 1. Purpose

This document defines the functional and non-functional requirements for Scribble's MVP release, in testable form, to guide implementation and QA.

---

## 2. User Roles & Permissions

| Role | Description | Permissions |
|---|---|---|
| **Registered User** | Any authenticated account holder | Draw/send/receive Scribbles, react, view own history, manage own contacts/widget |
| **Unauthenticated Visitor** | Pre-signup app state | Onboarding screens and sign-up/login only; no messaging access |
| **System/Admin** (internal only) | Backend operators | Content moderation review, abuse reports, account suspension — no end-user-facing role |

No tiered permission system is required in MVP (no "admin" role within the consumer app itself).

---

## 3. Functional Requirements

### FR-1: Authentication
- FR-1.1 Users must sign up via phone number (OTP) or email + password.
- FR-1.2 Users must verify phone/email before sending Scribbles (viewing received ones may be allowed pre-verification, TBD by product).
- FR-1.3 Sessions persist via refresh token; access token expires in ≤ 1 hour.
- FR-1.4 Users can log out, which clears local session tokens and cached Scribble content from the device (Room DB wipe).

### FR-2: Contacts
- FR-2.1 Users may sync device contacts (opt-in, explicit permission prompt) to find existing Scribble users.
- FR-2.2 Users may add contacts manually by phone number/username/invite link.
- FR-2.3 The system must not display any contact-matching data to a user without their explicit sync consent.

### FR-3: Drawing
- FR-3.1 The draw canvas must capture freehand stroke input via touch (finger or stylus).
- FR-3.2 Users must be able to select pen color from a predefined palette (minimum 6 colors) in MVP.
- FR-3.3 Users must be able to select pen thickness (minimum 3 preset sizes).
- FR-3.4 Users must be able to undo the last stroke and clear the entire canvas.
- FR-3.5 Canvas state must persist if the app is backgrounded mid-draw (not lost on interruption).

### FR-4: Sending
- FR-4.1 Users must be able to select 1 or more recipients from their contact list before sending.
- FR-4.2 A Scribble must be sendable with at most 2 taps after drawing is complete (recipient select → send).
- FR-4.3 On send, the system must persist the Scribble locally immediately (optimistic UI) and queue it for upload.
- FR-4.4 If upload fails, the system must retry with exponential backoff (max 5 attempts) and surface a clear "failed to send" state if all attempts fail.
- FR-4.5 Each Scribble sent to multiple recipients must be tracked as delivered/read independently per recipient.

### FR-5: Receiving & Inbox
- FR-5.1 The system must deliver a push notification to each recipient within 5 seconds of successful send under normal network conditions.
- FR-5.2 The Inbox screen must display received Scribbles in reverse-chronological order.
- FR-5.3 Unread Scribbles must be visually distinguished from read ones.
- FR-5.4 Opening a Scribble marks it as read and notifies the sender (read receipt).

### FR-6: Home Screen Widget
- FR-6.1 The widget must display the most recent unread Scribble (thumbnail + sender name).
- FR-6.2 The widget must update within 10 seconds of a new Scribble being received on-device (post-push-delivery).
- FR-6.3 Tapping the widget must deep-link directly into the full Scribble Viewer for that item.
- FR-6.4 If there is no unread Scribble, the widget must show a defined empty state (not a blank/broken view).
- FR-6.5 Widget content preview (showing the doodle thumbnail) must be toggleable off in Settings for privacy (falls back to sender name only).

### FR-7: Reactions
- FR-7.1 Users must be able to react to a received Scribble with one emoji from a fixed set (minimum 5 options).
- FR-7.2 A user may change their reaction but only one reaction per user per Scribble is stored.
- FR-7.3 The sender must see reactions update in near-real-time if the app is open (Firestore listener) or via notification if backgrounded.

### FR-8: History
- FR-8.1 Users must be able to view all sent and received Scribbles, grouped by date.
- FR-8.2 Users must be able to filter history by contact.
- FR-8.3 History must load with pagination (do not load entire history into memory at once).

---

## 4. Business Rules

- BR-1: A Scribble cannot be edited after it is sent.
- BR-2: A Scribble can be deleted by the sender from their own view only; it does not retract from recipients' inboxes (no unsend in MVP).
- BR-3: Maximum recipients per single send: 10 (soft cap to preserve "personal" feel and control fan-out cost).
- BR-4: Maximum stroke data size per Scribble: 2 MB (guards against pathological/abuse payloads).
- BR-5: Inactive accounts (no login for 12 months) may have cached remote data purged per data retention policy.

---

## 5. Data Requirements

| Entity | Key Fields |
|---|---|
| **User** | id, phone/email, displayName, avatarUrl, createdAt, settings (widgetPreviewEnabled, notificationsEnabled) |
| **Contact/Relationship** | userId, contactUserId, source (synced/manual/invite), status |
| **Scribble** | id, senderId, recipientIds[], strokes[], backgroundColor, createdAt, deliveryStatus (per recipient), readStatus (per recipient) |
| **Stroke** | points[] {x, y, pressure}, colorHex, widthDp |
| **Reaction** | scribbleId, userId, emoji, createdAt |

---

## 6. Validation Rules

- Phone number must match E.164 format before OTP is issued.
- Display name: 1–30 characters, no leading/trailing whitespace.
- At least 1 recipient required before "Send" is enabled.
- Canvas must contain at least 1 stroke before "Send" is enabled (prevents blank sends).
- Reaction emoji must be one of the server-defined allowed set (reject arbitrary client input).

---

## 7. Authentication & Authorization

- OTP-based phone auth or email/password via Firebase Authentication (or equivalent).
- All API/Firestore requests must include a valid auth token; unauthenticated requests are rejected.
- Firestore security rules must enforce: a user can only read Scribbles where their UID is in `recipientIds` or matches `senderId`; a user can only write Scribbles where their UID matches `senderId`.
- A user may only write a reaction document where the reaction's `userId` matches their own auth UID.

---

## 8. Error Handling

- Network failure during send → local optimistic copy retained, retry queued, non-blocking error toast shown only after final retry failure.
- Push notification delivery failure → silent; widget update still occurs on next app foreground/background sync as fallback.
- Malformed/oversized stroke payload → rejected server-side with a specific error code; client shows "couldn't send, note too large — try a simpler drawing."
- Auth token expiry mid-session → silent refresh; if refresh fails, force re-login with session-expired messaging.

---

## 9. Edge Cases

- Recipient has uninstalled the app → delivery marked failed after a defined timeout; sender is not falsely shown "delivered."
- User sends to a mix of valid and since-deleted contacts → send proceeds for valid recipients, invalid ones are silently excluded with a non-blocking notice.
- Device offline entirely at send time → Scribble queues locally and sends automatically on reconnect (must survive app kill).
- Multiple rapid reactions from the same user on the same Scribble → last write wins, no duplicate reaction records.
- Widget present on Home Screen but app fully force-stopped by OS → widget must still reflect last-synced state (not crash or blank).

---

## 10. Security Requirements

- All network traffic over TLS.
- Stroke data and metadata encrypted at rest (standard cloud provider encryption acceptable for MVP).
- No third-party analytics SDK may receive raw stroke/drawing content.
- Contact sync data must never be persisted server-side beyond matching purposes without explicit separate consent.
- Rate limiting on send endpoint to prevent spam/abuse (e.g., max 60 sends/hour/user).

---

## 11. Performance Requirements

- Draw canvas input latency: < 16ms per frame (60fps) for stroke rendering.
- Send action (local optimistic commit): < 200ms perceived latency.
- Widget update after push delivery: < 10 seconds.
- Inbox initial load: < 1 second for first 20 items on a typical 4G connection.
- App cold start to Draw screen: < 2 seconds on a mid-tier device.

---

## 12. Acceptance Criteria (Requirements-Level)

- [ ] Every FR above is covered by at least one automated or manual test case
- [ ] Security rules verified via Firestore emulator test suite (no unauthorized read/write possible)
- [ ] Performance targets verified on a mid-tier reference device (not just flagship)
- [ ] All error/edge cases produce a defined, non-crashing UI state
