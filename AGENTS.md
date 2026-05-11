# Codex Project Notes

These instructions apply to `C:\Stuff\Projects\ResourceTracker`.

## Project

ResourceTracker is a client-side Minecraft Fabric mod for tracking resource
collection progress on the HUD.

- Java package root: `net.fabricmc.resourcetracker`
- Default target: Minecraft `1.21.11`
- Default build profile: `mc1_21_9_to_1_21_11`
- Java: JDK 21 for 1.21.x builds, JDK 25 for MC 26.1
- Mapping set: Mojang official mappings for 1.21.x; MC 26.1 has no `mappings` dependency
- No test suite exists; compilation is the primary automated verification.

## Build And Verify

Fast default compile check:

```powershell
.\gradlew.bat compileJava
```

The default `build.gradle` mirrors `build-mc1.21.9-1.21.11.gradle`.

Profile build files:

```text
build-mc1.21.0-1.21.4.gradle
build-mc1.21.5.gradle
build-mc1.21.6-1.21.8.gradle
build-mc1.21.9-1.21.11.gradle
build-mc26.1.gradle
```

Gradle 9.4.1 does not accept the old `-b` short option. Use the `profileBuild`
selector instead:

```powershell
.\gradlew.bat "-PprofileBuild=build-mc1.21.0-1.21.4.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.5.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.6-1.21.8.gradle" compileJava
.\gradlew.bat "-PprofileBuild=build-mc1.21.9-1.21.11.gradle" compileJava
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
.\gradlew.bat "-PprofileBuild=build-mc26.1.gradle" compileJava
```

Quote the `-PprofileBuild=...` argument in PowerShell because the build file
names contain dots.

- For runtime `runClient` checks across multiple Minecraft profiles, launch profiles sequentially, one at a time. Wait until the user closes the current Minecraft window before starting the next profile. Do not start several profile clients in parallel because Gradle/Fabric Loom can fight over shared asset/cache locks and fail during `downloadAssets`.

Installed Java paths noted by the previous project setup:

- JDK 21: `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`
- JDK 25: `C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot`

## Architecture

```text
config/
  TrackerConfig

client/
  ResourceTrackerClient
  render/HudOverlay
  gui/MainScreen
  gui/EditScreen
  gui/SettingsScreen
  gui/HudMoveScreen

util/
  InventoryUtils
  RenderUtils

compat/
  mc1_21_0_to_1_21_4/java/.../VersionCompat + HudCompat
  mc1_21_5/java/.../VersionCompat + HudCompat
  mc1_21_6_to_1_21_8/java/.../VersionCompat + HudCompat
  mc1_21_9_to_1_21_11/java/.../VersionCompat + HudCompat
  mc26_1/java/.../VersionCompat + HudCompat
```

Key flow:

- `ResourceTrackerClient` loads config, registers keys, updates cached counts, and delegates HUD registration to `HudCompat`.
- `HudOverlay` renders cached counts only.
- `TrackerConfig` persists `resourcetracker.json` with GSON.
- `InventoryUtils` recursively counts inventory contents, containers, and bundles.
- `VersionCompat` owns key mapping registration, matrix operations, and registry id lookup differences.
- ModMenu is resolved from Modrinth Maven as `maven.modrinth:modmenu:<version>`.
- MC 26.1 uses profile-specific `GuiGraphicsExtractor` screens/HUD with UI parity
  against the 1.21.x screens where the 26.1 client API allows it.

## Non-Obvious Rules

- Write user-facing explanations, summaries, and completion reports in Russian unless the user asks for another language.
- Never call `InventoryUtils.countItems()` from render code; counting happens in the tick handler.
- All stored colors are ARGB `0xAARRGGBB`.
- `EditScreen.close()` is the save point for that screen.
- `columns = 0` means auto-layout. Fixed user-selected columns are `1` through `5`.
- Keep all Codex and local workflow files in `Workflow/`. Create new plans, scratch notes, session reports, inventories, and progress logs only in that directory.
- Keep `Workflow/` ignored in `.gitignore` so local work does not reach GitHub.
- Do not delete working files when a task finishes. If every item in a plan file is complete, rename that file inside `Workflow/` to include `done` and the completion date, for example `Workflow/PLAN_1_21_X_BUGFIXES.done-2026-05-04.md`.
- Before reporting completion, check for new working files outside `Workflow/`. Move them into `Workflow/` or explain why the file belongs in the project.
- Do not add generated analysis, cache, assistant settings, or local tool output to GitHub unless the user asks for that artifact.

## Workflow Directory Rules

Use `Workflow/` for local planning, QA evidence, notes, generated work files, and assistant scratch files. Keep production code, resources, Gradle files, and release artifacts outside `Workflow/` only when they belong in the mod or the user asks to publish them.

### Root

- Do not add new working files directly under `Workflow/`.
- Root-level files are only for a future `README.md`, `INBOX.md`, or legacy files that already exist.
- When editing an old root-level checklist or plan, move it to the correct subfolder first.

