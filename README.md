# Resource Tracker

Resource Tracker is a client-side Minecraft Fabric mod for tracking item collection progress on the HUD. It is built for survival projects where you need exact targets across many resources.

## Features

- Counts matching items in the player inventory, containers, shulker boxes, and bundles.
- Supports multiple per-world and per-server tracking lists.
- Lets you edit target counts, HUD position, scale, columns, colors, and icons.
- Uses compact olive HUD cards with a goal summary, subtle borders, per-resource progress strips, and muted counts; completed goals use the interface's green accent.
- Uses the same HUD rendering in game, in the live editor preview, and while dragging lists into position.
- Provides item search by translated name or item id.
- Stores lists as editable `.txt` files under the Minecraft config folder.
- Imports `.litematic` schematics, Litematica material-list JSON, and ResourceTracker TXT through a file browser and explicit preview.
- Calculates linked raw materials using vanilla crafting, stonecutting, cooking, and smithing upgrade recipes, with recipe/ingredient choices and shared inventory accounting.
- Supports persistent manual completion, pinned HUD rows, completed-row filtering, remaining-count sorting, row limits, and item/stack/shulker units.
- Quickly adds a hovered inventory/container item with a configurable key (K by default), quantity confirmation, and list selection.
- Announces resource/list completion with optional sound and volume settings, without repeating notifications on every update.
- Provides session undo for resource edits, removal, clearing, recipe choices, and deleted lists.
- Captures placement materials from an inclusive cuboid selected by commands or a configurable key, with a live colored outline and an editable list draft.
- Replaces a list resource without re-entering its quantity; replacing with an existing resource combines their targets.

## Supported profiles

The universal JAR targets Minecraft 1.21.0 through 1.21.11, plus 26.1, 26.2,
and 26.3. The 1.21.x payloads use Java 21; 26.1, 26.2, and 26.3 use Java 25.
Install both JDKs to build the universal JAR.

It contains one compiled payload per Minecraft version and selects it at startup.

- Minecraft 1.21.0, Java 21
- Minecraft 1.21.1, Java 21
- Minecraft 1.21.2, Java 21
- Minecraft 1.21.3, Java 21
- Minecraft 1.21.4, Java 21
- Minecraft 1.21.5, Java 21
- Minecraft 1.21.6, Java 21
- Minecraft 1.21.7, Java 21
- Minecraft 1.21.8, Java 21
- Minecraft 1.21.9, Java 21
- Minecraft 1.21.10, Java 21
- Minecraft 1.21.11, Java 21
- Minecraft 26.1, Java 25
- Minecraft 26.2, Java 25
- Minecraft 26.3, Java 25

## Usage

