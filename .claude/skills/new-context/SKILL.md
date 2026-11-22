---
name: new-context
description: Creates a new bounded context (planning, shopping, household...) - its domain package, events, application package, read state, server routing, allowed dependencies and tests. Use when a feature does not belong to journal, cookbook, habits or assistant.
---

# Creating a bounded context

A context is one thing told in a sentence, with its own events and words (README, "Bounded
contexts"). Reference: `cookbook`.

1. **Decide its dependencies first**: which contexts must it read? Keep it minimal and acyclic.
   Add it to `ALLOWED` in `domain/src/jvmTest/kotlin/dev/valkhes/mealplanner/architecture/ArchitectureTest.kt`
   (and allow `server` to use it). A new edge is a design decision: tell the owner.
2. **Domain** (`domain/src/commonMain/kotlin/dev/valkhes/mealplanner/domain/<context>/`): its
   sealed `<Context>Event : DomainEvent`, its events, and an immutable aggregate with decisions
   returning `Decision` and an `apply(event)` that never refuses.
3. **Application** (`application/src/commonMain/kotlin/dev/valkhes/mealplanner/application/<context>/`):
   `<Context>Events` with one `EventType` per event (skill `new-event-version`), added to
   `EventSchema.CURRENT`.
4. **Read state**: a field in `MealState` and its branch in `MealState.apply`. It is rebuilt from
   the log at every start, so adding it needs no migration.
5. **Commands and queries**: skill `new-command`; queries are `MealServer` functions returning read
   models built by pure functions (see `HabitViews`).
6. **Tests** in `domain/src/commonTest` and `application/src/commonTest`. Run `bash tools/check.sh`.
