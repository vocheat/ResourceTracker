---
description: Quick compile check without full JAR packaging
---

1. Run `./gradlew compileJava 2>&1`
2. If errors — show each with file:line and a one-line fix suggestion
3. If clean — respond "Compilation OK"
