# Resource Tracker

Resource Tracker is a client-side Minecraft Fabric mod for tracking item collection progress on the HUD. It is built for survival projects where you need exact targets across many resources.

## Features

- Counts matching items in the player inventory, containers, shulker boxes, and bundles.
- Supports multiple per-world and per-server tracking lists.
- Lets you edit target counts, HUD position, scale, columns, colors, and icons.
- Shows completed item goals on each list card and a live HUD preview while editing.
- Provides item search by translated name or item id.
- Stores lists as editable `.txt` files under the Minecraft config folder.

## Supported profiles

The universal JAR targets Minecraft 1.21.0 through 1.21.11, plus 26.1 and
26.2. The 1.21.x payloads use Java 21; 26.1 and 26.2 use Java 25. Install
both JDKs to build the universal JAR.

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

## Usage

1. Press **M** by default to open the tracker menu.
2. Click the **+** button to create a list.
3. Click a list row to edit it. Search by item name or id, then click an item to add it.
4. Set target counts and list style. Color fields use RGBA values from 0 to 255. Use **Preview** to see the HUD while changing its style, then **Items** to return to item selection.
5. Click **Move HUD Elements** and drag visible lists into place.
6. Use the folder buttons to open or reload editable list files.

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
```

The historical grouped selectors remain aliases to their upper exact target:
`build-mc1.21.0-1.21.4.gradle` selects 1.21.4,
`build-mc1.21.6-1.21.8.gradle` selects 1.21.8, and
`build-mc1.21.9-1.21.11.gradle` selects 1.21.11.

## License

MIT. See [LICENSE](LICENSE).

Copyright (c) 2026 vocheat
