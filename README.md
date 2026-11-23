# Meal Planner

A personal assistant for planning meals. It learns what a household
actually eats, answers "what do we eat tonight?" in about 30 seconds based
on the time available, and grows into weekly planning, a shared shopping
list and help finding new recipes to try.

It is an Android app that keeps **everything on the phone**: no server, no
account, no cloud. Two phones in the same household sync over the local
network.

**Status: v0 in development.** This README is the specification.
`CLAUDE.md` holds the working rules, and `bash tools/check.sh` checks
everything (CI runs it on every push).

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
   The only nudge is the evening reminder (below), sent only when nothing
   was logged, and answerable from the notification itself. Gaps are
   normal: a week without input is fine.
5. **Habits come from real frequency**, never from what people declare
   they like. The app has a break-in period of a few weeks before its advice
   is worth anything.
6. **The engine is separate from the interface.** Every action is a
   documented, typed command. The UI is the first client. A local AI agent
   (or, one day, an MCP tool) will be another client of the same commands,
   with nothing duplicated.
7. **Any AI is 100% local** and optional: the app works fully without it.
8. **English first, French native.** Everything in the repository is in
   English: code, comments, docs, commits. The app's default language is
   English, and it is fully in French when the phone is set to French.
   - **Texts:** every user-facing text is an Android string resource,
     never a literal in code. English goes in `values/`, French in
     `values-fr/`. A missing French translation fails the build (lint
     `MissingTranslation` treated as an error).
   - **Language:** the app follows the phone's language, and Android's
     per-app language setting (Android 13+) can override it.
   - **Formats:** dates, times, numbers and units are formatted for the
     current locale.
   - **User data is never translated:** a dish called "gratin dauphinois"
     keeps its name.
   - **Every interpreter understands both languages.** The rules know the
     English and French words for time ("tonight" / "ce soir",
     "yesterday at lunch" / "hier midi", "from the 3rd to the 10th" /
     "du 3 au 10") and for units ("tbsp" / "c. à soupe", "cup", "g",
     "cl"). An AI interpreter answers in the language you used.

## How it works

### Data that survives iteration

The app will change a lot. **Changing it must never mean converting or
re-saving the stored data.** That is why the only thing kept for good is
the event log. Everything else is derived from it and can be thrown away.

**The event log is the source of truth.** Every action is an event, for
example `MealLogged`, `DishRenamed`, `RecipeEdited` or `AbsenceSet`. An
event is stored once and never modified. Its envelope never changes:

| Field | Content |
|---|---|
| `id` | UUIDv7: unique and sortable by time, generated on the phone |
| `type` | the event name, e.g. `MealLogged` |
| `version` | the version of that event's schema, from 1 |
| `householdId`, `deviceId` | which household, which phone |
| `occurredAt` | when it happened (the meal), as the user says |
| `recordedAt` | when the phone recorded it |
| `payload` | the event's content as JSON |

**Everything else is a projection**, a read model rebuilt from the log:
- the dish list;
- frequencies and intervals;
- the shopping list;
- whatever a future feature needs.

The read state is never saved: the app replays the log into memory at
every start. A few years of meals is a few thousand events, so this takes
well under a second. Its shape can change with every release, freely. If
startup ever gets slow, a projection can be cached on disk with a version
number, dropped and rebuilt whenever its code changes. **Adding a feature
means adding a projection, never migrating data.**

**How event schemas evolve**, so that old events stay readable forever:
- **A released event type is never edited.** Adding an optional field with
  a default is allowed in place.
- **Any other change makes a new version** (`MealLogged` v2) together with
  an *upcaster*: a pure function that turns v1 into v2 when the log is read.
  Old events stay untouched on disk, and the code only ever sees the latest
  version.
- **Facts are corrected by new facts, never by rewriting.** Renaming a dish
  is `DishRenamed`, merging two dishes is `DishesMerged`, a wrong log is
  `MealRetracted`. History is kept, and the meaning comes from replaying it.
- **Every event version ever released has a golden test**: a real stored
  sample that must still upcast and replay to the expected state. Breaking
  compatibility makes the build fail.

