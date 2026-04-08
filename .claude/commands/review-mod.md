---
description: Audit codebase for common Minecraft Fabric mod issues
---

Read all Java files in src/main/java/ and check:

1. **Render-thread allocations**: `new ItemStack()`, `Registries.ITEM.get()`, `item.getName().getString()` in render methods — use TrackedItem caching instead
2. **Registry null checks**: `Registries.ITEM.get()` returns AIR, never null — use `isValid()` not `!= null`
3. **Color format**: all colors must be ARGB (0xAARRGGBB), not RGB
4. **Code duplication**: drawBox, shortenText, getCountText, drawStyledScrollbar — must use RenderUtils
5. **Config I/O in listeners**: `TrackerConfig.save()` must NOT be in text field change listeners
6. **Localization sync**: en_us.json and ru_ru.json must have identical keys
7. **Unused imports**

Then run `./gradlew compileJava 2>&1` to verify compilation.

Report as table: file | line | severity | issue.
