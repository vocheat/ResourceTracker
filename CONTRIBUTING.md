# Contributing

Contributions are welcome. This document explains how to set up the development environment and submit changes.

## Prerequisites

- Java 21 (JDK)
- Git

## Setup

```bash
git clone https://github.com/your-username/ResourceTracker.git
cd ResourceTracker
./gradlew genSources    # decompile Minecraft sources for IDE navigation
./gradlew build         # verify everything compiles
```

Import the project into your IDE as a Gradle project. IntelliJ IDEA with the Fabric Loom plugin is recommended.

## Development Workflow

1. Create a branch from `master`
2. Make your changes
3. Run `./gradlew compileJava` to verify compilation (there is no test suite yet)
4. Test manually with `./gradlew runClient` — press M in-game to open the tracker menu
5. Submit a pull request

## Project Structure

```
src/main/java/net/fabricmc/resourcetracker/
  config/         TrackerConfig — GSON-based config, singleton
  client/         Client entry point, tick handler, keybinding
  client/gui/     GUI screens (MainScreen, EditScreen, HudMoveScreen)
  client/render/  HUD overlay renderer
  util/           InventoryUtils (item counting), RenderUtils (shared rendering)

src/main/resources/
  fabric.mod.json                        Mod metadata
  assets/resourcetracker/lang/           Localization (en_us.json, ru_ru.json)
```

## Code Conventions

**Colors** — all color values are ARGB format (`0xAARRGGBB`), not RGB. This applies to `textColor`, `nameColor`, `backgroundColor` in `TrackerConfig.TrackingList`.

**Item lookups** — never call `Registries.ITEM.get(Identifier.of(...))` directly in render methods. Use `TrackedItem.getItem()`, `getStack()`, `getDisplayName()` which cache the result. `Registries.ITEM.get()` returns `Items.AIR` for unknown IDs (never `null`), so use `trackedItem.isValid()` instead of null checks.

**Shared rendering** — `drawBox`, `drawStyledScrollbar`, `shortenText`, `getCountText` live in `util/RenderUtils.java`. Do not create private copies in screen classes.

**Config saving** — `TrackerConfig.save()` should not be called from text field change listeners. EditScreen saves on close. MainScreen saves immediately only for discrete actions (toggle visibility, delete list).

**Mouse handling** — MC 1.21.11 changed the `Screen.mouseClicked` signature. Custom list interactions use GLFW polling (`glfwGetMouseButton` + `wasMouseDown` pattern).

**Mappings** — this project uses Fabric Yarn mappings. Method names follow Yarn conventions (`getStack`, `getInventory`, `drawTextWithShadow`).

## Localization

Two language files: `en_us.json` and `ru_ru.json`. When adding new UI text:

1. Add the key to both files
2. Use `Text.translatable("gui.resourcetracker.your_key")` in code
3. Use `Text.literal(...)` for user-defined content (list names, etc.), not `Text.translatable`

## Submitting Changes

- Keep pull requests focused — one feature or fix per PR
- Verify compilation passes before submitting
- Update both localization files if you add UI text
- Describe what you changed and why in the PR description