This also makes sync safe:
- **Merging two phones** means taking the union of their events: nothing
  is lost and the order does not matter.
- **A recipe edited on both phones**: the most recent edit wins, field by
  field.
- **A dinner both people logged** (same dish, same day, same meal slot)
  counts as one meal, so frequencies are not doubled.

### Sync

1. **Pairing, once:** one phone shows a QR code, the other scans it. They
   now share a household id and an encryption key.
2. **Sync:** with both phones on the same Wi-Fi, press "Synchronize". The
   phones find each other on the local network, exchange the events the
   other is missing, and merge.
3. **Fallback:** the shopping list can be sent as plain text through the
   Android share menu, for when one person is at the store and the other at
   home.

### The input box and its interpreter

The app has **one free-text box**. You can type a sentence ("we had a
curry tonight", "leftover lasagne, then a salad", "away from the 3rd to the
10th") or paste a whole recipe copied from a site. The text goes through an
`Interpreter`, which turns it into engine commands (`LogMeal`,
`CreateRecipe`, `SetAbsence`...). The app shows the result, and nothing is
saved until you confirm it.

The interpreter is interchangeable, and the app uses the best one the phone
has:
1. **`NanoInterpreter`**: Gemini Nano, through ML Kit's Prompt API on top
   of AICore, the Android system service. The system manages the model:
   on recent Pixels it is usually already on the phone, and elsewhere the
   system downloads it once. The APK stays small. It is only available on
   recent, mostly high-end phones (Pixel, recent Galaxy S, a few others).
2. **`GemmaInterpreter`**: a Gemma model file run inside the app (LiteRT-LM
   or llama.cpp), fully offline, on any phone with enough memory (6-8 GB of
   RAM, a 1-3 GB model installed once).
3. **`RuleInterpreter`**: no AI and always there. Simple, tested rules
   still understand most input:
   - a known dish name, with words like "tonight", "yesterday", "at lunch";
   - "from the 3rd to the 10th" as an absence;
   - for a pasted recipe, lines with quantities as ingredients, numbered
     lines as steps, and "30 min" as the time.

   The v0 ships with this one.

**Picking the interpreter, and getting Nano onto the phone:**
- **The app cannot install AICore.** It is part of the system, installed
  and updated by Android and the Play Store. If it is missing, the phone is
  not supported. If it is there but out of date, the app can at most open
  its Play Store page.
- **Never at startup.** Startup stays instant. The check runs in the
  background, and the rules serve in the meantime.
- **What the app does with ML Kit's answer:**
  - `AVAILABLE`: use Nano.
  - `DOWNLOADABLE`: **ask the user first** ("download the assistant, about
    X GB, on Wi-Fi?"), then ask ML Kit to download. AICore downloads the
    model once, for the whole phone. Never silently.
  - `DOWNLOADING`: rules meanwhile, then Nano once the model is ready.
  - `UNAVAILABLE`: Gemma if its model file is installed, otherwise the
    rules, with no error message.
- **Check again from time to time** (once a day), since a system update can
  make Nano available later.

Whatever the interpreter, the engine decides (frequencies, rhythms, time)
and the interpreter only understands and explains. An AI interpreter talks
to the engine through the same commands as the UI, which are its tools.

### Ready for an online database, if ever needed

Today everything stays on the phone (constraint 1). The design still makes
an online database a matter of **adding an adapter, not a rewrite**,
should the owner one day decide to amend that constraint.

**Rules kept from day one**, so the local data needs nothing to go online:
- **Ids are generated on the phone** (UUIDv7), never auto-incremented by
  the database: no id clashes when several stores meet.
- **Every event carries its `householdId` and `deviceId`**, so the same
  table can hold many households and phones.
- **Times are stored in UTC, with the user's time zone kept apart**, so the
  same data means the same thing on any machine.
- **Payloads are plain JSON**, readable by Postgres (`jsonb`) as well as
  SQLite.
- **No SQLite-specific logic outside the SQLite adapter**: the domain and
  application layers only know the `EventStore` port.
- **The sync cursor is per device**: "the events of phone X after its
  sequence number N". The same exchange works with a phone or a server.

**What going online would add:**
1. A `RemoteEventStore` adapter, or an online **sync peer** that stores
   events and speaks the same sync protocol as a phone. An append-only
   events table maps directly to Postgres (Neon or Supabase).
2. **End-to-end encryption with the household key**, which already exists
   for local sync. The server would only see ciphertext and envelopes, so
   meals and recipes would still never leave the phone in readable form.
3. Nothing else: projections, rules, the UI and the interpreters stay as
   they are. The phone keeps its full local copy and keeps working offline.

## Architecture

Clean Architecture with domain-driven design. **Dependencies only point
inwards**: the domain knows nothing about Android, the database, the
network or AI.

```
androidApp (Android entry point, notifications, composition root)
        |
ui (Compose Multiplatform screens)    infrastructure (SQLite event store,
        |                              |  rule interpreter, system clock;
        |                              |  later LAN sync, Nano and Gemma)
        |                              |  implements the ports below
application (commands and queries = use cases, the read state, ports:
        |    EventStore, Clock, IdGenerator, Interpreter)
        |
domain (pure Kotlin: aggregates, value objects, domain events, rules)
```

**One Gradle module per layer** (`:domain`, `:application`,
`:infrastructure`, `:ui`, `:androidApp`). The compiler enforces most of the
dependency rule: `:domain` cannot import Android, because it does not
depend on it. `ArchitectureTest` checks the rest: common code stays
portable, only `infrastructure` reads the system clock, and each context
uses only the contexts it is allowed to.

**Bounded contexts.** Each is a package in every layer and owns its own
events and language. A context talks to another only through its
application API or its events, never through its internals.

| Context | What it owns |
|---|---|
| **Journal** | meals eaten (`MealLogged`, `MealRetracted`) |
| **Cookbook** | dishes and their recipes (`DishCreated`, `DishRenamed`, `DishesMerged`, `RecipeEdited`) |
| **Habits** | read side only: frequency, typical interval, "Tonight"; later weekday patterns and real cooking time |
| **Planning** | absences (`AbsenceSet`, `AbsenceRemoved`); later the week plan |
| **Shopping** | the shared list |
| **Household** | phones, pairing, sync |
| **Assistant** | interpreters: text to commands |

**CQRS:**
- A **command** (`LogMeal`, `EditRecipe`...) is validated by its aggregate
  and appends events.
- A **query** (`Dishes`, `Tonight`...) reads projections.

`MealServer` is the application's single entry point. It exposes these
commands and queries to the UI today, and as tools to an AI interpreter or
an MCP server later. Today its implementation is `LocalMealServer`.

**Ports and adapters.** Everything outside the domain sits behind an
interface defined by the application: storage, time, ids, interpreters,
sync. Swapping SQLite for something else, or the rules for Nano, changes one
adapter and nothing else.

**Testing:**
- domain rules: plain unit tests on the JVM;
- use cases: in-memory adapters;
- compatibility: the event golden tests above;
- behaviour: replays of a simulated household's month must give the
  expected suggestions.

## Features, built as the data accumulates

### v0: log and see (start here)

Three screens:
1. **The input box**: type "curry tonight", "pasta yesterday at lunch",
   or paste a whole recipe (see below). The app shows what it understood
   (autocomplete on dishes already logged, the date defaults to today) and
   you confirm. Under 10 seconds for a meal.
2. **Dishes**: every dish with how many times, last time, and typical
   interval ("about every 9 days"), sorted by frequency.
3. **Dish page**: its recipe, written by the user (ingredients, steps,
   time) and editable, plus the dates it was eaten.

Plus **the evening reminder**, since the whole app depends on meals being
logged:
- **When:** at the time set in Settings (21:00 by default), and only if no
  dinner was logged that evening. There is no reminder during absences,
  and none on a day marked "eating out".
- **Answer in the notification**, without opening the app:
  - buttons with the two most likely dishes (the ones that are due, or the
    last ones made);
  - "Other…", a text field sent to the interpreter ("leftover curry");
  - "Not at home", which records nothing and does not count as a gap.
- **Never insistent:** one notification per evening, no follow-up. After a
  week of reminders ignored in a row, it suggests lowering the frequency,
  or turning it off, instead of pushing harder.
- **Local only:** scheduled on the phone (WorkManager and an exact alarm
  for the time), with no push service. Firebase Cloud Messaging would go
  through Google's servers.
- **With two phones** (once sync exists): each phone reminds its owner. If
  both answer, the two logs count as one meal, as for any dinner logged
  twice.

The v0 runs on one phone. The event log is there from day one, so sync can
be added later with no migration.

### Built since

- **"Tonight"**, at the top of the home screen. Pick the time available
  (15 / 30 / 45 min or any) and get three dishes. They are the most due
  against the household's own rhythm, they fit in that time (a dish
  without a known time always fits), and none was eaten today. Each comes
  with its reason ("not made for 12 days · about every 10 days"). A tap
  prepares tonight's log, to confirm. During the break-in (fewer than 10
  meals logged), it says so and offers the most frequent dishes.