### `Workflow/plans/`

Use this folder for implementation plans, roadmaps, task breakdowns, and active TODO lists.

- Name new plans as `PLAN_<scope>.md`.
- Use uppercase words and underscores for the scope: `PLAN_1.3.3.md`, `PLAN_MC26_1_PORT.md`, `PLAN_UI_RESPONSIVE_FIXES.md`.
- Keep checkboxes in plans until the task closes.
- When every item in a plan is complete, rename it to `PLAN_<scope>.done-YYYY-MM-DD.md`.
- Do not delete finished plans.

### `Workflow/notes/`

Use this folder for durable project notes that should survive across sessions.

- Use `CODEBASE_MEMORY.md` for broad architecture notes and facts worth reusing.
- Use `NOTE_<topic>.md` for new focused notes.
- Use `<topic>_progress.md` only for active porting or migration logs.
- Use `<topic>_transition.md` for API or version transition notes.
- Keep temporary reasoning out of `notes/` unless it will help a later session.

### `Workflow/qa/`

Use this folder for QA triage files that do not fit a specific test plan or log run.

- Keep temporary bug intake in `BUGS_TEMP.md`.
- Move confirmed bug work from `BUGS_TEMP.md` into a plan under `Workflow/plans/`.
- Put manual checklists and test plans in `Workflow/qa/test-plans/`.
- Put logs and crash reports in `Workflow/qa/test-logs/`.

### `Workflow/qa/test-plans/`

Use this folder for manual QA checklists, test matrices, and verification notes.

- Name release checklists as `CHECKLIST_<version>.md`, for example `CHECKLIST_1.3.3.md`.
- Name broader test plans as `TEST_PLAN_<scope>_YYYY-MM-DD.md`.
- Use Markdown checkboxes: `- [ ]` and `- [x]`.
- Include exact profile names when a check differs by Minecraft version.

### `Workflow/qa/test-logs/`

Use this folder for captured logs from Gradle, `runClient`, crash reports, and manual QA runs.

- Create one folder per run: `YYYYMMDD-HHMMSS`.
- Inside a run folder, group files by profile id: `mc1_21_0_to_1_21_4`, `mc1_21_5`, `mc1_21_6_to_1_21_8`, `mc1_21_9_to_1_21_11`, `mc26_1`.
- Use stable log names: `prepare.out.log`, `prepare.err.log`, `gradle-run.out.log`, `gradle-run.err.log`, `latest.log`, `debug.log`.
- Copy crash reports into a `crash-reports/` subfolder under the affected profile.
- Do not mix logs from different run times in the same timestamp folder.

### `Workflow/sessions/`

Use this folder for session summaries and handoff notes.

- Name session files as `SESSION_<YYYY-MM-DD>_<scope>.md`.
- Use `SESSION_RESULTS_<YYYY-MM-DD>.md` for full-day result summaries.
- If several summaries share a date, add a short scope or suffix: `SESSION_2026-05-11_1.3.3-ui.md`.
- Include commands run, profiles checked, files changed, and remaining risks.

### `Workflow/releases/`

Use this folder for release notes, changelog drafts, and store or GitHub release text.

- Name user-facing release notes as `RELEASE_NOTES_<version>.md`.
- Put platform-specific drafts in `Workflow/releases/release-notes/`.
- Name GitHub release drafts as `v<version>.github.md`.
- For changelogs or release notes, use `$changelog-generator` first, then run `$stop-slop`.

### `Workflow/assets/`

Use this folder for draft assets, generated images, SVG sources, and design experiments.

- Keep icon SVG sources in `Workflow/assets/icons/svg/`.
- Name source assets after the target icon or feature: `settings.svg`, `hud_preview.png`.
- Move assets into `src/main/resources/` only when the mod uses them.
- Keep generated variants in `Workflow/assets/` unless the user asks to publish them.

### `Workflow/tools/`

Use this folder for local helper configs and assistant-only tools.

- Keep MCP config examples under `Workflow/tools/mcp/`.
- Put one-off helper scripts here unless they become project build tooling.
- If a script affects production builds or releases, move it to the proper project folder and explain why.
- Keep `Workflow/tools/run-client-profile.bat` in sync with supported run profiles. Update it when a new Minecraft profile appears, a profile build file is renamed, a `runClient` command changes, or a profile needs a different Java path.

### Naming Rules

- Markdown docs use `.md`.
- Logs use `.log`.
- Run folders use `YYYYMMDD-HHMMSS`.
- Done plans use `.done-YYYY-MM-DD.md`.
- Avoid spaces in new file and folder names.
- Use lowercase for tool/config folders and uppercase prefixes for workflow documents: `PLAN_`, `CHECKLIST_`, `TEST_PLAN_`, `NOTE_`, `SESSION_`, `RELEASE_NOTES_`.

