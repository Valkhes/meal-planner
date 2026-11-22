# Meal Planner

A personal assistant for planning meals. It learns what a household
actually eats, answers "what do we eat tonight?" in about 30 seconds based
on the time available, and grows into weekly planning, a shared shopping
list and help finding new recipes to try.

It is an Android app that keeps **everything on the phone**: no server, no
account, no cloud. Two phones in the same household sync over the local
network.

**Status: design.** No code yet. This README is the specification.

## Constraints (non-negotiable)

1. **Nothing leaves the phone.** No server, no account, no cloud, no
   analytics, no crash reporting, no third-party library that calls home.
   In practice:
   - no Google, Firebase or analytics SDK;
   - network traffic limited to local network addresses;
   - Android Auto Backup disabled, since it would otherwise copy the app's
     data to the Google cloud;
   - data leaves the device only through an explicit export by the user.
2. **Sync between two phones on the same local network only.** A
   "Synchronize" button runs it when both phones are on the same Wi-Fi.
   Traffic is encrypted with a household key created once by pairing with
   a QR code, and only phones of that household are accepted.
3. **Shareable as is.** Anyone can install the APK. Each household is
   independent and nobody has anything to host.
4. **No tracking chores.** The only input is "we ate this dish", in under
   10 seconds. No inventory, no "what's left in the fridge", no streaks.
   Gaps are normal: a week without input is fine.
5. **Habits come from real frequency**, never from what people declare
   they like. The app has a break-in period of a few weeks before its advice
   is worth anything.
6. **The engine is separate from the interface.** Every action is a
   documented, typed command. The UI is the first client. A local AI agent
   (or, one day, an MCP tool) will be another client of the same commands,
   with nothing duplicated.
7. **Any AI is 100% local** and optional: the app works fully without it.

## How it works

### The data: an append-only event log

Every action is an event, for example `MealLogged`, `RecipeEdited` or
`AbsenceSet`. Each event has a unique id, a device id and a timestamp, and
is never modified. This makes sync trivial and safe:
- **Merging two phones** means taking the union of their events: nothing
  is lost and the order does not matter.
- **A recipe edited on both phones**: the most recent edit wins, field by
  field.
- **A dinner both people logged** (same dish, same day, same meal slot)
  counts as one meal, so frequencies are not doubled.

The current state (dishes, frequencies, recipes) is computed from the log.

### The engine: `MealServer`

The UI only talks to a `MealServer` interface, which accepts commands and
returns results. Today it is `LocalMealServer`: a local database on the
phone (SQLite via Room). The rules (frequency, typical interval, what is
due) are pure functions, tested separately.

### Sync

1. **Pairing, once:** one phone shows a QR code, the other scans it. They
   now share a household id and an encryption key.
2. **Sync:** with both phones on the same Wi-Fi, press "Synchronize". The
   phones find each other on the local network, exchange the events the
   other is missing, and merge.
3. **Fallback:** the shopping list can be sent as plain text through the
   Android share menu, for when one person is at the store and the other at
   home.

## Features, built as the data accumulates

### v0: log and see (start here)

Three screens:
1. **"We ate…"**: type the dish name (autocomplete on dishes already
   logged), the date defaults to today, confirm. Under 10 seconds.
2. **Dishes**: every dish with how many times, last time, and typical
   interval ("about every 9 days"), sorted by frequency.
3. **Dish page**: its recipe, written by the user (ingredients, steps,
   time) and editable, plus the dates it was eaten.

The v0 runs on one phone. The event log is there from day one, so sync can
be added later with no migration.

### Next, in order

- **Sync between two phones** (pairing by QR code, local network, button).
- **"Tonight"**: given the time available (15 / 30 / 45 / 60+ min), three
  dishes that are due according to the household's rhythm and fit in that
  time. Each comes with the reason ("not made for 12 days, usually every
  10"). Learned patterns include weekdays ("pizza on Fridays") and each
  dish's real cooking time.
- **Leftovers** ("lasagne left from yesterday, 0 min") at the top of
  "Tonight".
- **Absences** ("away from the 3rd to the 10th"), so the rhythm is not
  skewed and the plan skips those days.
- **Week plan** in one tap, editable, and a **shared shopping list**
  generated from the plan, grouped by aisle.
- **Recipes to try**: a "to try" list. "Tonight" offers one of them now
  and then, when there is time. A recipe that gets made again joins the
  regular rotation; one that never does fades out.
- **Dish recognition from a photo, on the phone, without an LLM.** A small
  vision model (MobileCLIP or MobileNet class) turns each photo into an
  embedding. The app suggests the three closest dishes among the
  household's own, and every confirmation adds an example. For a dish never
  seen before, an image-text model suggests a name from a list of common
  dishes. The recipe is never guessed from the photo: it comes from the
  dish page.
- **A local AI agent** (Gemma 3n via MediaPipe LLM Inference, or Gemini
  Nano where the system offers it). It turns plain sentences into commands
  ("we made a curry tonight"), explains suggestions, structures a recipe
  pasted as text, and answers "find something close to what we like, under
  30 min" by calling the engine. The engine decides; the agent understands
  and explains. It needs a recent phone (6-8 GB of RAM) and a 1-3 GB model,
  so it is optional.

## Open decisions

- **Importing a recipe from a link.** Most recipe sites embed a
  machine-readable recipe (schema.org/Recipe), so a link could be imported
  without AI. But fetching the page contacts the internet: no personal data
  is sent, yet the site sees the IP address and the page. Allow it only on
  an explicit user action, or leave it out.
- **Getting the AI model onto the phone**: a one-time download the user
  starts, from a known source, or a file copied by hand.

## Technology

Native Android: Kotlin, Jetpack Compose, Room (SQLite). The rules are
plain Kotlin with unit tests on the JVM. Sync and the AI agent come later.
