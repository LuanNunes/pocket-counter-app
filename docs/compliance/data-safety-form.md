# Play Console — Data Safety form answers

Answers to transcribe into **Play Console → App content → Data safety**.
Items marked `[CONFIRM]` depend on backend facts — verify before submitting.
Inaccurate Data Safety answers are themselves a policy violation, so err on the
side of declaring.

## Overview questions

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (TLS) |
| Do you provide a way for users to request that their data is deleted? | **Yes** (in-app account deletion + web/email channel — must exist before submitting) |

## Data types collected

### Personal info
- **Name** — Collected. Purpose: Account management. Not shared. Not optional.
- **Email address** — Collected. Purpose: Account management. Not shared. Not optional.

### Financial info
- **Other financial info** (user-entered transactions, categories, cards, invoices)
  — Collected. Purpose: App functionality. Not shared. Not optional (core feature).
- **Purchase history** — declare only if notification capture stores merchant purchase
  records; captured bank-notification transactions arguably are this. **Recommended: declare**,
  Purpose: App functionality. Not shared. Optional (feature requires opt-in permission).

### Messages
- **Other in-app messages** — the safest bucket for **notification content read from
  other apps** is "Other user-generated content" or this one; Google has no perfect
  category for notification listeners. **Recommended: declare under
  "App activity → Other user-generated content"**: Collected. Purpose: App
  functionality. Not shared. Optional (opt-in via notification-access permission).

### App activity
- **Other user-generated content** — see above (notification text). Declare here.

### NOT collected (leave unchecked)
- Location, Contacts, Photos/Videos, Audio, Health, Web browsing, Calendar,
  App interactions/diagnostics `[CONFIRM: no analytics/crash SDK in app — verified
  no Firebase/Crashlytics dependency in app/build.gradle.kts as of 2026-07-02]`.

## Data handling declarations

- **Ephemeral processing:** No (notification content is stored server-side associated
  with the account) `[CONFIRM]`.
- **Data shared with third parties:** None (hosting provider is a processor, not
  "sharing" under Play's definition).
- **Deletion:** account deletion removes personal + financial data
  `[CONFIRM: backend endpoint + retention window]`.

## Related Play Console items (same section, different forms)

- **Privacy policy URL:** must be live before submitting — host
  `docs/compliance/politica-de-privacidade.md` at e.g.
  `https://pocket-counter.com/privacidade`.
- **Sensitive permission declaration:** the app must declare its use of the
  Notification Listener permission when prompted; the justification is the prominent
  in-app disclosure + core-feature use (financial transaction detection).
- **Financial features declaration:** Play Console asks whether the app provides
  financial features (loans, banking). PocketCounter is a **personal expense tracker**
  — it does not handle money movement; answer accordingly (typically "My app doesn't
  provide any financial features").
- **Account deletion URL:** Play requires a **web** link where users can request
  account deletion without reinstalling the app.
