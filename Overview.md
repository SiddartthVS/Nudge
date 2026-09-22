# NUDGE — How the Codebase Works

This document explains the three main pieces of logic in the app, from top to bottom, in plain words. It reflects the code as it exists on disk right now.

---

## 1. Doomscroll Counter Logic

**Where it lives:** `TrackerService.java`, `ReelSignal.java`

This is the part of the app that actually watches the screen and decides "the user just scrolled to a new reel, count it." It runs as an Android **Accessibility Service** — a special kind of background service Android allows to read what's on screen, for apps like screen readers. It has nothing to do with React Native or JavaScript; it runs natively in Java, independently of whether the app itself is open.

### The core problem it solves

Android tells the service every time *anything* scrolls on screen (`TYPE_VIEW_SCROLLED`). If you counted every one of those events, the number would shoot up while the user is mid-drag, holding their finger on the screen without actually moving to a new reel — because that single drag fires many scroll events, not one. So "an event happened" is not the same as "the user watched a new reel," and the whole design of this piece is about telling those two things apart.

### The fix: read the screen, don't trust the event

Instead of trusting the event, the service reads a small piece of text that identifies *which reel is currently on screen* — normally the caption or the author's name — and only counts a reel when that text has genuinely changed. This logic was ported from an open-source project called Curbox and lives entirely in `ReelSignal.java`. It works differently for each app, because each app's screen is built differently under the hood:

- **Instagram:** reads the caption and author-username text.
- **YouTube:** reads the video's content area, then strips out fixed menu text (like "Home", "Shorts", "Subscriptions") that would otherwise always be present and drown out real changes.
- **Facebook:** has no reliable ID to search for, so it walks the screen's element tree looking for a specific label ("Reels tab details") and reads the text near it.
- **Snapchat:** reads its "spotlight" viewer area.

Once it has this text for the current reel and the text from the *previous* reel, it doesn't require an exact match — it compares how many words the two texts share. If less than 90% of the words overlap, that's treated as a genuinely different reel. This threshold is what absorbs a held or aborted scroll: if you drag partway and let go, the caption you're looking at hasn't actually changed, so the two texts are almost identical and nothing is counted.

There's also a small memory of the last 5 reels seen per app, so if you scroll back up to a reel you were already just on, it doesn't get counted twice.

### The full step-by-step flow

1. **An event arrives.** Android calls `onAccessibilityEvent()` with some event type and the package name of the app it came from.
2. **Is this a scroll/content-change event from one of the 4 monitored apps** (Instagram, YouTube, Facebook, Snapchat)? If not, and it's instead a "window changed" event (the user switched apps), that's handled separately, purely to decide whether to show or hide the on-screen counter (the HUD) — see the UI-adjacent note below.
3. **Confirm the app is really in the foreground.** Getting a real scroll event from an app is treated as solid proof that app is genuinely on screen right now — stronger evidence than Android's own "window changed" signal, which can be unreliable behind things like a fingerprint lock screen.
4. **Throttle.** To avoid doing expensive screen-reading work on every single event during a fast scroll, the service only actually re-reads the screen at most once every 120 milliseconds per app.
5. **Always schedule one more check, 250ms later.** This matters because the very last event of a swipe is often the one the throttle above skips — but that's exactly the event that fires once the new reel has actually loaded. Without this follow-up "settle" check, that reel would sometimes never get read at all, and the count would silently be too low.
6. **Read the screen** (via `ReelSignal.isNewReel()`) and get back a small identifying string for whatever reel is currently visible.
7. **Compare it to the last one seen.** If the text is genuinely different (less than 90% word overlap) and it isn't a reel that was already recently counted, this counts as a new reel.
8. **If it counts:** the in-memory number for that app goes up by one, immediately. The on-screen counter (HUD) is updated and made to flash — fade in, hold for 2 seconds, fade out. If another reel is counted while it's still visible, the fade-out timer just restarts, so fast scrolling doesn't cause flickering.
9. **Saving to disk is deliberately delayed** by 1.5 seconds after the last count, so a rapid burst of scrolling doesn't hammer the disk with writes — it just writes once, after things settle. (What happens to that saved data is the subject of Section 2.)

### A note on the on-screen counter (HUD)

The floating number the user sees is a small overlay window, drawn on top of whatever app is open, using Android's `TYPE_ACCESSIBILITY_OVERLAY` window type. It's kept *attached* to the screen for as long as a monitored app is in the foreground (attaching/detaching the whole window is what makes it reliably appear/disappear — toggling visibility on an already-attached view turned out not to be reliable). Within that, the number itself stays invisible until a reel is actually counted, at which point it flashes as described in step 8 above.

---

## 2. Data Storage Logic

