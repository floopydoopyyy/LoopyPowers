package com.yourname.loopypowers.client;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
// requests to hide playermodels from others view

public final class HiddenPlayersClient {
    private static final Int2IntOpenHashMap HIDDEN = new Int2IntOpenHashMap();

    private HiddenPlayersClient() {}

    public static void hide(int entityId, int ticks) {
        int cur = HIDDEN.getOrDefault(entityId, 0);
        if (ticks > cur) HIDDEN.put(entityId, ticks);
    }

    public static boolean isHidden(int entityId) {
        return HIDDEN.getOrDefault(entityId, 0) > 0;
    }

    public static void tick() {
        if (HIDDEN.isEmpty()) return;

        var it = HIDDEN.int2IntEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            int left = e.getIntValue() - 1;
            if (left <= 0) it.remove();
            else e.setValue(left);
        }
    }
}
