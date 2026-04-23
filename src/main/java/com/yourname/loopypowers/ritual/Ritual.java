package com.yourname.loopypowers.ritual;

import net.minecraft.server.world.ServerWorld;

public interface Ritual {
    boolean tick(ServerWorld world); // return true when finished
}