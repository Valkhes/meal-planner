---
name: new-event-version
description: Adds an event type or changes what an event stores, without ever migrating data - EventType codecs, versions, upcasters, goldens. Use whenever a payload changes shape or a new kind of fact is recorded.
---

# Events: adding one, or changing one safely

The event log is the only data kept for good (README, "Data that survives iteration"). Old events
must read forever, on this phone and on the other one.

## A new event type
1. A `data class` in the context's domain package implementing its sealed `...Event` interface.
2. An `EventType` in the context's `...Events.kt` (`application/.../<context>/`): a `name` stable
   forever, `version = 1`, hand-written `encode`/`decode` with explicit field names (`json(...)`,
   `string(...)`). Optional fields are omitted when null.
3. Register it in the context's `types` list (already in `EventSchema.CURRENT`).
4. Append a golden to `application/src/commonTest/kotlin/dev/valkhes/mealplanner/application/EventGoldens.kt`:
   the exact JSON this version writes and the event it decodes to. `EventSchemaTest` fails without it.

## Changing a released event
- **Adding an optional field with a default**: allowed in place. Old payloads lack it; `decode`
  must read it with a default. Add nothing else.
- **Anything else** (rename, remove, change a type or meaning):
  1. bump the `EventType`'s `version` to N+1 and change `encode`/`decode` to the new shape;
  2. add `upcasters = mapOf(N to { old -> new })`: a pure function from a version-N payload to
     version N+1 (`EventType` refuses a missing step);
  3. keep the version-N golden as it is and add a version-N+1 golden.
- **Never** edit or remove a golden, rename a `name`, or rewrite stored events. A fact is
  corrected by a new event (`DishRenamed`, `MealRetracted`).

## Why it is safe to sync
An event from a newer app (unknown type or version) is kept in the store and skipped when reading
(`ReadEvent.Unknown`), so two phones on different versions never lose each other's data.

Run `bash tools/check.sh`.
