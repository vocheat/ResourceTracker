---
description: Sync en_us.json and ru_ru.json localization keys
---

1. Read both files from `src/main/resources/assets/resourcetracker/lang/`
2. Find keys present in one but missing in the other
3. Add missing Russian keys with English value prefixed "[EN] "
4. Add missing English keys with Russian value prefixed "[RU] "
5. Report what was added
