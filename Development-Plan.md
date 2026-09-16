# Development Plan
## Sketchly — v1

**A messaging app where every message is a hand-drawn note or doodle, deliverable instantly and surfaced on the Home Screen via widgets.**

> **Changelog:** Auth updated — phone/OTP only. Full Name + Username screen added after OTP. Contact sync + username search added as new milestones. Email/password removed from V1 scope. **Auto-connect on receive added** — `onScribbleCreate` reverse-connection upsert, `ContactRepository.getReverseConnections()`, `DrawViewModel.contactGroups`, and two-section `SendToScreen` UI. **Block system added** — `BlockRepository`, `BlockViewModel`, `BlockedUsersScreen`. **CircleScreen unified for V1** — `FeatureFlags.HIDE_SUGGESTED_TAB = true`; tab row removed; Sync card + connections list shown on a single screen. **Follow-request UI gated** — `ENABLE_CONNECTION_REQUESTS = false`; V1 connections form automatically via scribble send (reverse-connection path). Avatar fallback is initials everywhere.

---

## 1. V1 Scope Recap

**In scope:** phone + OTP auth, username, contact sync (hash matching), username search, draw, send, auto-connect on receive, inbox, widget, emoji reactions, history, block user.

**V2 gated (code preserved, UI hidden):** follow-request system (`ENABLE_CONNECTION_REQUESTS = false`), suggested contacts tab (`HIDE_SUGGESTED_TAB = true`), email/password auth.

**Out of scope:** text tool, video/audio Scribbles, social feed, monetization.

---

## 2. Milestones Overview

| Milestone | Goal |
|---|---|
| M0 — Project Setup | Repo, tooling, CI, Firebase provisioned |
| M1 — Auth (Phone + Username) | Signup, OTP verify, name + username screen, login |
| M2 — User Profile + Firestore | UserProfile model, Firestore write/read, security rules |
| M3 — Contact Sync | Hash matching, synced contacts shown in Circle screen |
| M4 — Search *(V2: + Follow Request)* | Username search; follow-request flow gated behind `ENABLE_CONNECTION_REQUESTS` |
| M5 — Draw + Send | Canvas, recipient picker (two-section), optimistic send, auto-connect on receive |
| M6 — Notifications + Widget | FCM push, Glance widget, WorkManager update job |
| M7 — Reactions + History | Reaction write, viewer, history screen with pagination |
| M8 — Block + Safety | Block user, blocked users list, local Room cleanup, server teardown |
| M9 — Hardening + QA | Edge cases, performance, accessibility, security rules audit |
| M10 — Release Candidate | Internal testing, bug bash, Play Store listing |

---

## 3. Detailed Roadmap

### M0 — Project Setup
- [ ] Initialize Android project (Kotlin, Compose, Hilt, min SDK 26)
- [ ] Set up base module structure per Architecture doc
- [ ] Provision Firebase project (dev + staging + prod): Auth, Firestore, Storage, Functions, FCM
- [ ] Set up GitHub Actions CI (lint, unit tests on PR)
- [ ] Configure Firebase emulator suite for local dev (Auth, Firestore, Functions)
- **DoD:** app builds, runs blank Compose screen, CI green, emulators run locally

### M1 — Auth (Phone + OTP + Username)
- [ ] Phone number entry screen (E.164 format validation)
- [ ] Firebase Phone Auth — OTP send + verify
- [ ] New user detection (first time this phone number is seen)
- [ ] Full Name + Username screen (shown only for new users)
- [ ] Username uniqueness check against Firestore before account creation
- [ ] Returning user → skip name/username → go directly to Inbox
- [ ] Session persistence (Firebase refresh token)
- [ ] Logout → clear Room DB + session
- **DoD:** full signup and login flows work on a real device with a real SIM; returning user skips setup; username uniqueness enforced

