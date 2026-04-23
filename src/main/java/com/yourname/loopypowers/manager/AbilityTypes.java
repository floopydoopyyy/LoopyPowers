package com.yourname.loopypowers.manager;

public enum AbilityTypes { // COLOUR CODES FOR ABILITY ON UI

    PRIMARY("P", "§b"),     // light blue
    SECONDARY("S", "§9"),   // darker blue
    ULTIMATE("U", "§5§l");  // bold purple

    public final String shortName;
    public final String color;

    AbilityTypes(String shortName, String color) {
        this.shortName = shortName;
        this.color = color;
    }
}