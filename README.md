# Resource Tracker

A client-side **Minecraft Fabric** mod that helps you track resource gathering progress directly on the HUD. Built for large survival projects where you need to collect hundreds of specific items.

## Features

- **Deep Scanning** — counts items not just in the main inventory, but recursively inside Shulker Boxes and Bundles
- **Fully Customizable** — text colors, background colors, scale, and position of each tracking list
- **Multiple Lists** — create separate tracking lists for different builds or goals
- **Item Search** — built-in search bar to quickly find any item in the registry
- **Drag-and-Drop** — move HUD elements on screen using the configuration menu
- **Two Display Modes** — show total count (`64/128`) or remaining (`Need: 64`)

## Requirements

- Java 21+
- [Fabric Loader](https://fabricmc.net/) >= 0.18.0
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Minecraft 1.21.11

## Installation

1. Install Fabric Loader and Fabric API
2. Download the latest `.jar` from [Releases](../../releases)
3. Place it in your `.minecraft/mods/` folder
4. Launch the game

## Usage

1. Press **M** (default keybinding) to open the Resource Tracker menu
2. Click **"Add New List"** to create a tracking list
3. Click the list name to open the editor — search for items on the left, click to add them
4. Set target amounts for each item (optional)
5. Use the color fields (RGBA) to customize appearance
6. Click **"Move HUD Elements"** to drag lists to the desired screen position

## Building from Source

```bash
git clone https://github.com/your-username/ResourceTracker.git
cd ResourceTracker
./gradlew build
```

The compiled JAR will be in `build/libs/`.

To launch Minecraft with the mod for testing:

```bash
./gradlew runClient
```

## License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

Copyright (c) 2026 vocheat
