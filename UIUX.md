# UI/UX Document
## Sketchly

**A messaging app where every message is a hand-drawn note or doodle, deliverable instantly and surfaced on the Home Screen via widgets.**

---

## 1. Design Principles

1. **Paper, not screen.** The app should feel like a notebook, not a chat app — warm, tactile, low-glare.
2. **Handwriting is the hero.** UI chrome (buttons, nav, cards) stays clean and quiet so the user's own drawing is always the most visually interesting thing on screen.
3. **Speed over polish steps.** Every core action (draw → send) should require the minimum possible taps and zero unnecessary confirmation screens.
4. **The widget is not an add-on.** It's designed with equal care to the in-app screens, since it's the primary way most users will experience the app day-to-day.

---

## 2. User Journey (First-Time)

```
Sign up (phone + OTP)
   → Choose Full Name + Username
   → Contact Sync permission + hash matching
   → Guided first drawing (tutorial canvas)
   → Send first Scribble to a contact (or username search result)
   → Prompt: "Add Scribble to your Home Screen" (widget install walkthrough)
   → Land on Dashboard (Inbox)
```

The widget install prompt is placed **after** the first successful send — not before — so the user has already felt the core value before being asked for the extra commitment.

---

## 3. Navigation Structure

```
Bottom Nav (3 tabs):
 ┌─────────┬─────────┬─────────┐
 │  Inbox  │  Draw    │ History │
 │ (home)  │ (center, │         │
 │         │ prominent)│         │
 └─────────┴─────────┴─────────┘
```

- **Draw** is the center tab, visually emphasized (gold FAB-style button) since it’s the primary action.
- Tapping a Scribble in Inbox/Dashboard routes to **Scribble Viewer** if unread, or **Contact History** if already read.
- Settings accessible via avatar icon on Profile screen (from Dashboard top-right or History top-left).
- **Circle screen** ("Your Circle") is accessible from the Circle icon on the Dashboard top bar.

---

## 4. Screens & Flows

### 4.1 Inbox
- **Purpose:** surface received Scribbles, most recent first.
- **Layout:** vertical list of note-style cards — sender avatar, small doodle thumbnail, sender name, relative timestamp, unread indicator (dot or bold state).
- **Empty state:** friendly illustration + "No Scribbles yet — draw one to get things started" with a direct button to Draw.
- **Interaction:** tap card → Scribble Viewer. Long-press → quick reaction without opening full view (optional stretch, not MVP-blocking).

### 4.2 Draw Screen
- **Purpose:** the core creation surface.
- **Layout:** fullscreen canvas (paper-textured background), floating bottom toolbar (color swatches, thickness, undo, clear), "Send" button top-right, back/close top-left.
- **Interaction:** immediate drawing on touch-down, no mode-switching required. Toolbar auto-collapses to a minimal state after a few seconds of inactive drawing to maximize canvas space, reappears on tap.
- **States:** empty canvas → Send disabled; ≥1 stroke → Send enabled.

### 4.3 Recipient Picker (SendToScreen)
- **Purpose:** choose who receives the Scribble.
- **Layout:** full-screen — search field at top, scrollable two-section contact list, sticky "Send to N" button at bottom.
- **Two sections:**
  - **"YOUR CONTACTS"** — users from contact sync. Standard contact row (avatar, name, username, selection circle).
  - **"SENT YOU A SCRIBBLE"** — users who have already sent the current user a Scribble and are not yet in contacts. Same row layout but with a gold **\u21a9 Reply** pill badge next to their name. Populated automatically by the backend (`onScribbleCreate` Cloud Function) with no user action required.
- **Search:** filters across both sections simultaneously; matched results appear in their respective section headers.
- **Interaction:** multi-select toggle per contact; button label updates live ("Send to 1" → "Send to 3"). Selection UX is identical across both sections.
- **Deduplication:** if a user appears in both sections (e.g., they synced contacts *after* receiving a Scribble), they are shown only under "Your Contacts".
- **Empty state:** if both sections are empty → "No contacts yet. Invite friends to join Sketchly!"

### 4.4 Scribble Viewer
- **Purpose:** view one Scribble fullscreen, react to it, optionally block the sender.
- **Layout:** fullscreen doodle on paper background, sender name + timestamp at top, horizontal emoji reaction row pinned at bottom.
- **More menu (⋮):** Block User option — only shown if the viewer is NOT the current user’s own sketch.
- **Interaction:** optional stroke-by-stroke replay animation on first open (delightful, skippable by tap). Reaction row: tap an emoji to react, tap again to remove.
- **States:** loading (skeleton/paper placeholder), loaded, reaction-sent confirmation (subtle animation, not a modal).

### 4.5 Contact History Screen
- **Purpose:** view all Scribbles exchanged with one contact in a grid.
- **Header:** large initials-avatar circle (not a thumbnail), contact display name, scribble count + "since" label.
- **Body:** week-grouped 2-column grid (THIS WEEK / LAST WEEK / MMM YYYY); each card shows the sketch thumbnail + day-of-week chip.
- **More menu:** Block User option available from this screen.
- **Navigation:** reached by tapping an already-read Scribble in Dashboard. Self-tap (own profile) is silently skipped.

### 4.6 History
- **Purpose:** browse all past sent/received Scribbles.
- **Layout:** grouped by date section headers (Today / Yesterday / This Week / Earlier), loose grid of thumbnails (corkboard feel) rather than a strict list.
- **Interaction:** filter chip row at top (All / Sent / Received / by contact), tap thumbnail → Scribble Viewer.
- **Empty state:** "Nothing here yet" with illustration.

