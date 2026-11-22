---
name: new-command
description: Adds a user action that changes data (a Command) end to end - the command, the domain decision, the events, the server routing, UI error strings and tests. Use whenever something must be recorded (log, rename, plan, set an absence...).
---

# Adding a command

Every change is a command sent to `MealServer`. The UI sends them today; an AI interpreter or an
MCP tool will send the same ones later, so a command is a small, explicit, documented action.
Reference: `LogMeal` and `RetractMeal`.

1. **The command**: a `data class` in `application/src/commonMain/kotlin/dev/valkhes/mealplanner/application/server/Command.kt`,
   implementing `Command`, with a KDoc sentence saying what it does (it becomes the tool description).
   Fields are domain types (`DishId`, `LocalDate`, `MealSlot`) or plain strings the domain validates.
2. **The decision** in the domain aggregate of the context that owns the data (`Cookbook`,
   `Journal`, or a new one: skill `new-context`): a function returning `Decision<...Event>`.
   It validates everything (treat input as hostile: it may come from an interpreter), returns
   `Decision.Rejected(DomainError.X)` for expected failures, never throws, and returns no event
   when nothing changes.
3. **New events?** Follow `new-event-version`. Add the event's `apply` branch to its aggregate:
   `apply` never refuses, since events may come from the other phone.
4. **Route it** in `LocalMealServer.decide`. The `when` is exhaustive: the compiler shows where.
5. **Errors**: a new `DomainError` gets a stable `code` and a translated message in
   `ui/src/commonMain/kotlin/dev/valkhes/mealplanner/ui/common/Texts.kt` (`ERRORS`) plus `error_<code>` strings in `values/` and `values-fr/`.
6. **Tests**: the decision in `domain/src/commonTest` (each refusal, the events, no event when
   nothing changes); the round trip through `LocalMealServer` in
   `application/src/commonTest/kotlin/dev/valkhes/mealplanner/application/LocalMealServerTest.kt` (state after a restart included).
7. `bash tools/check.sh`.
