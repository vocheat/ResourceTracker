# Resource Tracker

Resource Tracker is a client-side Minecraft Fabric mod for tracking item collection progress on the HUD. It is built for survival projects where you need exact targets across many resources.

## Features

- Counts matching items in the player inventory, containers, shulker boxes, and bundles.
- Supports multiple per-world and per-server tracking lists.
- Lets you edit target counts, HUD position, scale, columns, colors, and icons.
- Provides item search by translated name or item id.
- Stores lists as editable `.txt` files under the Minecraft config folder.

## Supported profiles

- Minecraft 1.21.0-1.21.4, Java 21
- Minecraft 1.21.5, Java 21
- Minecraft 1.21.6-1.21.8, Java 21
- Minecraft 1.21.9-1.21.11, Java 21
- Minecraft 26.1, Java 25

## Usage

1. Press **M** by default to open the tracker menu.
2. Click the **+** button to create a list.
3. Click a list row to edit it. Search by item name or id, then click an item to add it.
4. Set target counts and list style. Color fields use RGBA values from 0 to 255.
5. Click **Move HUD Elements** and drag visible lists into place.
6. Use the folder buttons to open or reload editable list files.

## Building

Default compile target is Minecraft 1.21.9-1.21.11:

```powershell
.\gradlew.bat compileJava
```

Compile a specific profile:

```powershell
.\gradlew.bat "-PprofileBuild=build-mc1.21.0-1.21.4.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.5.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.6-1.21.8.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.9-1.21.11.gradle" compileJava
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
.\gradlew.bat "-PprofileBuild=build-mc26.1.gradle" compileJava
```

## License

MIT. See [LICENSE](LICENSE).

Copyright (c) 2026 vocheat