### 4.7 Circle Screen ("Your Circle")
- **Purpose:** show all users the current user is connected with.
- **V1 layout (unified, no tabs):**
  - Search bar at top — filters connections by name.
  - **SYNC CONTACTS** section: `SyncContactsCard` with sync status + button.
  - **YOUR CONNECTIONS · N** section: list of connected users (auto-formed via contact sync + scribble send).
  - Empty state: “Send someone a scribble — a connection forms automatically.”
- **V2 (when `HIDE_SUGGESTED_TAB = false`):** restores 3-tab layout — Suggested / Requests / Connected.

### 4.8 Home Screen Widget
- **Purpose:** primary passive touchpoint.
- **Layout (2x2):** sticky-note-style card, doodle thumbnail fills most of the space, sender name small at bottom.
- **Layout (4x2):** same content, wider aspect, slightly larger thumbnail.
- **States:** unread Scribble present (default), no new Scribbles (calm empty state — e.g., a subtle "all caught up" note, not a dead/blank widget), loading/stale (last-known content shown, never a blank white box).
- **Interaction:** tap → deep link directly into Scribble Viewer for that item.

### 4.9 Settings (supporting screen, not a nav tab)
- Widget content preview toggle (show doodle vs. sender name only)
- Notification toggle
- Contact sync management
- **Blocked Users** — list of blocked users with unblock action
- Account / logout

---

## 5. Core Components

| Component | Used in |
|---|---|
| **Note Card** | Inbox, History — thumbnail + metadata card with paper-card styling |
| **Avatar (circular)** | Inbox, Recipient Picker, Scribble Viewer |
| **Pen Toolbar** | Draw screen — color swatches, thickness stepper, undo/clear icons |
| **Reaction Chip Row** | Scribble Viewer |
| **Primary Button** (rounded, accent-filled) | Send, Confirm actions |
| **Section Header** | History date grouping |
| **Empty State Illustration + CTA** | Inbox, History |

---

## 6. Forms & Inputs

- **Sign-up:** phone number field (auto-formats), OTP 6-digit input with auto-advance, email/password fallback fields with standard validation (inline error text, not modal alerts).
- **Search (contacts/history):** single text field, live filter, no submit button needed.
- No other complex forms exist in MVP — the app is intentionally form-light.

---

## 7. Loading / Error / Empty States

| State | Treatment |
|---|---|
| **Loading (screen-level)** | Paper-texture skeleton shapes, not generic spinners, to stay on-brand |
| **Loading (send in-flight)** | Optimistic — Scribble appears immediately in sender's own view with a subtle "sending…" tag that clears on confirmation |
| **Error (send failed)** | Non-blocking inline tag on the item ("Couldn't send — tap to retry"), no disruptive modal |
| **Error (network offline)** | Small persistent banner at top of Inbox, dismissible, auto-hides on reconnect |
| **Empty (Inbox/History)** | Warm illustration + one-line copy + direct CTA button |
| **Empty (Widget)** | Calm "all caught up" note styling, never a blank/broken-looking widget |

---

## 8. Responsive Behavior

- Primary target: phone form factors (portrait). Landscape not prioritized for MVP (canvas remains usable but not specially optimized).
- Tablet: not explicitly designed for MVP; layout should not break (single-column content simply centers/constrains max-width) but no bespoke tablet layouts required.
- Widget sizes: support 2x2 (minimum) and 4x2; both must degrade gracefully (thumbnail scales, text truncates rather than overflows).

---

## 9. Accessibility

- Minimum touch target size: 48x48dp for all interactive elements.
- Color is never the sole indicator of state (e.g., unread uses a dot + bold text, not color alone).
- All icons have content descriptions for screen readers (TalkBack).
- Text contrast meets WCAG AA against the paper background for all UI chrome text (drawing content itself is user-generated and exempt).
- Support system font scaling up to at least 130% without breaking layout.
- Reaction emoji and sender names must be reachable and actionable via screen reader navigation order, not just touch.

---

## 10. Typography

- **UI chrome:** a rounded, friendly sans-serif (e.g., a geometric rounded typeface) for all system text — buttons, labels, timestamps, settings.
- **Never** simulate "handwriting" with a decorative font for UI text — that identity is reserved for actual user-drawn content, keeping a clear visual distinction between "app" and "you."
- Scale: consistent type scale (e.g., 12/14/16/20/24sp) applied consistently across screens — no ad hoc sizes.

---

## 11. Color & Spacing

- Palette: warm paper background, single primary accent, single secondary accent, ink-dark text color, muted neutral for borders/dividers (see the separate Color Palette prompt/output for specific hex options).
- Spacing system: 4dp base unit (4/8/12/16/24/32) applied consistently for padding/margins across all screens.
- Corner radius: consistently rounded (e.g., 16dp for cards, 24dp for buttons/sheets) to reinforce the soft, tactile tone — no sharp-cornered elements.

---

## 12. Motion & Interaction Details

- Send action: quick, satisfying "lift and fly" micro-animation of the note toward the recipient picker/confirmation, reinforcing the "instant" promise.
- Scribble Viewer: optional stroke-by-stroke replay, skippable, capped at ~1.5s regardless of original draw time so it never feels slow.
- Reaction: small bounce/pop animation on emoji select, no full-screen takeover.
- Widget refresh: no animation required (widgets have limited animation support) — content simply updates on next redraw.

This document is implementation-ready: every screen above maps directly to the composables and navigation graph defined in the Architecture and Development Plan documents.
