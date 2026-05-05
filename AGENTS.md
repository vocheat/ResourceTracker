# Codex Project Notes

These instructions apply to `C:\Stuff\Projects\ResourceTracker`.

## Project

ResourceTracker is a client-side Minecraft Fabric mod for tracking resource
collection progress on the HUD.

- Java package root: `net.fabricmc.resourcetracker`
- Default target: Minecraft `1.21.11`
- Default build profile: `mc1_21_9_to_1_21_11`
- Java: JDK 21 for 1.21.x builds, JDK 25 for MC 26.1
- Mapping set: Mojang official mappings for 1.21.x; MC 26.1 has no `mappings` dependency
- No test suite exists; compilation is the primary automated verification.

## Build And Verify

Fast default compile check:

```powershell
.\gradlew.bat compileJava
```

The default `build.gradle` mirrors `build-mc1.21.9-1.21.11.gradle`.

Profile build files:

```text
build-mc1.21.0-1.21.4.gradle
build-mc1.21.5.gradle
build-mc1.21.6-1.21.8.gradle
build-mc1.21.9-1.21.11.gradle
build-mc26.1.gradle
```

Gradle 9.4.1 does not accept the old `-b` short option. Use the `profileBuild`
selector instead:

```powershell
.\gradlew.bat "-PprofileBuild=build-mc1.21.0-1.21.4.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.5.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.6-1.21.8.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.9-1.21.11.gradle" compileJava
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
.\gradlew.bat "-PprofileBuild=build-mc26.1.gradle" compileJava
```

Quote the `-PprofileBuild=...` argument in PowerShell because the build file
names contain dots.

Installed Java paths noted by the previous project setup:

- JDK 21: `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`
- JDK 25: `C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot`

## Architecture

```text
config/
  TrackerConfig

client/
  ResourceTrackerClient
  render/HudOverlay
  gui/MainScreen
  gui/EditScreen
  gui/SettingsScreen
  gui/HudMoveScreen

util/
  InventoryUtils
  RenderUtils

compat/
  mc1_21_0_to_1_21_4/java/.../VersionCompat + HudCompat
  mc1_21_5/java/.../VersionCompat + HudCompat
  mc1_21_6_to_1_21_8/java/.../VersionCompat + HudCompat
  mc1_21_9_to_1_21_11/java/.../VersionCompat + HudCompat
  mc26_1/java/.../VersionCompat + HudCompat
```

Key flow:

- `ResourceTrackerClient` loads config, registers keys, updates cached counts, and delegates HUD registration to `HudCompat`.
- `HudOverlay` renders cached counts only.
- `TrackerConfig` persists `resourcetracker.json` with GSON.
- `InventoryUtils` recursively counts inventory contents, containers, and bundles.
- `VersionCompat` owns key mapping registration, matrix operations, and registry id lookup differences.
- ModMenu is resolved from Modrinth Maven as `maven.modrinth:modmenu:<version>`.
- MC 26.1 compiles with profile-specific minimal `GuiGraphicsExtractor` screens/HUD.
  Full UI parity with 1.21.x is still pending.

## Non-Obvious Rules

- Never call `InventoryUtils.countItems()` from render code; counting happens in the tick handler.
- All stored colors are ARGB `0xAARRGGBB`.
- `EditScreen.close()` is the save point for that screen.
- `columns = 0` means auto-layout. Fixed user-selected columns are `1` through `5`.
- Keep all Codex and local workflow files in `Workflow/`. Create new plans, scratch notes, session reports, inventories, and progress logs only in that directory.
- Keep `Workflow/` ignored in `.gitignore` so local work does not reach GitHub.
- Do not delete working files when a task finishes. If every item in a plan file is complete, rename that file inside `Workflow/` to include `done` and the completion date, for example `Workflow/PLAN_1_21_X_BUGFIXES.done-2026-05-04.md`.
- Before reporting completion, check for new working files outside `Workflow/`. Move them into `Workflow/` or explain why the file belongs in the project.
- Do not add generated analysis, cache, assistant settings, or local tool output to GitHub unless the user asks for that artifact.
