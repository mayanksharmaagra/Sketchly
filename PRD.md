# Product Requirements Document (PRD)
## Sketchly — v1

**A messaging app where every message is a hand-drawn note or doodle, deliverable instantly and surfaced on the Home Screen via widgets.**

> **Changelog:** App renamed Scribble → Sketchly. Auth simplified to phone-only for V1. Email/password deferred to V2. Contact system updated to phone-sync + username search. **Follow-request system moved to V2** — gated behind `ENABLE_CONNECTION_REQUESTS = false`; V1 connections form automatically when a Scribble is sent (`onScribbleCreate` reverse-connection path). **Auto-connect on receive** — senders automatically appear in recipient’s reply picker after the first Scribble. **Block system added** — users can block others from the viewer or contact history; blocked user’s sketches removed locally. **CircleScreen unified for V1** — single view showing Sync Contacts card + connected users (no Suggested/Requests tabs).

---

## 1. Problem Statement

Modern messaging is fast but impersonal. Typed text, emoji, and reused GIFs have flattened how people express affection or presence in a conversation. There is no lightweight way to send someone a genuinely personal, handmade gesture — the digital equivalent of a sticky note left on a desk — without the friction of full drawing apps or the delay of mail.

Sketchly solves this by making hand-drawn communication as fast as sending a text, and by using Home Screen widgets to recreate the physical feeling of finding a note waiting for you.

---

## 2. Target Users

| Persona | Description | Motivation |
|---|---|---|
| **Close-circle communicators** | People who message a small circle of friends/family/partners daily | Want warmth and personality, not efficiency |
| **Long-distance connections** | Couples, friends, or family separated geographically | Want a tactile "thinking of you" touchpoint |
| **Casual creatives** | People who doodle but don't consider themselves artists | Want a low-pressure way to share small creative moments |

**Not the primary target (V1):** professional/work messaging, large group broadcast, business use cases.

---

## 3. Goals

### Business Goals
- Establish a distinct, defensible niche (handwritten-first messaging)
- Drive daily engagement through the Home Screen widget as a persistent, low-effort touchpoint
- Build a foundation that supports future monetization (premium pens/paper styles, sticker packs) without needing it for MVP

### User Goals
- Sign up in under 30 seconds — phone number + OTP only, nothing else required
- Find friends via contact sync or username search
- Send a genuine, personal note in under 15 seconds
- See notes from people they care about without opening the app

---

## 4. Core Features (V1)

1. **Auth** — phone number + OTP only. Full Name + Username collected after OTP verify
2. **Contact Sync** — find existing Sketchly users from device contacts (phone hash matching)
3. **Username Search** — find any user by their unique @username
4. **Draw** — freehand canvas with pen color/thickness, undo, clear
5. **Send instantly** — to anyone found via contact sync or who has sent the user a Scribble
6. **Auto-connect on receive** — when a user receives a Scribble, the sender is automatically surfaced in the recipient picker under "Sent you a Scribble" so they can reply without syncing contacts or sending a follow request
7. **Block user** — block any sender from the Scribble viewer or contact history; blocked user’s sketches are removed from the local feed immediately
8. **Home Screen widget** — shows latest unread Scribble, tap to open
9. **Emoji reactions** — quick reaction to a received Scribble
10. **Inbox** — chronological feed of received Scribbles
11. **History** — archive of sent + received Scribbles grouped by date
12. **Circle screen** — unified view: Sync Contacts card + connected users list (no tab navigation in V1)

---

## 5. V1 Scope

**In scope:**
- Phone number + OTP signup only
- Full Name + unique Username (chosen right after OTP)
- Contact sync via hashed phone number matching
- Username search (exact match)
- 1:1 and small-group (multi-select, max 10) sending
- Auto-connect on receive (reverse-connection via `onScribbleCreate` Cloud Function)
- Block user (with local Room cleanup + server-side connection teardown)
- Single Home Screen widget showing latest Scribble
- Fixed emoji reaction set (5 options)
- Local + cloud sync of Scribbles
- Push notification on new Scribble received
- Circle screen: unified Sync Contacts card + connections list

