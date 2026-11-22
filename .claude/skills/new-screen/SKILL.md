---
name: new-screen
description: Creates or changes a screen of the app in Compose Multiplatform - reading through MealServer, sending commands, English and French strings, navigation, the no-chores UX rule. Use for any work under ui/ or on androidApp's notifications.
---

# Screens

The UI is Compose Multiplatform in `ui/src/commonMain` (portable to iOS later). Reference:
`home/HomeScreen.kt`.

- **Reading**: collect `server.revision` and reload in a `LaunchedEffect(revision)`; read only
  through `MealServer` queries. Never keep a second copy of the data.
- **Changing**: send a command (`server.send(...)`), show `CommandResult.Refused` with
  `errorText(...)`. The UI never computes what the domain decides.
- **Text**: never a literal. Add the key to `ui/src/commonMain/composeResources/values/strings.xml`
  and the same key to `values-fr/strings.xml` (same placeholders, `%1$s`); use `stringResource`,
  `pluralStringResource` for counts. Dates through `dateText`, slots through `slotText`.
  `ArchitectureTest` fails on `Text("literal")`.
- **Navigation**: a new `Screen` case in `MealPlannerApp.kt` and its `Navigator.back()` target.
- **Android-only UI** (notifications, widgets) lives in `androidApp`, strings in its
  `res/values` and `res/values-fr`.
- **The no-chores rule** (README, constraint 4): a screen never asks for data the app can infer,
  never nags, and anything typed is shown back for confirmation before it is saved.
- Check on a phone or emulator after a visible change; `bash tools/check.sh` before saying done.
