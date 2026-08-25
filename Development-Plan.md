# Development Plan
## Scribble

**A messaging app where every message is a hand-drawn note or doodle, deliverable instantly and surfaced on the Home Screen via widgets.**

Based on the PRD, SRS, Architecture, and UI/UX documents. This plan sequences work so development can begin without guessing.

---

## 1. MVP Scope Recap

In scope: draw, send (1:many), Home Screen widget, emoji reactions, inbox, history, push notifications, phone/email auth.
Out of scope: typed text messages, audio/video, social feed, multiple widget themes, monetization. (Full list in PRD §10.)

---

## 2. Milestones Overview

| Milestone | Goal |
|---|---|
| M0 — Project Setup | Repo, tooling, CI, Firebase project provisioned |
| M1 — Core Draw & Local Persistence | Canvas works, Scribbles save locally, feels good to use |
| M2 — Backend Integration | Auth, Firestore, send/receive across two real accounts |
| M3 — Notifications & Widget | Push wakes device, widget renders and updates |
| M4 — Reactions & History | Full feature set complete |
| M5 — Hardening & QA | Edge cases, performance, accessibility, error states |
| M6 — Release Candidate | Internal testing track, bug fixing, store listing |

---

## 3. Detailed Roadmap

### M0 — Project Setup
- [ ] Initialize Android project (Kotlin, Compose, min SDK 26)
- [ ] Set up Hilt DI, base module structure per Architecture doc §2 project structure
- [ ] Provision Firebase project (dev + staging + prod), enable Auth, Firestore, Storage, Functions, FCM
- [ ] Set up GitHub Actions CI (lint, unit tests on PR)
- [ ] Configure Firebase emulator suite for local dev
- **Definition of Done:** app builds and runs a blank Compose screen; CI pipeline green on a trivial PR; Firebase emulators run locally.

### M1 — Core Draw & Local Persistence
*Depends on: M0*
- [ ] Build Draw screen composable: Canvas + pointerInput stroke capture (SRS FR-3)
- [ ] Implement pen color/thickness selection, undo, clear (SRS FR-3.2–3.4)
- [ ] Define data models (`Scribble`, `Stroke`, `Point`) per Architecture §2
- [ ] Set up Room database + DAOs for local Scribble persistence
- [ ] Canvas state survives backgrounding (SRS FR-3.5)
- **Priority:** Highest — this is the core product loop; everything else is scaffolding around it.
- **Definition of Done:** user can draw, undo, clear, and the in-progress drawing survives an app background/foreground cycle. No backend involved yet.

### M2 — Backend Integration (Auth, Send, Receive)
*Depends on: M1*
- [ ] Implement phone/OTP and email/password auth (Firebase Auth) — SRS FR-1
- [ ] Implement contact sync (opt-in) and manual contact add — SRS FR-2
- [ ] Build Recipient Picker screen (UI/UX §4.3)
- [ ] Implement Firestore write on send (optimistic local write → async remote write) — Architecture §4
- [ ] Implement Firestore Security Rules per SRS §7; validate with emulator test suite
- [ ] Build Inbox screen with Firestore-synced Room cache — SRS FR-5
- [ ] Implement retry/backoff for failed sends via WorkManager — SRS FR-4.4
- **Priority:** Highest, immediately after M1 — no product without this.
- **Definition of Done:** two real test accounts can send/receive a Scribble end-to-end; failed sends visibly retry and eventually show a clear failure state; security rules block unauthorized reads/writes (verified by emulator tests).

### M3 — Notifications & Home Screen Widget
*Depends on: M2*
- [ ] Implement `onScribbleCreate` Cloud Function for FCM fan-out (Architecture §7)
- [ ] Implement `ScribbleMessagingService` (FCM receiver) on client
- [ ] Implement `WidgetUpdateWorker` (fetch, render bitmap, cache, trigger widget update)
- [ ] Build `ScribbleWidget` (Glance) — 2x2 and 4x2 layouts per UI/UX §4.6
- [ ] Build `ScribbleWidgetReceiver`, manifest registration
- [ ] Implement widget empty/stale states
- [ ] Build first-run widget install onboarding flow (UI/UX §2)
- **Priority:** High — this is the product's key differentiator; do not treat as a stretch goal.
- **Definition of Done:** sending a Scribble from Device A causes Device B's Home Screen widget to update within the 10-second target (SRS FR-6.2) without the app being opened on Device B.

