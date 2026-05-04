# Codex Project Notes

These instructions apply to `C:\Stuff\Projects\ResourceTracker`.

## Project

ResourceTracker is a client-side Minecraft Fabric mod for tracking resource collection progress on the HUD.

- Java package root: `net.fabricmc.resourcetracker`
- Default target: Minecraft `1.21.11`
- Java: JDK 21 for normal builds
- Mapping set: Fabric Yarn
- Current profile: `modern`
- No test suite exists; compilation is the primary automated verification.

## Build And Verify

Run the fastest compile check after every code change:

```powershell
.\gradlew.bat compileJava
```

Full build:

```powershell
.\gradlew.bat build
```

Manual runtime check:

```powershell
.\gradlew.bat runClient
```

Multi-profile compile checks:

```powershell
.\gradlew.bat compileJava -Pprofile=legacy -Pminecraft_version=1.21.5 -Pyarn_mappings="1.21.5+build.1" -Pfabric_version="0.119.2+1.21.5"
.\gradlew.bat compileJava -Pprofile=transition -Pminecraft_version=1.21.8 -Pyarn_mappings="1.21.8+build.1" -Pfabric_version="0.136.1+1.21.8"
.\gradlew.bat compileJava -Pprofile=modern -Pminecraft_version=1.21.11 -Pyarn_mappings="1.21.11+build.4" -Pfabric_version="0.141.3+1.21.11"
```

MC 26.1 / nextgen uses Java 25 and `build-nextgen.gradle`:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
.\gradlew.bat compileJava -b build-nextgen.gradle -Pminecraft_version=26.1 -Pfabric_version="0.144.4+26.1" -Pfabric.loom.disableObfuscation=true
```

Installed Java paths noted by the previous project setup:

- JDK 21: `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`
- JDK 25: `C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot`

## Architecture

```text
config/
  TrackerConfig
    TrackingList
      TrackedItem

client/
  ResourceTrackerClient
  render/HudOverlay
  gui/MainScreen
  gui/EditScreen
  gui/HudMoveScreen

util/
  InventoryUtils
  RenderUtils

compat/
  legacy/VersionCompat
  transition/VersionCompat
  modern/VersionCompat
```

Key flow:

- `ResourceTrackerClient` loads config, registers the M keybind, and updates item counts every 10 ticks.
- `HudOverlay` renders cached counts only.
- `TrackerConfig` persists `resourcetracker.json` with GSON.
- `InventoryUtils` recursively counts inventory contents, containers, and bundles.
- `RenderUtils` owns shared drawing helpers and text formatting.

## Non-Obvious Rules

- `Registries.ITEM.get()` returns `Items.AIR` for unknown IDs, not `null`. Use `TrackedItem.isValid()` for validation.
- In render paths, use `TrackedItem.getItem()`, `getStack()`, and `getDisplayName()` so lazy caches are respected.
- Never call `InventoryUtils.countItems()` from render code; counting happens in the tick handler.
- All stored colors are ARGB `0xAARRGGBB`. Plain RGB values like `0xFFFFFF` render fully transparent because alpha is missing.
- `EditScreen.close()` is the save point for that screen. Do not add save calls to text field listeners.
- `MainScreen` saves immediately for discrete actions such as toggle, delete, and reorder.
- Custom list interactions use GLFW polling plus the `wasMouseDown` edge-detection pattern. Avoid replacing that with `mouseClicked` for these lists.
- Matrix transforms in HUD rendering go through `VersionCompat`; do not call `DrawContext.getMatrices().push()` or related matrix APIs directly.
- `columns = 0` means auto-layout. Fixed user-selected columns are `1` through `5`.

## Localization

Localization files must have matching keys:

- `src/main/resources/assets/resourcetracker/lang/en_us.json`
- `src/main/resources/assets/resourcetracker/lang/ru_ru.json`

When adding UI text:

- Add the key to both files.
- Use `Text.translatable("gui.resourcetracker.<key>")` for UI strings.
- Use `Text.literal(...)` for user-defined content like list names.

## Exploration

- Prefer source and docs over generated files.
- Read `CODEBASE_MEMORY.md` for the current local/GitHub comparison and MC 26.1 migration snapshot.
- Keep `CODEBASE_MEMORY.md` current. Update it when work changes architecture, build commands, migration status, important blockers, GitHub/local comparison, required tool/JDK versions, or non-obvious project rules.
- Read `CLAUDE.md`, `CONTRIBUTING.md`, and relevant Java files before changing behavior.
- `graphify-out/` is generated knowledge-graph output. It can help with orientation, but do not treat it as source code.
- After meaningful structural changes, rebuild Graphify only if the tool is available and the user expects it.
