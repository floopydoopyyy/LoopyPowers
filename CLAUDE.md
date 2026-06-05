# Loopypowers — Fabric to NeoForge Parity

## Overview
Both the Fabric and NeoForge versions are fully functional. This is not a
bug-fix task — it is a feature/content parity task. The NeoForge version has
received additions and improvements that the Fabric version does not yet have.
The goal is to bring the Fabric version up to the same state as NeoForge.

Do not assume something is broken in the Fabric version. Read both versions
and identify what NeoForge has that Fabric does not, then add it.

## Project locations
**Fabric (target):** Current working directory
**NeoForge (reference):** `C:\Users\olive\Documents\things\MDK-1.21.1-NeoGradle-main\src\main\java\com\loopy\loopypowers`
NeoForge resources: `C:\Users\olive\Documents\things\MDK-1.21.1-NeoGradle-main\src\main\resources`

Read the NeoForge version as the source of truth for what the Fabric version
should look like when this task is complete.

# General workflow rules

- **One item at a time.** Do not batch multiple classes or files in a single pass.
- **List differences before editing.** For every file, read both versions fully,
  state every difference found, and confirm before making any changes.
- **Both versions work.** Do not assume something is broken. If the Fabric
  version does something differently but achieves the same result, flag it
  rather than blindly overwriting it.
- **Mark checklist items done** as each is completed so progress is tracked
  across sessions. Remove any spare notes next to a checklist entry once
  that area is completed.
- **When an entire section is complete, remove it from CLAUDE.md** for
  clarity and to save context space.