**Deferred to V2 (code preserved, UI hidden):**
- Follow-request system (`ENABLE_CONNECTION_REQUESTS = false`) — send/accept/decline + Requests tab in Circle
- Suggested contacts tab in Circle (`HIDE_SUGGESTED_TAB = true`)
- Email address (add from Settings, not at signup)
- Password (set from Settings once email is added)
- Email-based login (enabled after email + password set)
- Email-based search

**Explicitly minimal for V1:**
- One canvas size/aspect ratio only
- One widget size/layout
- No text tool — drawing only

---

## 6. User Stories

**Auth & Discovery:**
- As a user, I want to sign up with just my phone number so I can get started in under 30 seconds.
- As a user, I want to choose a unique username so friends can find me even without my phone number.
- As a user, I want to sync my contacts so I can immediately see which friends are already on Sketchly.
- As a user, I want to search by username so I can find someone even if they are not in my contacts.
- As a user, I want to send a follow request so I can connect with someone before messaging them.
- As a user who received a Scribble, I want the sender to appear in my reply picker automatically so I can write back without syncing contacts.

**Core messaging:**
- As a user, I want to draw a quick note so I can send something personal in seconds.
- As a user, I want new Scribbles to appear on my Home Screen so I don't have to open the app.
- As a user, I want to react with an emoji so I can respond without drawing every time.
- As a user, I want to browse my past Scribbles so I can revisit meaningful notes.

---

## 7. Success Metrics

| Metric | Target (90 days post-launch) |
|---|---|
| D1 retention | ≥ 35% |
| D7 retention | ≥ 15% |
| Widget install rate (among active users) | ≥ 50% |
| Avg. Scribbles sent per active user/week | ≥ 3 |
| Contact sync rate (users who grant permission) | ≥ 60% |
| Follow request acceptance rate | ≥ 70% |
| Median time from open-draw-screen to send | < 20 seconds |
| Crash-free session rate | ≥ 99.5% |

---

## 8. Assumptions

- Users are comfortable sharing their phone number for signup (standard in messaging apps)
- Enough of a user's existing contacts will also be on Sketchly to make contact sync immediately useful, OR username search is sufficient as a fallback
- Users are willing to grant Home Screen widget placement — this is core, not optional
- Drawing on a phone screen (finger or stylus) is acceptable for the target audience
- Push notification permission will be granted by a majority of users

---

## 9. Risks

| Risk | Mitigation |
|---|---|
| Cold-start network effect — no connections to message | Contact sync + username search + invite link all work together |
| One-way discovery gap — sender finds recipient but recipient can't reply | **Auto-connect on receive** — sender is surfaced in recipient's picker after first Scribble |
| Users don't add the widget | Strong first-run onboarding with widget setup walkthrough |
| Drawing feels clunky on small screens | Prioritize canvas responsiveness early; 60fps target |
| Follow request friction reduces sends | Show "Pending" state clearly; notify when accepted |
| Low network reliability | Optimistic local send + WorkManager retry |
| Privacy concerns around contact sync | Hash on-device, never store raw numbers, clear consent copy |

---

## 10. Out of Scope (V1)

- Email signup / login (V2 — addable from Profile Settings)
- Password (V2 — set after email is added)
- Email-based search (V2)
- Text messaging / typed captions
- Video or audio Scribbles
- Public/social feed or discovery
- Custom widget themes
- Scheduled or recurring sends
- Web or desktop clients
- Monetization

---

## 11. Acceptance Criteria (V1 Release)

- [ ] User can sign up with phone + OTP and choose Full Name + Username in under 60 seconds
- [ ] Contact sync finds existing Sketchly users from device contacts without storing raw phone numbers server-side
- [ ] Username search returns correct user by exact @username match
- [ ] Follow request flow works end-to-end: send → notify recipient → accept/decline → connection established
- [ ] User can draw and send a Scribble to 1+ connected recipients in under 20 seconds end-to-end
- [ ] Recipient receives a push notification within 5 seconds of send (normal network conditions)
- [ ] After receiving a Scribble, the sender appears in the recipient's "Sent you a Scribble" picker section without any contact sync
- [ ] Home Screen widget updates within 10 seconds of new Scribble received
- [ ] User can react to a received Scribble with one of the 5 provided emoji
- [ ] History loads paginated, grouped by date, filterable by contact
- [ ] Failed sends retry automatically and show a clear failure state if all retries fail
- [ ] All core flows work crash-free across the last 2 major Android OS versions
