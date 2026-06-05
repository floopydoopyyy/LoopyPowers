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

---

# SECTION A — Known Parity Gaps (fix these first)

These are confirmed differences that were missed during the initial parity
pass. Fix each one, then do a broader check of the surrounding power/class
to catch anything else that was missed at the same time.

## A1. HealingPower — missing phase-specific ultimate particle FX
The Fabric version is missing phase-specific particle effects for the healing
ultimate. The NeoForge version has distinct particles per phase.

**What to do:**
1. Read the NeoForge `HealingPower.java` and its client handler
   (`HealingUltPayload`, `HealingFxClient` or equivalent)
2. Identify each phase and its corresponding particle events
3. Check what the Fabric version currently sends/renders for the ult
4. Add the missing phase-specific payload sends to the Fabric power class
5. Update the Fabric client handler to render the correct particles per phase
6. While in this file, do a full re-check of HealingPower for any other
   missed differences and fix those too

- [x] HealingPower phase-specific ult particles fixed
- [x] HealingPower full re-check complete

---

## A2. FlightPower — most particle FX incorrect
The Fabric version has most flight particle effects implemented incorrectly
compared to the NeoForge version.

**What to do:**
1. Read the NeoForge `FlightPower.java` and ALL its client handler files
   (FlightBoomDashPayload, FlightBoomImpactPayload, FlightBoomWindupPayload,
   and any other flight-related payloads and their handlers)
2. Read every Fabric flight particle payload and client handler
3. Do a thorough comparison — positions, particle types, counts, spreads,
   velocities, timing, conditions that trigger each effect
4. Fix every discrepancy found — do not just fix the obvious ones
5. While in this file, do a full re-check of FlightPower for any other
   missed differences and fix those too

- [x] FlightPower particle FX corrected
- [x] FlightPower full re-check complete

---

## A3. NaturePower — poison gas is boxy instead of rounded
The poison gas effect in the Fabric version spawns particles in a box/cube
shape. The NeoForge version uses a rounded/cylindrical/spherical distribution.

**What to do:**
1. Read the NeoForge `NatureGasFxPayload` and its client handler to see
   exactly how it distributes particles (likely uses radius + angle math
   rather than flat dx/dy/dz spread)
2. Read the Fabric equivalent client handler
3. Update the Fabric client handler to use the same distribution math as
   NeoForge — matching radius, height, density, and particle type exactly
4. While in this file, do a full re-check of NaturePower for any other
   missed differences and fix those too

- [x] NaturePower poison gas rounded correctly
- [x] NaturePower full re-check complete

---

## A4. SoundPower — multiple areas different, especially secondary
SoundPower has significant differences between versions, particularly the
secondary ability. Treat this as a near-full re-port of the class.

**What to do:**
1. Read both versions of `SoundPower.java` in full side by side
2. List every difference found — abilities, logic, values, effects, payloads
3. Pay particular attention to the secondary ability — compare every line
4. Check ALL sound-related payloads and client handlers in NeoForge vs Fabric:
   SoundBassBurstPayload, SoundBassPullPayload, SoundBassPullAnimatePayload,
   SoundBassBlastAnimatePayload, SoundBassPulsePayload, SoundUltimateBeamPayload
   and their corresponding client handlers
5. Fix every difference found — do not skip anything marked as minor
6. Confirm the secondary ability behaviour matches NeoForge exactly

- [x] SoundPower secondary ability corrected
- [x] SoundPower all other differences fixed
- [x] SoundPower payload/client handler audit complete

---

## A5. StrengthPower — ultimate sound ticks not working
The Fabric version's ultimate sound tick logic is not functioning correctly.
The NeoForge version ticks sounds during the ultimate correctly.

**What to do:**
1. Read the NeoForge `StrengthPower.java` and identify how ultimate sound
   ticks are triggered (look for recurring sound sends during the ult duration)
2. Read the Fabric version and identify where the equivalent code is and
   why it is not working — wrong tick condition, missing payload, handler
   not registered, etc.
3. Fix the root cause — match the NeoForge approach exactly
4. While in this file, do a full re-check of StrengthPower for any other
   missed differences and fix those too

- [x] StrengthPower ult sound ticks fixed
- [x] StrengthPower full re-check complete

# SECTION B — Lang Entries
**Goal:** Fabric has missing areas/language entries where NeoForge uses
translatable text.

### Where they live
NeoForge: References across all areas including power classes, items, rituals, commands etc.
Fabric: References across all areas including power classes, items, rituals, commands etc.
Lang file across both: `resources/assets/loopypowers/lang/en_us.json`

The lang file can and should be identical across both versions.

### What to do
1. Transfer the more modern NeoForge lang file to replace the older Fabric version
2. Open the lang file
3. Check each entry against the corresponding class/file and ensure the
   reference is used and correct
4. If some entries are missing from their class, implement them

### LANG checklist
- [x] All power text is correctly in place
- [x] All item text is correctly in place
- [x] All effect text is correctly in place
- [x] All entity related text is correctly in place
- [x] All subtitle text is correctly in place
- [x] All death messages are correctly in place
- [x] All remaining text is correctly in place

# SECTION C — Final checks
**Goal:** Fabric had some missing areas on the first sweep, check each class with major changes to see if all areas have complete parity.

### Where they live
Both files have powers in the 'power' package.

After fixing the five known issues above, do a sweep of the remaining power
classes that were already marked complete to catch anything else that was
missed. For each power, quickly re-read both versions and flag any remaining
differences before closing out this section.

Powers to re-check:
- [x] FortunePower re-check
- [x] CosmicPower re-check
- [x] DarknessPower re-check
- [x] IcePower re-check
- [x] FirePower re-check
- [x] ExplosionPower re-check
- [x] LightningPower re-check
- [x] PsychicPower re-check
- [x] SpeedPower re-check
- [x] TelekinesisPower re-check
- [x] TeleportationPower re-check

---

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
