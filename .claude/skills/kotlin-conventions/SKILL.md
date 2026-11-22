---
name: kotlin-conventions
description: Kotlin style and pitfalls for this project - ktlint official style, portable commonMain, immutability, value classes, coroutines, time, tests. Use when writing or reviewing any .kt file.
---

# Kotlin conventions

- **Style**: ktlint official (`.editorconfig`), checked by `tools/check.sh`; `./gradlew ktlintFormat` fixes most of it. Lines up to 160 characters.
- **Portable core**: in `commonMain`, only Kotlin multiplatform libraries (`kotlinx.datetime`,
  `kotlinx.coroutines`, `kotlinx.serialization`, `androidx.sqlite`, Compose Multiplatform). No
  `java.*`, `android.*`, `System`, `String.format`: `ArchitectureTest` fails.
- **Time and randomness**: through `Clock` and `IdGenerator`, never `Clock.System`,
  `TimeZone.currentSystemDefault()` or `Random.Default` outside `infrastructure`. `kotlin.time.Instant`
  needs the `kotlin.time.ExperimentalTime` opt-in, set per module in Gradle.
- **Types**: ids are `@JvmInline value class`; domain state is immutable (`apply` returns a new
  state); expected failures are values (`Decision.Rejected`, `CommandResult.Refused`), not
  exceptions. Avoid `!!` except right after a check that makes it safe.
- **Coroutines**: every `MealServer` call is `suspend`; the server serializes with a `Mutex`.
  Blocking I/O runs on the dispatcher handed to the adapter (`Dispatchers.IO` on Android).
- **Comments** say why, not what. KDoc on every public command, port and rule.
- **Tests**: `kotlin.test` in `commonTest` (run on the JVM by `./gradlew jvmTest`), names in
  snake_case sentences (`a_retracted_meal_no_longer_counts`), `runTest` for suspend code, the fakes
  in `application/src/commonTest/kotlin/dev/valkhes/mealplanner/application/Fakes.kt` (`FakeClock`, `InMemoryEventStore`, `SequentialIds`).