1. Press **M** by default to open the tracker menu.
2. Click **Create list** to create a list and open its editor.
3. Click a list row to edit it. Search by item name or id, then click an item to add it.
4. Use **Items** to set target counts and **HUD appearance** to adjust position, scale, columns, icons, and colors alongside a live preview. Preview zoom starts at **200%**; its **100% / 200% / 300%** controls do not change the saved HUD scale. Large previews fit the panel and show the effective zoom. The color picker offers a palette, RGB HEX, opacity, and optional exact RGBA values. Changes are saved when you leave the editor.
5. Click **Place HUD** and drag visible lists into place. Toggle a list's eye icon to show or hide it; use its **…** menu to delete it with confirmation.
6. Use **Files** to open folders or reload editable list files. **Settings** separates new-list defaults, their appearance, and file tools.
7. Use **Import** in the main menu to browse a scheme or list, inspect its materials, then create a new list. Litematica JSON uses `Total`; its multiplier is already included. Native schematics use all regions and block placement materials, without checking the current world, container contents, entities, or decorative NBT. Unsupported states and invalid quantities block import instead of creating incomplete totals.
8. In an existing list's editor, click **HUD: finished goals** below the tabs to switch to **HUD: raw materials**. The same switch is available on the **Raw materials** page, where you can select recipes and ingredient variants or stop expansion at a particular item. Closing the editor saves the display mode without replacing the original goals. Stonecutting, cooking, and smithing upgrades are supported; fuel and workstations are excluded. Leather, wool, dyes, and ordinary ingots are gathered materials by default; choose a recipe explicitly to expand them. Special recipes that depend on item components remain finished items. This uses the current game's vanilla recipes, which can differ from server datapacks. Completed goods and intermediates in your inventory reduce the remaining crafting work.
9. Mark a row **Already collected** when you have finished collecting it. The annotation survives moving the items out of inventory and restarting the game; it does not create virtual inventory. Increasing that row's requirement clears its annotation. Raw-material annotations are independent of finished-item annotations.
10. Hover an item in an inventory or container and press **K** to add it to a tracking list. Confirm the quantity and destination; existing targets increase by that quantity. Find all mod bindings under **Controls → Key Binds → ResourceTracker**.
11. Use **Undo** or **Ctrl+Z** in the editor to reverse recent resource edits. **Restore list** in the overview restores a recently deleted list. History lasts for the current world/server session and is cleared when lists are reloaded.
12. Configure resource/list completion messages, sound, and volume in **Settings → New lists → Completion notifications**. Messages also work when the global HUD is hidden; opening a world with completed lists stays quiet.
13. Aim at a block and press **B** to set the first area corner. Aim at the opposite corner while the colored outline follows your view, then press **B** again. Alternatively, use `/rtarea pos1 x y z` and `/rtarea pos2 x y z`; omitting coordinates uses the aimed block within 128 blocks. Both corner blocks are included. `/rtarea cancel` cancels selection or counting. The limit is 2,000,000 blocks including air, and all selected chunks must be loaded on the client. Counting is spread across ticks; it captures the world as scanned, so avoid changing the area during capture.
14. In the area draft, enter a list name, click a material to replace it with the same quantity, or click **×** to remove it. **Create list** saves the result; **Cancel** discards the draft. Air and duplicate door/bed halves are excluded. Unsupported blocks are reported with details on hover; containers' contents, entities, and decorative block-entity data are not counted. In the normal list editor, click a resource's icon/name to replace it. Selecting an existing resource adds the targets; exceeding the quantity limit is rejected. A changed material starts with a fresh collected annotation, and the editor's **Undo** reverses the replacement.
15. Set **Copies: ×** in the area draft and press **Apply multiplier** to prepare materials for several identical builds. Counts always derive from one copy: changing ×3 to ×2 produces two copies. Replacements and removals work with this factor; **Create list** saves the displayed totals. Invalid values and quantity overflow are rejected. **Undo** or **Ctrl+Z** reverses draft deletion, replacement, wood-family replacement, and applied multiplier changes. Cancelling discards the draft and its history.
16. Use **Replace wood family** in the area draft or ordinary list editor to replace all matching wooden shapes, such as oak planks, stairs, slabs, and doors with spruce equivalents, preserving quantities. Review the source/destination families and preview, then apply once. Logs, stripped wood, signs, fences, boats, and other available counterparts are supported. Shapes without a counterpart stay unchanged and are shown in the preview. Existing destination rows merge; quantity overflow rejects the entire operation. The full group replacement is one undo step, and ordinary editor changes save on close.

The stock grey/white HUD palette is upgraded to the olive theme when lists or settings are loaded. Custom palettes retain their saved colors and alpha. Turning item icons off switches to compact rows with names on the left and counts on the right; automatic and fixed column layouts remain available.

Interface icons use a shared 16×16 pixel grid. Their editable SVG sources are in `src/main/resources/assets/resourcetracker/textures/gui/icons/svg/`; Minecraft uses the exported PNG atlas. Check marks have two separately drawn variants: 16×16 for already-added items in the item chooser, and 6×6 for completed goals beside text in the menu and HUD.

## Building

Build the universal JAR:

```powershell
.\gradlew.bat '-PprofileBuild=build-universal.gradle' :universal:universalJar
```

The JAR is written to `gradle-projects/universal/build/libs/`.

Default compile target is Minecraft 1.21.11:

```powershell
.\gradlew.bat compileJava
```

Run the dependency-free regression suite (including actual Minecraft registry and block-state conversion checks):

```powershell
.\gradlew.bat test compileJava
```

Compile a specific profile:

```powershell
.\gradlew.bat "-PprofileBuild=build-mc1.21.0.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.1.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.2.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.3.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.4.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.5.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.6.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.7.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.8.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.9.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.10.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.11.gradle" compileJava
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
.\gradlew.bat "-PprofileBuild=build-mc26.1.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc26.2.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc26.3.gradle" compileJava
```

The historical grouped selectors remain aliases to their upper exact target:
`build-mc1.21.0-1.21.4.gradle` selects 1.21.4,
`build-mc1.21.6-1.21.8.gradle` selects 1.21.8, and
`build-mc1.21.9-1.21.11.gradle` selects 1.21.11.

## License

MIT. See [LICENSE](LICENSE).

Copyright (c) 2026 vocheat