### M2 — UserProfile + Firestore
- [ ] Define UserProfile data model (uid, displayName, username, phoneNumberHash, avatarUrl, isSearchable, createdAt, authProvider)
- [ ] Write UserProfile to Firestore on signup
- [ ] Firestore Security Rules v1 (user can only write own profile; read own profile always; read others limited to public fields)
- [ ] Verify rules via Firestore emulator test suite
- [ ] Room UserProfileEntity + DAO
- **DoD:** UserProfile created on signup, security rules block unauthorized reads/writes (emulator verified)

### M3 — Contact Sync
- [ ] READ_CONTACTS permission request with rationale dialog
- [ ] Read device contacts → normalize to E.164 → SHA-256 hash client-side
- [ ] `matchContactsByHash` Cloud Function (authenticated HTTP, accepts hashes[], returns matched UserProfiles)
- [ ] Store matched users in Room (SuggestedContactEntity, source="contact_sync")
- [ ] "People You May Know" section in Find Friends screen
- [ ] Users who decline permission — skip silently, offer retry in Settings
- [ ] Network inspection verification: confirm raw numbers never transmitted
- **DoD:** contact sync shows matched Sketchly users from device contacts; confirmed by network log that only hashes are sent

### M4 — Search + Follow Request System
- [ ] Username search screen (exact match, Firestore query)
- [ ] Search result card: avatar, name, username, Follow/Pending/Connected button
- [ ] isSearchable = false users excluded from results
- [ ] Send follow request → write to Firestore follow_requests collection
- [ ] `onFollowRequestCreate` Cloud Function → FCM to recipient
- [ ] Follow Requests screen (incoming list: accept / decline)
- [ ] `onFollowRequestAccept` Cloud Function → write connection docs both ways → FCM to sender
- [ ] Cancel outgoing request
- [ ] Room: ConnectionEntity, FollowRequestEntity DAOs
- [ ] Firestore Security Rules for follow_requests and connections
- **DoD:** full follow flow works end-to-end on two real devices; connections appear in Room + Firestore after accept; security rules verified

### M5 — Draw + Send
- [ ] Draw screen: canvas, pointerInput stroke capture, color/thickness picker, undo, clear
- [ ] Canvas state persists on app background
- [ ] Recipient picker — "Your Contacts" section: only mutually connected users (from Room ConnectionEntity)
- [ ] Recipient picker — "Sent you a Scribble" section: users from `reverseConnections/{uid}/senders` (auto-connect on receive)
- [ ] `ContactRepository.getReverseConnections()` — Firestore snapshot listener on `reverseConnections/{uid}/senders`
- [ ] `DrawViewModel.contactGroups` — three-stream combine of suggested + connected + reverse; deduplication logic
- [ ] `SendToScreen` two-section UI: section headers, gold "↩ Reply" badge on reverse contacts
- [ ] Multi-select (max 10) across both sections
- [ ] Optimistic local write (Room) → async Firestore write
- [ ] WorkManager retry (exponential backoff, max 5 attempts)
- [ ] "Failed to send — tap to retry" state
- [ ] `onScribbleCreate` Cloud Function: FCM fan-out **+ reverse-connection upsert** (`reverseConnections/{recipientId}/senders/{senderId}`)
- [ ] Firestore Security Rules: `reverseConnections` owner-read, `allow write: if false`
- **DoD:** two connected devices can exchange a Scribble end-to-end; sender appears in recipient's "Sent you a Scribble" picker after first send; group Scribbles create entries for all eligible recipients; failed sends retry and show clear error

### M6 — Notifications + Home Screen Widget
- [ ] FCM data message handling (ScribbleMessagingService)
- [ ] WidgetUpdateWorker: fetch Scribble → render bitmap → update Glance widget
- [ ] ScribbleWidget (Glance): 2x2 and 4x2 layouts, unread Scribble, empty state
- [ ] Widget deep-link → ScribbleViewerScreen
- [ ] First-run widget install onboarding (after first successful send)
- [ ] Widget doodle preview toggle in Settings
- **DoD:** widget updates within 10 seconds of Scribble received without opening the app; deep-link opens correct Scribble

