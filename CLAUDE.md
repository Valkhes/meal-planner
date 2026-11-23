# Meal Planner

A personal meal-planning assistant, Android first, everything on the phone. The README is the
specification: read its constraints and "Architecture" before any change.

## Language
Everything in the repository is English: code, comments, docs, commits.
User-facing text is never a literal in code. Shared UI strings go in
`ui/src/commonMain/composeResources/values/strings.xml` and its `values-fr/` twin; Android-only
strings (notifications) in `androidApp/src/main/res/values/` and `values-fr/`. Every English string
has its French one with the same placeholders: `tools/lint/translations.py` and Android lint fail
otherwise. User data (dish names) is never translated.

## Commits
Commit as the owner: `git config user.name "Valkhes"` and
`git config user.email "20518397+Valkhes@users.noreply.github.com"` (the SessionStart hook in
`.claude/settings.json` sets it; check it before the first commit). The agent's co-author line stays
in the message. The owner allows pushing straight to `main`.

## Commands
- Check everything (translations, doc paths, ktlint, every JVM test, architecture rules, Android
  lint, debug APK, APK permissions): `bash tools/check.sh`. Run it after any change, before saying
  a task is done. CI runs it on every push (`.github/workflows/checks.yml`).
- Fast loop: `./gradlew jvmTest` (domain, application, infrastructure and architecture tests).
- Format: `./gradlew ktlintFormat`.
- Install on a phone: `./gradlew :androidApp:installDebug` (`adb devices` first).
- Android SDK: `ANDROID_HOME`, else `local.properties` (`sdk.dir=...`, written by the session hook
  when `/opt/android-sdk` exists).

## Architecture rules (why: README, "Architecture")
- Kotlin Multiplatform. The core is in `commonMain` and must stay portable for iOS later: no
  `java.*`, `javax.*`, `android.*` there (`ArchitectureTest` checks it, since no iOS target is
  built: the owner does not want iOS in CI). Platform code goes in `androidApp` or a platform
  source set.
- Modules point inwards: `androidApp` -> `ui`, `infrastructure` -> `application` -> `domain`.
  The domain imports only `kotlin.*`, `kotlinx.datetime.*` and itself.
- Every change is a command sent to `MealServer` (skill `new-command`). The UI never changes state
  itself, and reads only through `MealServer`.
- The event log is the only data kept for good. A released event version is never edited: a change
  is a new version with an upcaster, and every version has a golden (skill `new-event-version`).
  Facts are corrected by new events, never by rewriting.
- The read state is rebuilt from the log at every start. It is never saved, so its shape changes
  freely: no migration.
- Time comes from `Clock`, ids and randomness from `IdGenerator`: only `infrastructure` reads the
  system.
- Bounded contexts (`journal`, `cookbook`, `habits`, `planning`, `sync`, `assistant`, plus `events`, `server`,
  `shared`) use only what `ALLOWED` in `ArchitectureTest` lists. A new edge is a design decision:
  say so to the owner. New context: skill `new-context`.
- If `ArchitectureTest` fails, fix the dependency; don't loosen the test.

## Product rules (README, constraints)
- Nothing leaves the phone: no analytics, crash reporting, Firebase or any library that calls home.
  The INTERNET permission exists only for the sync on the local network: only
  `androidApp/src/main/kotlin/dev/valkhes/mealplanner/sync/` may use the network, and only private
  addresses (`ArchitectureTest`). `tools/lint/apk_permissions.py` fails on a new permission; adding
  one to its list is the owner's decision.
- No tracking chores: the only input is "we ate this dish". No inventory, no streaks, no nagging;
  the evening reminder is the only nudge.
- The app works fully without AI; an interpreter only proposes, the user confirms.

## Nothing stays that is not used
A change that replaces something deletes what it replaced in the same commit: code, tests, strings
and its sentence in the docs. Docs say what is true now: `tools/lint/doc_refs.py` fails on a path
they name that is gone.

## Skills
`new-command`, `new-event-version`, `new-context`, `new-screen`, `kotlin-conventions`.
