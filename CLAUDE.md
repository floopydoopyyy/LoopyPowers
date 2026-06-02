# Loopypowers Fabric — Particle Payload Migration

## Project info
Fabric mod, Minecraft 1.21.1. Main package: `com.yourname.loopypowers`
Mod ID: `loopypowers`

## The task
Every significant particle spawning call in the `power/` and `ritual/` packages
currently runs server-side. This crashes on dedicated servers because particle
calls require a client world. The fix is to send a network payload to the client
and spawn the particles there instead.

## Package structure
```
src/main/java/com/yourname/loopypowers/
  power/          ← power classes, each has particle calls to migrate
  ritual/         ← ritual classes, same problem
  network/        ← payloads go here
    payload/      ← one payload record per logical particle event
  client/
    fx/           ← one *FxClient.java handler per power/ritual
```

## What counts as a "significant" particle event
Migrate these:
- Any `world.spawnParticles(...)` or `serverWorld.spawnParticles(...)` call
- Any `world.addParticle(...)` called from server-side code
- Particle bursts, trails, auras, rings, or impact effects

Do NOT migrate:
- `world.playSound(...)` — sounds are fine server-side
- Block place/break particles — vanilla handles these
- Single-particle debug calls

## Step-by-step process for each power/ritual

### 1. Read the source file
Identify every particle call. Group calls that always fire together into a
single payload (e.g. a burst that spawns 3 different particle types at once
is one payload, not three).

### 2. Create a payload record
Location: `src/main/java/com/yourname/loopypowers/network/payload/`
Naming: `{Power}FxPayload.java` for simple powers, or named by event e.g.
`SpeedDashCastPayload.java` if a power has many distinct events.

Template:
```java
package com.yourname.loopypowers.network.payload;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ExamplePayload(double x, double y, double z) implements CustomPayload {

    public static final Id<ExamplePayload> ID =
            new Id<>(Identifier.of("loopypowers", "example"));

    public static final PacketCodec<PacketByteBuf, ExamplePayload> CODEC =
            PacketCodec.of(ExamplePayload::write, ExamplePayload::new);

    public ExamplePayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
```

Pass exactly the data the client needs to reproduce the particles — position,
entity ID (client looks up position), velocity direction, intensity, seed, etc.
Do not pass server-only objects.

### 3. Register the payload (server side)
In `AbilityPackets.java` (or equivalent network registration class), add:
```java
PayloadTypeRegistry.playS2C().register(ExamplePayload.ID, ExamplePayload.CODEC);
```
This must be called from the common initializer (`onInitialize`), not client-only code.

### 4. Send the payload from the power/ritual class
Replace the particle call(s) with a network send. For nearby players:
```java
// Replace this:
world.spawnParticles(ParticleTypes.FLAME, x, y, z, 20, 0.3, 0.3, 0.3, 0.05);

// With this (send to all players who can see the area):
PlayerLookup.tracking(serverWorld, new BlockPos((int)x, (int)y, (int)z)).forEach(p ->
    ServerPlayNetworking.send(p, new ExamplePayload(x, y, z))
);

// Or for a single target player:
ServerPlayNetworking.send(targetPlayer, new ExamplePayload(x, y, z));
```

### 5. Create the client handler
Location: `src/main/java/com/yourname/loopypowers/client/fx/`
Naming: `{Power}FxClient.java`
Annotate with `@Environment(EnvType.CLIENT)` — strips it from the server jar.

Template:
```java
package com.yourname.loopypowers.client.fx;

import com.yourname.loopypowers.network.payload.ExamplePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;

@Environment(EnvType.CLIENT)
public final class ExampleFxClient {

    private ExampleFxClient() {}

    public static void handle(ExamplePayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            world.addParticle(ParticleTypes.FLAME,
                    payload.x(), payload.y(), payload.z(),
                    0.0, 0.0, 0.0);
        });
    }
}
```

### 6. Register the client handler
In `LoopypowersClient.java` (the `ClientModInitializer`):
```java
ClientPlayNetworking.registerGlobalReceiver(ExamplePayload.ID, ExampleFxClient::handle);
```

## Important: spawnParticles vs addParticle
Server-side `ServerWorld.spawnParticles(type, x, y, z, count, dx, dy, dz, speed)`
spawns `count` particles with random spread. Client-side `ClientWorld.addParticle`
only spawns one at a time — reproduce the same spread by calling it in a loop
with random offsets matching the original dx/dy/dz values. Use the same Random
seed if the original used one for deterministic effects.

## Fabric-specific notes
- `@Environment(EnvType.CLIENT)` on a class = completely absent from server jar.
- `PlayerLookup` is from `net.fabricmc.fabric.api.lookup.v1.entity` — use it
  to find players tracking a position rather than iterating all players.
- `ctx.client().execute(() -> { ... })` ensures particle code runs on the render thread.
- Unlike NeoForge, Fabric does NOT require channels declared on both sides —
  `PayloadTypeRegistry.playS2C().register()` on the server and
  `ClientPlayNetworking.registerGlobalReceiver()` on the client is all you need.
- No `PayloadInit`, no `ClientPayloadRegistry` — Fabric is much simpler here.

## Workflow: one power at a time
Do powers in order. After each one compiles and the particles work in-game,
mark it complete and move to the next. Do not batch multiple powers in one pass
— particle-heavy classes are large and context gets unwieldy.

## Completed
- [x] BloodPower
- [x] CosmicPower
- [x] DarknessPower
- [x] DimensionalPower
- [x] ExplosionPower
- [x] FirePower
- [ ] FlightPower
- [x] FortunePower
- [x] HealingPower
- [x] IcePower
- [x] LightningPower
- [ ] NaturePower
- [ ] PsychicPower
- [ ] SoundPower
- [ ] SpeedPower
- [ ] StrengthPower
- [ ] TelekinesisPower
- [ ] TeleportPower
- [ ] Rituals (after all powers done)
