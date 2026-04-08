---
description: Full verification loop — compile, check imports, validate config, review patterns
---

Run all checks sequentially. Stop and fix on first failure before proceeding.

1. `./gradlew compileJava 2>&1` — must compile clean
2. Grep all Java files for unused imports — remove any found, recompile
3. Verify TrackerConfig.java colors are all ARGB (bit 24-31 must be non-zero in defaults)
4. Verify en_us.json and ru_ru.json have identical key sets
5. Verify fabric.mod.json has `"environment": "client"` and no `"main"` entrypoint
6. Grep for `new ItemStack(` in render/ and gui/ — should only appear in TrackedItem.getStack()
7. Grep for `TrackerConfig.save()` in setChangedListener lambdas — should be zero matches
8. Report: "All checks passed" or list failures