- **Absences.** Set them in Settings, or type "on part du 3 au 10" /
  "away from the 28th to the 2nd" in the input box. Days away do not count
  in the rhythms, and there is no reminder on those days.
- **The evening reminder's settings**: on or off, and its time in steps
  of 15 minutes. These are settings of the phone, not household data.

### Next, in order

- **Sync between two phones** (pairing by QR code, local network, button).
- **Leftovers** ("lasagne left from yesterday, 0 min") at the top of
  "Tonight".
- **Learned patterns for "Tonight"**: weekdays ("pizza on Fridays") and
  each dish's real cooking time.
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
- **A conversational local agent** on top of the input box (see "The
  input box and its interpreter"). It talks with you: it asks when
  something is ambiguous ("the green curry or the chicken one?"), explains
  suggestions, and answers "find something close to what we like, under
  30 min" by calling the engine.

## Open decisions

- **Importing a recipe from a link.** Most recipe sites embed a
  machine-readable recipe (schema.org/Recipe), so a link could be imported
  without AI. But fetching the page contacts the internet: no personal data
  is sent, yet the site sees the IP address and the page. Allow it only on
  an explicit user action, or leave it out.
- **Gemini Nano and constraint 1.** Inference with Nano runs on the phone,
  so meals and recipes are not sent anywhere. But ML Kit, as far as its
  privacy terms go (to be checked), sends usage and performance metrics to
  Google. That is not content, but it does leave the phone. Accept that
  trade-off for Nano's convenience, or use only Gemma and the rules, which
  send nothing.
- **Getting a Gemma model onto the phone**: a one-time download the user
  starts, from a known source, or a file copied by hand.

## Technology

**Kotlin Multiplatform**, Android first, iOS possible later:
- The domain, the application, the infrastructure and the screens are
  written in `commonMain` source sets, so they can compile for iOS one day.
- No iOS target is built for now, locally or in CI (owner's decision).
  `ArchitectureTest` keeps common code free of JVM and Android APIs in the
  meantime.
- On iOS, only the platform adapters would be written again: notifications,
  and the on-device AI (Apple's Foundation Models behind the same
  `Interpreter` port).

Libraries:
- **Compose Multiplatform** (Material 3) for the screens, with its
  resources for the English and French strings;
- **androidx.sqlite** with its bundled SQLite driver for the event store;
- **kotlinx-datetime**, **kotlinx-coroutines** and
  **kotlinx-serialization** (JSON payloads, written by hand-coded codecs);
- **WorkManager** for the evening reminder.

Checks:
- ktlint;
- `kotlin.test` on the JVM;
- Android lint, with a missing French string as an error;
- scripts in `tools/lint/` for translations, doc paths and the APK's
  permissions.

All of them run through `tools/check.sh`.

Sync and the AI interpreters come later. A first test will measure Nano and
Gemma on real phones: speed, and quality on pasted recipes and everyday
sentences.
