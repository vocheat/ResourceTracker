# Resource Tracker

Resource Tracker is a client-side Minecraft Fabric mod for tracking item collection progress on the HUD. It is built for survival projects where you need exact targets across many resources.

## Features

- Counts matching items in the player inventory, containers, shulker boxes, and bundles.
- Supports multiple per-world and per-server tracking lists.
- Lets you edit target counts, HUD position, scale, columns, colors, and icons.
- Uses olive HUD cards with a distinct title band, bold heading, divider, goal summary, per-resource progress strips, and muted counts; completed goals use the interface's green accent.
- Uses the same HUD rendering in game, in the live editor preview, and while dragging lists into position.
- Provides a compact three-section editor (**Resources / Raw materials / HUD**), a persistent searchable item catalog, and one action panel for the selected resource. Search accepts translated names or item ids.
- Stores lists as editable `.txt` files under the Minecraft config folder.
- Imports `.litematic` schematics, Litematica material-list JSON, and ResourceTracker TXT through a file browser and explicit preview.
- Calculates linked raw materials using vanilla crafting, stonecutting, cooking, and smithing upgrade recipes, with recipe/ingredient choices and shared inventory accounting.
- Supports persistent manual completion, pinned HUD rows, completed-row filtering, remaining-count sorting, row limits, and item/stack/decimal-stack/shulker units.
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
2. Click **Create** and choose **Empty list**, **From file**, or **From area**. Area capture returns to the world with instructions using your current key binding.
3. Click a list row to edit it. In **Resources**, click **Add** to open the catalog. It stays open for a series of additions and retains its search and page. New resources start at 1; selecting an existing item opens its target without adding another copy. Use **Done adding** to return.
4. **Resources** has one panel for the selected row: exact target in items, manual completion, pinning, replacement, and deletion. Clicking a target focuses its count field. On small windows, details replace the table until you return. **HUD** groups controls into **Content**, **Appearance**, and **Position**. Wide windows show a live preview alongside controls; narrow windows open it separately. Preview zoom (**100% / 200% / 300%**) is independent of the saved HUD scale. The color picker keeps presets, RGB HEX, opacity, and expandable exact RGBA channels. Changes are saved when you leave the editor.
5. Click **Place HUD** and drag visible lists into place. HUD cards stay inside the screen when counts, completion, icons, or window dimensions change: a card at the right edge shifts left as it widens. Cards larger than the window temporarily fit within it without changing the saved position or scale. Toggle a list's eye icon to show or hide it; use its **…** menu to delete it with confirmation.
6. **Settings** contains **New lists**, **Notifications**, **Keys**, and **Files and recovery**. New-list defaults include their colors and affect only lists created afterwards. The overview's **Files** link opens the same file page for folders, reload, deleted-list recovery, and configuration recovery.
7. Use **Create → From file** to browse a schematic or list, inspect its read-only material draft, then create a new list. **Another file** returns to the browser without losing its directory/search; **Notes** shows import limitations. Litematica JSON uses `Total`; its multiplier is already included. Native schematics use all regions and block placement materials, without checking the current world, container contents, entities, or decorative NBT. Unsupported states and invalid quantities block import instead of creating incomplete totals.
8. Choose **HUD → Content → On HUD** to display targets or raw materials. Opening **Raw materials** alone does not change the HUD source. **Calculation rules** groups wood, smelting, and the independent stone policy. Recipe details let you choose collecting a finished item, automatic or explicit recipes, and variants of the selected recipe's ingredients. Enable **Before smelting base materials** in the rules to also expand collection materials through furnace recipes: iron/gold/copper ingots into raw metals, or stone into cobblestone. The setting applies to the raw-material HUD and is saved per list; **Undo** restores the previous setting. Existing ingots and finished goods reduce the required inputs, and explicit recipe choices or expansion stops take priority. When stone is involved, the stone selector switches between collecting stone directly with Silk Touch and smelting cobblestone, independently of the general toggle; use Recipes for other per-material choices. Closing the editor saves the display mode without replacing the original goals. Stonecutting, cooking, and smithing upgrades are supported; fuel and workstations are excluded. Leather, wool, dyes, and ordinary ingots are gathered materials by default; choose a recipe explicitly to expand them. Special recipes that depend on item components remain finished items. This uses the current game's vanilla recipes, which can differ from server datapacks. Completed goods and intermediates in your inventory reduce the remaining crafting work.
9. Select a resource and switch **Collected manually** on when you have finished collecting it. The annotation survives moving the items out of inventory and restarting the game; it does not create virtual inventory. Increasing that row's requirement clears its annotation. Raw-material annotations are independent of finished-item annotations.
10. Hover an item in an inventory or container and press **K** to add it to a tracking list. Confirm the quantity and destination; existing targets increase by that quantity. Find all mod bindings under **Controls → Key Binds → ResourceTracker**.
11. Use **Undo** or **Ctrl+Z** in the editor to reverse recent resource edits. **Restore list** in the overview restores a recently deleted list. History lasts for the current world/server session and is cleared when lists are reloaded.
12. Configure resource/list completion messages, sound, and volume in **Settings → Notifications**. Messages also work when the global HUD is hidden; opening a world with completed lists stays quiet.
13. Aim at a block and press **B** to set the first area corner. Aim at the opposite corner while the colored outline follows your view, then press **B** again. Alternatively, use `/rtarea pos1 x y z` and `/rtarea pos2 x y z`; omitting coordinates uses the aimed block within 128 blocks. Both corner blocks are included. `/rtarea cancel` cancels selection or counting. The limit is 2,000,000 blocks including air, and all selected chunks must be loaded on the client. Counting is spread across ticks; it captures the world as scanned, so avoid changing the area during capture.
14. In the area draft, enter a list name and select a material to replace it with the same quantity or remove it using its detail panel. **Create list** saves the result; **Cancel** discards the draft. Air and duplicate door/bed halves are excluded. Unsupported blocks are reported in **Details** and on hover; containers' contents, entities, and decorative block-entity data are not counted. In the normal list editor, select a resource and click **Replace item**. Selecting an existing resource adds the targets; exceeding the quantity limit is rejected. A changed material starts with a fresh collected annotation, and the editor's **Undo** reverses the replacement.
15. Open **List actions → Copies** in the area draft and press **Apply multiplier** to prepare materials for several identical builds. Counts always derive from one copy: changing ×3 to ×2 produces two copies. Replacements and removals work with this factor; **Create list** saves the displayed totals. Invalid values and quantity overflow are rejected. **Undo** or **Ctrl+Z** reverses draft deletion, replacement, wood-family replacement, and applied multiplier changes. Cancelling discards the draft and its history.
16. Use **List actions → Replace wood in this list** in the area draft or ordinary list editor to replace all matching wooden shapes, such as oak planks, stairs, slabs, and doors with spruce equivalents, preserving quantities. Review the source/destination families and preview, then apply once. Logs, stripped wood, signs, fences, boats, and other available counterparts are supported. Shapes without a counterpart stay unchanged and are shown in the preview. Existing destination rows merge; quantity overflow rejects the entire operation. The full group replacement is one undo step, and ordinary editor changes save on close.
17. In **HUD → Content**, choose **Units** to switch between item counts, full stacks plus leftover items, decimal stacks, and shulkers. For a stack size of 64, 272 items display as `272`, `4 st. 16 items`, or `4.25 st.`. Decimal stacks always round up to hundredths (`273` → `4.27 st.`) so the displayed requirement never understates what you need. Each item's actual maximum stack size is used. The format applies to HUD counts, the live preview, and raw materials, and is saved per list when the editor closes.
18. Open **Raw materials → Calculation rules → Wood in recipes** to choose a wood type for recipes with interchangeable wooden ingredients, such as chests and sticks. Choose **Automatic** to use available types and mixtures, or select a specific available overworld wood, nether wood, or bamboo. Counts follow the actual recipes and their yields. Items of a specific wood type keep their own requirements: oak stairs still require oak. Explicit ingredient choices under **Recipes** take priority. The setting updates the raw-material HUD, supports **Undo**, and saves per list when the editor closes.

The stock grey/white HUD palette is upgraded to the olive theme when lists or settings are loaded. Custom palettes retain their saved colors and alpha. Turning item icons off switches to compact rows with names on the left and counts on the right; automatic and fixed column layouts remain available.

Interface icons use a shared 16×16 pixel grid. Their editable SVG sources are in `src/main/resources/assets/resourcetracker/textures/gui/icons/svg/`; Minecraft uses the exported PNG atlas. Check marks have two separately drawn variants: 16×16 for buttons and already-added items in the item chooser, and 6×6 for completed goals beside text in the editor, raw materials, and HUD. Selected choices also use the PNG check rather than a font glyph.

## Languages

ResourceTracker follows Minecraft's selected language (**Options → Language**). Its complete interface is available in English, Russian, Simplified Chinese, Hindi, Spanish, Arabic, French, Bengali, Brazilian Portuguese, Indonesian, Urdu, and German.

The mod registers Bengali (`bn_bd`) and Urdu (`ur_pk`) in the native language menu. These translations cover ResourceTracker; vanilla item names and game menus use the installed Minecraft language assets, with Minecraft's normal English fallback where unavailable. Arabic and Urdu use right-to-left text rendering. Commands, item identifiers, and exact target quantities keep their original format.

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
