# Codex Project Notes

## Project

ResourceTracker is a client-side Minecraft Fabric mod that tracks resource
collection progress on the HUD.

- Java package root: `net.fabricmc.resourcetracker`
- Main target: Minecraft 1.21.11
- Main source compatibility: `src/compat/mc1_21_9_to_1_21_11`
- Java: JDK 21 for Minecraft 1.21.x; use JDK 25 only for MC 26.x profiles
- No test suite exists; compilation is the normal automated check

## Working Style

- Work in the current checkout by default. Use a branch or worktree only when
  the user asks for isolation or the change clearly needs it.
- Make the smallest change that completes the request. Preserve unrelated user
  edits and existing behavior.
- Do not create plans, reports, logs, or delegation tasks unless they are
  genuinely needed for the work or the user asks for them.
- Do not commit, push, publish, or create a pull request unless the user asks.
- Reply to the user in Russian unless they request another language.
- Keep temporary workflow artifacts in `Workflow/` when they are needed.

## Product Constraints

- Count inventory items in the tick/update path. Never call
  `InventoryUtils.countItems()` from render code.
- Stored colors use ARGB format: `0xAARRGGBB`.
- `columns = 0` means automatic layout; values `1` through `5` are fixed
  layouts.
- `EditScreen.onClose()` is the save point for that screen.
- Keep version-specific code in its matching `src/compat/` profile and preserve
  parity between screens where the APIs allow it.

## Verification

- For documentation-only changes, run `git diff --check` and review the diff.
- For Java changes, compile the affected profile. The usual command is:
  `.\gradlew.bat compileJava`
- Compile additional profiles only when the change affects them or the user
  requests it.
- Use `profileBuild` for alternate build files; do not use Gradle's old `-b`
  option. Runtime `runClient` checks are sequential when they are needed.