### M7 — Reactions + History
- [ ] Scribble Viewer screen (fullscreen, replay animation, reaction row)
- [ ] Reaction write → Firestore; live update via snapshot listener
- [ ] `onReactionCreate` Cloud Function → FCM to sender
- [ ] History screen: date-grouped grid, contact filter, Paging 3
- [ ] Contact's Scribbles screen (all Scribbles with one person)
- **DoD:** reactions sync in real-time when app is open and via notification when backgrounded; history paginates correctly

### M8 — Hardening + QA
- [ ] All error/empty states from SRS §8 implemented and tested
- [ ] All edge cases from SRS §9 tested and non-crashing
- [ ] Auto-connect edge cases: self-send guard, deduplication with synced contacts, group Scribble all recipients
- [ ] Accessibility pass (touch targets, TalkBack, contrast, font scaling)
- [ ] Performance validation on mid-tier reference device (all SRS §11 targets)
- [ ] Full Firestore Security Rules audit via emulator suite (including `reverseConnections` write-block)
- [ ] Rate limiting verified (send + follow request limits)
- [ ] OTP lockout after 3 failed attempts tested
- [ ] Crashlytics + Performance Monitoring verified in staging
- **DoD:** all SRS acceptance criteria pass; crash-free rate ≥ 99.5% in staging; performance targets met on reference device

### M9 — Release Candidate
- [ ] Internal testing track release (Play Console)
- [ ] Bug bash + severity-based triage
- [ ] Play Store listing: screenshots, description, content rating
- [ ] Final security rules + rate limit review
- [ ] Staged rollout plan (10% → 50% → 100%)
- **DoD:** no P0/P1 bugs open; crash-free rate ≥ 99.5% in internal testing; staged rollout approved

---

## 4. Dependency Graph

```
M0 (Setup)
  └─► M1 (Auth)
        └─► M2 (UserProfile + Firestore)
              ├─► M3 (Contact Sync)
              │         └─► M4 (Search + Follow)
              │                   └─► M5 (Draw + Send)
              │                             ├─► M6 (Notifications + Widget)
              │                             └─► M7 (Reactions + History)
              │                                         └─► M8 (Hardening)
              │                                                   └─► M9 (RC)
              └─► M2 security rules must complete before M3/M4 begin
```

---

## 5. Priorities (If Timeline Pressure Forces Cuts)

1. **Non-negotiable:** M0→M5 — without auth, connections, draw, and send, there is no product
2. **Non-negotiable:** M6 widget — this is Sketchly's core differentiator
3. **Required but flexible on polish:** M7 reactions (can ship with 3 emoji instead of 5), History (can ship as flat list before date-grouped grid)
4. **Never cut:** Security rules audit, contact sync privacy verification (raw number check), OTP lockout — cutting these creates legal/trust risk

---

## 6. Definition of Done (Global)

A task is done only when:
- [ ] Code merged to `main` via reviewed PR with passing CI
- [ ] Corresponding SRS requirement satisfied and testable
- [ ] Manual test on at least one mid-tier physical device
- [ ] No new Crashlytics-reportable crash introduced
- [ ] Firestore security rules updated and emulator-tested if the task touches data access patterns
- [ ] UI matches the UI/UX doc (or deviation is explicitly approved)

---

## 7. Suggested Team Split (2 developers)

| Dev A (Client) | Dev B (Backend/Integration) |
|---|---|
| M1 UI screens (phone, OTP, name/username) | M0 Firebase setup + emulators |
| M3 contact reading + hashing (client-side) | M2 Firestore schema + security rules |
| M4 Search UI + Follow Request UI | M3 matchContactsByHash Cloud Function |
| M5 Draw canvas + recipient picker UI | M4 onFollowRequest Cloud Functions |
| M6 Glance widget + deep-link | M5 Firestore write + WorkManager retry |
| M7 Viewer + History UI | M6 FCM + WidgetUpdateWorker |
| M8 Accessibility + UI edge cases | M8 Security audit + performance |
