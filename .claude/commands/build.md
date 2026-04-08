---
description: Compile the mod and fix any errors
---

1. Run `./gradlew compileJava 2>&1` and capture output
2. If compilation fails — read error messages, fix the source files, and recompile. Repeat until clean.
3. Then run `./gradlew build 2>&1` to produce the final JAR
4. Report: "Build OK — JAR at build/libs/" or list remaining errors
