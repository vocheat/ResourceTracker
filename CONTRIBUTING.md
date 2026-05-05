# Contributing

Keep changes focused and verify the profile you touched before opening a pull request.

## Prerequisites

- Git
- JDK 21 for Minecraft 1.21.x profiles
- JDK 25 for Minecraft 26.1

## Setup

```powershell
git clone https://github.com/your-username/ResourceTracker.git
cd ResourceTracker
.\gradlew.bat compileJava
```

Import the project as a Gradle project. The project uses Fabric Loom and Mojang official mappings.

## Profile builds

Use `-PprofileBuild=...`; do not use Gradle's old `-b` option.

```powershell
.\gradlew.bat "-PprofileBuild=build-mc1.21.0-1.21.4.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.5.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.6-1.21.8.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.9-1.21.11.gradle" compileJava
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
.\gradlew.bat "-PprofileBuild=build-mc26.1.gradle" compileJava
```

## Project structure

```text
src/main/java/net/fabricmc/resourcetracker/
  client/         Client entry point and ModMenu integration
  client/gui/     Shared 1.21.9+ UI screens
  client/render/  Shared HUD renderer
  config/         JSON settings and TXT list storage
  util/           Inventory, rendering, and icon helpers

src/compat/<profile>/java/
  Profile-specific API shims and compatibility classes
```

## Rules

- Keep assistant notes, logs, scratch files, and run scripts in `Workflow/`.
- Do not commit generated files from `build/`, `run/`, `.gradle/`, Graphify, or local assistant tools.
- Store colors as ARGB integers in code and config. UI color editors expose RGBA fields from 0 to 255.
- Never count inventory from HUD rendering code. Cache counts during client ticks.
- Add new UI text to both `en_us.json` and `ru_ru.json`.
- Check `git status --short --ignored` before committing.
