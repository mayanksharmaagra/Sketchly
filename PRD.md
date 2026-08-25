# Product Requirements Document (PRD)
## Scribble

**A messaging app where every message is a hand-drawn note or doodle, deliverable instantly and surfaced on the Home Screen via widgets.**

---

## 1. Problem Statement

Modern messaging is fast but impersonal. Typed text, emoji, and reused GIFs have flattened how people express affection or presence in a conversation. There's no lightweight way to send someone a genuinely personal, handmade gesture — the digital equivalent of a sticky note left on a desk — without the friction of full drawing/design apps or the delay of mail.

Scribble solves this by making **hand-drawn communication as fast as typing a text**, and by using Home Screen widgets to recreate the physical feeling of finding a note waiting for you.

---

## 2. Target Users

| Persona | Description | Motivation |
|---|---|---|
| **Close-circle communicators** | People who message a small circle of friends/family/partners daily | Want warmth and personality, not efficiency |
| **Long-distance connections** | Couples, friends, or family separated geographically | Want a tactile, "thinking of you" touchpoint |
| **Casual creatives** | People who doodle but don't consider themselves artists | Want a low-pressure way to share small creative moments |

**Not the primary target (v1):** professional/work messaging, large group broadcast, business use cases.

---

## 3. Goals

### Business Goals
- Establish a distinct, defensible niche (handwritten-first messaging) rather than competing head-on with text messengers
- Drive daily engagement through the Home Screen widget as a persistent, low-effort touchpoint
- Build a foundation that supports future monetization (premium pens/paper styles, sticker packs) without needing it for MVP

### User Goals
- Send a genuine, personal note in under 15 seconds
- See notes from people they care about without opening the app
- Feel a small moment of delight, not obligation, when using the app

---

## 4. Core Features (MVP)

1. **Draw** — freehand canvas with pen color/thickness, undo, clear
2. **Send instantly** — to one or multiple recipients from contacts
3. **Home Screen widget** — shows latest unread Scribble, tap to open
4. **Emoji reactions** — quick reaction to a received Scribble
5. **Inbox** — chronological feed of received Scribbles
6. **History** — archive of sent + received Scribbles, searchable by contact/date

---

## 5. MVP Scope

**In scope:**
- 1:1 and small-group (multi-select) sending
- Single Home Screen widget type showing the latest Scribble
- Basic emoji reaction set (fixed, non-custom)
- Local + cloud sync of Scribbles (so history persists across devices/reinstall)
- Push notification on new Scribble received

**Explicitly minimal for v1:**
- One canvas size/aspect ratio only
- One widget size/layout to start (expand post-MVP)
- No text tool — drawing only, to preserve the core identity

---

## 6. User Stories

- As a user, I want to draw a quick note so I can send something personal in seconds.
- As a user, I want to pick one or several recipients so I can share a Scribble with a small group at once.
- As a user, I want new Scribbles to appear on my Home Screen so I don't have to open the app to feel connected.
- As a user, I want to react with an emoji so I can respond without drawing something back every time.
- As a user, I want to browse my past Scribbles so I can revisit meaningful notes.
- As a user, I want to know when someone has viewed or reacted to my Scribble so I feel the exchange is real.

---

## 7. Success Metrics

| Metric | Target (90 days post-launch) |
|---|---|
| D1 retention | ≥ 35% |
| D7 retention | ≥ 15% |
| Widget install rate (among active users) | ≥ 50% |
| Avg. Scribbles sent per active user/week | ≥ 3 |
| Median time from open-draw-screen to send | < 20 seconds |
| Crash-free session rate | ≥ 99.5% |

---

## 8. Assumptions

- Users have an existing contact/friend graph they want to reach (via phone contacts or in-app friending)
- Users are willing to grant Home Screen widget placement — this is core, not optional, to the value prop
- Drawing on a phone screen (finger or stylus) is an acceptable input method for the target audience
- Push notification permission will be granted by a majority of users, since it directly enables the core loop

---

## 9. Risks

| Risk | Mitigation |
|---|---|
| Users don't add the widget → core value prop lost | Strong first-run onboarding that walks through widget setup |
| Drawing feels clunky on small screens → low engagement | Prioritize canvas responsiveness and low-latency stroke rendering early |
| Low network reliability delays "instant" feel | Optimistic local send + background retry via WorkManager |
| Privacy concerns around always-on widget previews | Allow users to disable widget content preview (show sender only, not doodle) |
| Cold-start network effect (no one to send to) | Contact-based onboarding, prompt to invite via SMS/share link |

---

## 10. Out of Scope (v1)

- Text messaging / typed captions
- Video or audio Scribbles
- Public/social feed or discovery
- Custom widget themes or multiple simultaneous widgets
- Scheduled or recurring sends
- Web or desktop clients
- Monetization (sticker packs, premium tools)

---

## 11. Acceptance Criteria (MVP release)

- [ ] User can draw and send a Scribble to 1+ recipients in under 20 seconds end-to-end
- [ ] Recipient receives a push notification within 5 seconds of send (normal network conditions)
- [ ] Home Screen widget updates to show the newest unread Scribble without requiring the app to be opened
- [ ] User can react to a received Scribble with one of the provided emoji
- [ ] User can view full send/receive history, grouped by date, with working search/filter by contact
- [ ] Failed sends are retried automatically and clearly indicated in the UI if they ultimately fail
- [ ] All core flows work with no crashes across the last 2 major Android OS versions
