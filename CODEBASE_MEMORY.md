# ResourceTracker Codebase Memory

Snapshot date: 2026-05-04.

## Memory Maintenance

This file is living project memory, not a one-time report. Update it during future work when any of these change:

- Architecture, ownership of modules, or important code flow.
- Build, compile, run, CI, Java/JDK, Fabric, Loom, Minecraft, or dependency versions.
- MC 26.1 migration status, blockers, commands tried, and compile/runtime results.
- Local-vs-GitHub comparison, active branch, important commits, or uncommitted work that affects the migration.
- Non-obvious project rules, compatibility constraints, known risks, or decisions that future agents must preserve.

Keep updates factual and compact. Prefer replacing stale facts over appending contradictory notes.

## Project Identity

ResourceTracker is a client-side Minecraft Fabric mod. It lets players create HUD tracking lists for resource collection goals, count matching items in inventory, and render progress on screen.

- Package root: `net.fabricmc.resourcetracker`
- Current public version line: `1.2-beta`
- Current default GitHub target: Minecraft `1.21.11`, Fabric Yarn mappings, Java 21
- Migration work target: Minecraft `26.1`, Mojang-native names, no obfuscation, Java 25
- Normal compile check: `.\gradlew.bat compileJava`
- Nextgen compile check: set `JAVA_HOME` to JDK 25 and run `.\gradlew.bat compileJava -b build-nextgen.gradle -Pminecraft_version=26.1 -Pfabric_version="0.145.1+26.1" -Pfabric.loom.disableObfuscation=true --no-daemon`

Known local JDK paths:

- JDK 21: `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`
- JDK 25: `C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot`

## Git State Versus GitHub

Remote:

- `origin`: `https://github.com/vocheat/ResourceTracker`
- After `git fetch origin --prune --tags`, `origin/master` is `35a7d470bc9595baff99d944d07aafb93bc0c749`
- Local `HEAD` on `mc-26.1-migration` is `756f5340c951151e93b27659c80613d281c64dc7`
- `origin/v1.2-beta` is also `756f5340c951151e93b27659c80613d281c64dc7`

Important comparison:

- `git diff HEAD origin/master` is empty. GitHub `master` has the same committed file tree as local `HEAD`; the only difference is the GitHub merge commit `35a7d47`.
- The real local-vs-GitHub delta is the uncommitted working tree on this machine.

Tracked local modifications compared to GitHub/HEAD:

