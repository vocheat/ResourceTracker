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
