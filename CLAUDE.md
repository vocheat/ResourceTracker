# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Verify

```bash
./gradlew compileJava    # fast compile check (use this often)
./gradlew build          # full build — JAR in build/libs/
./gradlew runClient      # launch Minecraft with mod loaded
./gradlew genSources     # decompile MC sources for navigation
```

No test suite. **Always run `./gradlew compileJava` after making changes.** Use `/verify` for full validation.

## Architecture

Client-side Fabric mod (MC 1.21.11, Java 21). Package root: `net.fabricmc.resourcetracker`.

```
config/TrackerConfig       Singleton config (GSON → resourcetracker.json)
  └ TrackingList           name, x/y, scale, colors (ARGB), items
    └ TrackedItem          itemId, targetCount + transient caches

client/ResourceTrackerClient   Loads config, registers keybind (M), ticks cache every 10 ticks
client/render/HudOverlay       Renders lists on HUD, auto double-column on overflow
client/gui/MainScreen          List CRUD, scrollable, eye/trash icons
client/gui/EditScreen          Item search + add, color/position/scale editing
client/gui/HudMoveScreen       Drag-and-drop HUD positioning

util/InventoryUtils            Recursive counting (inventory → shulker boxes → bundles)
util/RenderUtils               Shared: drawBox, drawStyledScrollbar, shortenText, getCountText
```

## Non-Obvious Rules

These have caused bugs before. Do not violate them.

- **`Registries.ITEM.get()` never returns null** — it returns `Items.AIR`. Use `trackedItem.isValid()`, not `item != null`.
- **TrackedItem has caching methods** — `getItem()`, `getStack()`, `getDisplayName()` cache lazily. Never call `Registries.ITEM.get(Identifier.of(...))` or `new ItemStack(item)` in render paths.
- **All colors are ARGB** — `textColor`, `nameColor`, `backgroundColor` use format `0xAARRGGBB`. Don't use RGB-only like `0xFFFFFF`.
- **EditScreen saves on close** — `TrackerConfig.save()` is called in `close()`, NOT in `setChangedListener` callbacks. MainScreen saves immediately because toggle/delete are discrete actions.
- **Don't duplicate RenderUtils** — `drawBox`, `drawStyledScrollbar`, `shortenText`, `getCountText` are in `util/RenderUtils.java`. Never create private copies in screens.
- **MC 1.21.11 changed `mouseClicked` signature** to `(Click, boolean)`. Custom list click handling uses GLFW polling via `wasMouseDown` pattern — don't try to override `mouseClicked(double, double, int)`.
- **`Screen.hasShiftDown()` does not exist** in this MC version. Use GLFW `glfwGetKey` for shift detection.
- **Yarn mappings, not Mojang** — method names follow Yarn conventions (`getStack`, `drawTextWithShadow`).

## Localization

Two files: `en_us.json`, `ru_ru.json` in `assets/resourcetracker/lang/`. Must have identical key sets. Use `/sync-lang` to check.

## Verification

After any code change, run `/verify` or at minimum `./gradlew compileJava`. The mod has no tests — compilation is the primary correctness check.