- `build.gradle`
- `gradle.properties`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradlew`
- `settings.gradle`
- `src/main/java/net/fabricmc/resourcetracker/client/ResourceTrackerClient.java`
- `src/main/java/net/fabricmc/resourcetracker/client/gui/EditScreen.java`
- `src/main/java/net/fabricmc/resourcetracker/client/gui/HudMoveScreen.java`
- `src/main/java/net/fabricmc/resourcetracker/client/gui/MainScreen.java`
- `src/main/java/net/fabricmc/resourcetracker/client/render/HudOverlay.java`
- `src/main/java/net/fabricmc/resourcetracker/config/TrackerConfig.java`
- `src/main/java/net/fabricmc/resourcetracker/util/InventoryUtils.java`
- `src/main/java/net/fabricmc/resourcetracker/util/RenderUtils.java`
- `src/main/resources/fabric.mod.json`

Untracked local files/directories:

- `AGENTS.md`
- `CODEBASE_MEMORY.md`
- `build-nextgen.gradle`
- `settings-nextgen.gradle`
- `mc26.1_progress.md`
- `mc26.1_transition.md`
- `src/compat/nextgen/.../VersionCompat.java`
- `META-INF/jars/fabric-rendering-v1-23.0.4+10de3da347.jar`
- `remappedSrc/...`

Do not commit generated/cache output casually. `remappedSrc/` is generated migration output. `META-INF/jars/...` looks like a vendored Fabric jar and should be reviewed before committing.

## Architecture Map

Core data:

- `config/TrackerConfig`: singleton config, GSON persistence to `resourcetracker.json`
- `TrackerConfig.TrackingList`: one HUD panel, position, scale, colors, visibility, icons, columns, tracked items
- `TrackerConfig.TrackedItem`: item ID plus target count, with transient lazy caches for `Item`, `ItemStack`, and display name

Client entry:

- `client/ResourceTrackerClient`: loads config, registers keybindings, toggles HUD visibility, updates cached counts every 10 ticks, registers HUD renderer

HUD/rendering:

- `client/render/HudOverlay`: renders only cached counts, never scans inventory
- `util/RenderUtils`: common drawing helpers, text shortening, count text formatting, column layout calculation

GUI:

- `client/gui/MainScreen`: list CRUD, toggle/delete/reorder, opens edit/move screens
- `client/gui/EditScreen`: list editor, item search, colors, position, scale, columns, clear confirmation
- `client/gui/HudMoveScreen`: drag-and-drop HUD positioning

Counting:

- `util/InventoryUtils`: recursive inventory scan, including container data and bundle contents

Compatibility:

- `src/compat/legacy/.../VersionCompat.java`: older 1.21.x matrix/keybinding API
- `src/compat/transition/.../VersionCompat.java`: middle 1.21.x matrix API
- `src/compat/modern/.../VersionCompat.java`: 1.21.9-1.21.11 style API
- `src/compat/nextgen/.../VersionCompat.java`: MC 26.1+, Mojang-native names, `KeyMappingHelper`

ModMenu:

- `client/ModMenuIntegration`: exposes the main config screen through ModMenu.

## Non-Obvious Project Rules

- `TrackedItem.isValid()` is the validation gate. Older Yarn code used `Registries.ITEM.containsId(...)`; current migrated code uses `BuiltInRegistries.ITEM.containsKey(...)`.
- Render paths should use `TrackedItem.getItem()`, `getStack()`, and `getDisplayName()` to keep hot-path allocations down.
- Do not call `InventoryUtils.countItems()` from render code. Counts are updated from the client tick handler.
- Colors are ARGB `0xAARRGGBB`. Plain RGB values like `0xFFFFFF` render transparent because alpha is missing.
- `EditScreen.close()` is the save point for edit-screen changes. `MainScreen` saves immediately for discrete actions.
- Custom list clicks use GLFW polling plus `wasMouseDown` edge detection.
- Matrix operations go through `VersionCompat`.
- `columns = 0` means auto-layout; fixed columns are `1` through `5`.
- Localization keys must match between `en_us.json` and `ru_ru.json`.

## Prior Claude Code Work

Relevant Claude artifacts found locally:

- `.claude/settings.local.json`: broad permissions used by Claude Code, including `git`, `gh`, Gradle, Fabric/Mojang/Modrinth web fetches, Java version checks, and nextgen compile commands.
- `CLAUDE.md`: detailed build, architecture, compatibility, Graphify, and verification guidance.
- `mc26.1_transition.md`: migration plan for MC 26.1, including Java 25, no mappings line, `net.fabricmc.fabric-loom`, `implementation` instead of `modImplementation`, and manual Fabric API rename risks.
- `mc26.1_progress.md`: concrete migration checklist.
- `review.md`: older project review with UX/code-quality issues.
- `graphify-out/GRAPH_REPORT.md`: generated architecture graph from 2026-04-11.

Do not edit `.claude/` files unless the user explicitly asks to change Claude Code settings.

## MC 26.1 Migration State

Already done locally:

- Created branch `mc-26.1-migration`.
- Added `build-nextgen.gradle` for MC 26.1 / Java 25 / no mappings line.
- Added `settings-nextgen.gradle`.
- Updated Gradle wrapper to Gradle `9.4.1`.
- Added nextgen properties:
  - `minecraft_version_nextgen=26.1`
  - `loader_version_nextgen=0.18.6`
  - `fabric_version_nextgen=0.145.1+26.1`
  - `modmenu_version_nextgen=18.0.0-alpha.8`
- Updated `fabric.mod.json` placeholders for `minecraft_min_version` and `java_min_version`.
- Added `src/compat/nextgen/.../VersionCompat.java`.
- Main Java sources are already partially migrated from Yarn-style names to Mojang-style names:
  - `DrawContext` -> `GuiGraphics`
  - `Text` -> `Component`
  - `KeyBinding` -> `KeyMapping`
  - `client.world` / `age` -> `client.level` / `tickCount`
  - `Registries` -> `BuiltInRegistries`
  - `DataComponentTypes` -> `DataComponents`
  - `net.minecraft.item.*` -> `net.minecraft.world.item.*`

Current hard blocker:

- Nextgen compile currently fails before Java compilation.
- Command run:
  `.\gradlew.bat compileJava -b build-nextgen.gradle -Pminecraft_version=26.1 -Pfabric_version="0.145.1+26.1" -Pfabric.loom.disableObfuscation=true --no-daemon`
- Result: Gradle prints `Fabric Loom: 1.14.10` even though `build-nextgen.gradle` declares Loom `1.16.1`.
- Failure: `Failed to find minecraft version: 26`.
- This matches the blocker already recorded in `mc26.1_progress.md`: plugin resolution/cache is pulling old Loom instead of the intended 1.16.1.

Likely next investigation:

1. Resolve why `build-nextgen.gradle` still loads Loom `1.14.10`.
2. Re-run nextgen compile to reach real Java compiler errors.
3. Fix post-mapping Java/API errors.
4. Run Java 21 modern compile to ensure the old 1.21.11 profile still works.
5. Update CI matrix for nextgen only after local compile is stable.

## Suspicious Migration Points To Check After Loom Is Fixed

- `TrackerConfig.java` imports `net.minecraft.resources.Identifier`. In Mojang names this may need to be `net.minecraft.resources.ResourceLocation`; verify against actual 26.1 sources.
- `fabric.mod.json` still declares `"fabricloader": ">=0.16.0"` directly while `processResources` computes a loader version. Decide whether nextgen should template this too.
- `build-nextgen.gradle` uses `com.terraformersmc:modmenu:${modmenu_version_nextgen}` with only Terraformers Maven. The broader `build.gradle` nextgen branch uses `maven.modrinth:modmenu` and adds Modrinth Maven. Verify which coordinate is valid for `18.0.0-alpha.8`.
- The untracked `META-INF/jars/fabric-rendering-v1-23.0.4+10de3da347.jar` should be explained or removed from the commit plan.
- `remappedSrc/` should be treated as generated output unless intentionally used as a source for manual migration.

## GitHub Baseline Summary

GitHub `master` currently represents the stable 1.2-beta multi-version 1.21.5-1.21.11 state:

- `build.gradle` uses `fabric-loom` `1.14-SNAPSHOT`, Yarn mappings, Java 21.
- Profiles are `legacy`, `transition`, and `modern`.
- Default properties target `minecraft_version=1.21.11`, `yarn_mappings=1.21.11+build.4`, `fabric_version=0.141.3+1.21.11`, `loader_version=0.19.1`.
- Local working tree adds the 26.1 migration on top of this baseline.

## Practical Next Steps

High priority:

1. Fix Loom plugin resolution for nextgen.
2. Compile nextgen and capture Java errors.
3. Fix Mojang-name/API mismatches in source.
4. Decide whether `build-nextgen.gradle` stays separate or whether `build.gradle -Pprofile=nextgen` becomes the only path.
5. Keep `remappedSrc/` and generated jars out of the final commit unless there is a deliberate reason.

Medium priority:

1. Update `.github/workflows/build.yml` for a nextgen matrix entry with Java 25.
2. Re-run old profile compile checks after main sources use Mojang names.
3. Refresh project docs after the compile path is proven.