**Where it lives:** `TrackerService.java` (today's live counter), `WeekHistoryStore.java` (on-device history), `SupabaseSync.java` (uploading), `SupabaseReader.java` (reading back), plus two SQL files under `supabase/migrations/`.

There are three layers of storage, each with a different job. Think of it as: **today's number → a short on-device memory of recent days → a permanent cloud copy.**

### Layer 1 — Today's live counter (SharedPreferences)

This is the fastest, simplest layer. Every app's count for *today* is kept as a small JSON object on the phone itself, in Android's `SharedPreferences` (a simple key-value store). It looks like:

```
{ "com.instagram.android": 214, "com.google.android.youtube": 37, ... }
```

This is what gets incremented the instant a reel is counted (Section 1, step 8), and it's what the on-screen HUD number always reflects.

### Layer 2 — Recent-day cache, also in SharedPreferences

Once a day is *finished* (see "Daily rollover" below), it's copied into a second small JSON object — a dictionary of `"date": {counts}` pairs — capped at the most recent **7 days**. This exists so the "This week" chart in the app always has something real and fast to read from the device itself, without needing the internet.

### Layer 3 — Supabase (the permanent cloud copy)

Everything older than what fits in the 7-day cache lives only in Supabase (a hosted Postgres database). This is the one part of the storage system that needs an internet connection and exists specifically so history isn't lost forever once it ages out of the on-device cache — and so that, down the line, the same account could be read from more than one device.

**Daily rollover — when a day "finishes":** the service checks the current date every time a reel is counted. The moment the date changes (or the very first time the service starts on a new day), the *previous* day's numbers are:
1. Copied into the 7-day on-device cache (Layer 2), and
2. Handed off to be uploaded to Supabase (Layer 3) — then the live counter (Layer 1) is reset to zero for the new day.

**Uploading is never allowed to block or lose data.** Before anything is sent over the network, the finished day is first written into a small "pending uploads" queue, also in SharedPreferences. Only once Supabase confirms the upload does that entry get removed from the queue. If the phone is offline, or Supabase is briefly down, the day just stays queued — nothing is lost — and it's automatically retried the next time a day rolls over, or the next time the service starts up.

**How the upload actually happens:** the native Java code talks directly to Supabase's REST API using a plain HTTP connection — it does *not* use the `supabase-js` JavaScript library that's installed in the project, because this whole tracking service runs independently of whether the JavaScript/React Native side of the app is even running. It calls a Postgres function called `upsert_daily_counts`, defined in `supabase/migrations/20260919000000_create_daily_scroll_counts.sql`. That function writes one row into a table called `daily_scroll_counts`, keyed by (device ID, date) — so uploading the same day twice simply overwrites it rather than creating a duplicate.

**Reading data back — for the "This week"/"Apps" charts and the Lifetime/Year/Month/Week/Today range picker:** a second Postgres function, `get_daily_counts`, lets the app ask "give me every day's counts for this device, from this date onward." When the app wants a total for a range (e.g. "this month"), the native code:
1. Fetches whatever it can from Supabase for that range,
2. Adds in anything still sitting in the pending-upload queue (in case it hasn't synced yet),
3. Adds in today's still-live counter,
4. Adds all of that together into one combined total per app.

If Supabase can't be reached, it falls back to using just the on-device 7-day cache instead, and marks the result as "incomplete" so the app knows the number might not be the full picture.

**Security of the cloud data:** the table is locked down using Postgres Row-Level-Security with *no* read/write policies at all — meaning the public key baked into the app cannot read or write the table directly, under any circumstances. The only way in or out is through the two named functions above, which are marked `SECURITY DEFINER` (they run with elevated rights) and do their own basic validation. Each install of the app is assigned a random ID (a UUID) the first time it runs, which is what ties all of one device's rows together in the table — there's no login system yet.

> **⚠️ Something worth knowing about the current state of the code:** `SupabaseSync.getDeviceId()` currently always returns the fixed text `"TEST_DEVICE_DO_NOT_USE"` instead of the real per-install random ID — the real ID is still generated and saved, but the function ignores it and returns the test value regardless. This was set up earlier for testing with seeded fake data. Right now, **every install of the app is reading and writing under that one shared test ID**, rather than each phone getting its own private data. This needs to be reverted (return `id` instead of the hardcoded string) before this is used for real.

### A caching layer on top, in JavaScript (`rangeCache.ts`)

This isn't storage in the sense of *saving* anything — it's a short-lived, in-memory cache on the JavaScript side that avoids re-asking Supabase for the same range (Today, This week, This month, This year, Lifetime) over and over. Each range has its own "freshness window" (Today refreshes every 5 seconds, Lifetime every 5 minutes), so switching between them in the app feels instant, while the more expensive ranges aren't re-fetched unnecessarily. This cache clears itself whenever the app fully restarts — it's not written to disk.

---

## 3. UI Logic

**Where it lives:** `App.tsx`, `screens/PermissionsScreen.tsx`, `screens/HomeScreen.tsx`, `screens/components/`

This part is much simpler — it's just React Native screens showing data that the two systems above already produce.

- **`App.tsx` decides which screen to show.** On launch, it checks all three required permissions (overlay, accessibility, battery). If all three are already granted, it goes straight to the Home screen. Otherwise, it shows the Permissions screen first.
- **The Permissions screen** shows a progress tracker and three cards (Display over apps, Accessibility service, Run in background), each with an "Allow" button that opens the right Android settings page. It only moves on to the Home screen when the user taps the "Nudge!" button themselves — it doesn't jump forward automatically the instant the last permission is granted.
- **The Home screen** is just three cards stacked on top of each other:
  - **CountDisplay** — the big number at the top. It has two rows of pills: pick a time range (Today, This week, This month, This year, Lifetime) at the top, and pick an app (or "All") at the bottom. Whichever two are selected decide what number is shown.
  - **WeekStats ("This week")** — a simple bar chart of the last 7 days, always showing all apps combined.
  - **AppStats ("Apps")** — a horizontal bar chart comparing the 4 apps against each other, with its own small dropdown to pick the same kind of time range as CountDisplay.

All three cards get their numbers the same general way: ask the shared caching layer from Section 2 for a range, and re-render whenever new data comes back. None of these components contain any counting or storage logic themselves — they're purely display.