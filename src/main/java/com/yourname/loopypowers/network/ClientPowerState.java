package com.yourname.loopypowers.network;
// registers some powers client side so the client doesn't battle their passives.
public final class ClientPowerState {
    private ClientPowerState() {}

    private static volatile boolean hasStrengthPower = false;

    public static boolean hasStrengthPower() {
        return hasStrengthPower;
    }

    public static void setStrengthPower(boolean v) {
        hasStrengthPower = v;
    }
}