### M4 — Reactions & History
*Depends on: M2 (Reactions), M2 (History — can run in parallel with M3)*
- [ ] Build Scribble Viewer screen with reaction chip row (UI/UX §4.4)
- [ ] Implement reaction write + Firestore security rule (SRS FR-7, §7)
- [ ] Implement `onReactionCreate` Cloud Function for sender notification
- [ ] Build History screen with date grouping, contact filter, pagination (SRS FR-8)
- [ ] Implement stroke-by-stroke replay animation (UI/UX §12) — capped at 1.5s
- **Priority:** Medium-high — required for MVP acceptance criteria, but can proceed in parallel with M3 since it depends only on M2.
- **Definition of Done:** reactions sync live when sender's app is foregrounded and via notification when backgrounded; History loads paginated results correctly with no full-dataset load into memory.

### M5 — Hardening & QA
*Depends on: M1–M4 complete*
- [ ] Implement all error/empty states defined in UI/UX §7
- [ ] Accessibility pass: touch targets, TalkBack labels, font scaling, contrast (UI/UX §9)
- [ ] Performance validation against SRS §11 targets on a mid-tier reference device
- [ ] Edge case testing per SRS §9 (offline send, uninstalled recipient, force-stopped app widget behavior, etc.)
- [ ] Rate limiting / abuse protection verification (SRS §10, BR-3/BR-4)
- [ ] Crashlytics + Performance Monitoring wired and verified in staging
- **Definition of Done:** every edge case in SRS §9 has a defined, tested, non-crashing behavior; performance targets met on reference hardware; accessibility checklist passes.

### M6 — Release Candidate
*Depends on: M5*
- [ ] Internal testing track release (Play Console)
- [ ] Bug bash + triage (severity-based fix prioritization)
- [ ] Store listing assets (screenshots reflecting UI/UX doc, description from PRD problem/goals)
- [ ] Final security rules + rate limit review before public release
- [ ] Staged rollout plan (e.g., 10% → 50% → 100%)
- **Definition of Done:** crash-free session rate ≥ 99.5% in internal testing (PRD §7 target), no P0/P1 bugs open, staged rollout plan approved.

---

## 4. Dependency Graph (Summary)

```
M0 (Setup)
  └─► M1 (Draw + Local)
        └─► M2 (Auth + Send/Receive Backend)
              ├─► M3 (Push + Widget)
              └─► M4 (Reactions + History)  [can run parallel to M3]
                    └─► M5 (Hardening/QA)
                          └─► M6 (Release Candidate)
```

---

## 5. Priorities (if timeline pressure forces cuts)

1. **Non-negotiable for MVP:** M1, M2, M3 (draw, send, widget — this is the entire product thesis)
2. **Required but flexible on polish:** M4 reactions (can ship with a smaller emoji set), History (can ship with simpler flat list before full date-grouped/filtered UI)
3. **Never cut:** Security rules validation, retry/failure states, widget empty state — cutting these produces a broken-feeling product, not just a smaller one

---

## 6. Definition of Done (Global, applies to every task above)

A task is "done" only when:
- [ ] Code is merged to `main` via reviewed PR with passing CI
- [ ] Corresponding SRS functional requirement(s) are satisfied and testable
- [ ] Corresponding UI/UX spec is matched (or deviations are explicitly approved)
- [ ] Manual test performed on at least one mid-tier physical device (not emulator-only)
- [ ] No new Crashlytics-reportable crash introduced
- [ ] Firestore security rules updated and tested if the task touches data access patterns

---

## 7. Suggested Team Sequencing (if more than one developer)

- **Dev A (Client-focused):** M1 → M3 (widget/notifications) → M5 accessibility/perf
- **Dev B (Backend/Integration-focused):** M0 backend setup → M2 → M4 → M5 edge cases/security
- Both converge on M6 for release hardening and bug bash.

This plan is sequenced so development can start immediately at M0 without further clarification, with clear stopping points (Definition of Done) at every milestone.
