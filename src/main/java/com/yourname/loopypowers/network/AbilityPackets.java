package com.yourname.loopypowers.network;

import com.yourname.loopypowers.Loopypowers;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.power.FlightPower;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

//this class registers packets to server

public class AbilityPackets {
    // SERVER PACKETS
    public static final Identifier PRIMARY_ABILITY =
            new Identifier(Loopypowers.MOD_ID, "primary_ability");
    public static final Identifier SECONDARY_ABILITY =
            new Identifier(Loopypowers.MOD_ID, "secondary_ability");
    public static final Identifier ULTIMATE_ABILITY =
            new Identifier(Loopypowers.MOD_ID, "ultimate_ability");
    public static final Identifier CAMERA_SHAKE =
            new Identifier(Loopypowers.MOD_ID, "camera_shake");
    public static final Identifier FLIGHT_GLIDE_REQUEST =
            new Identifier(Loopypowers.MOD_ID, "flight_glide_request");
    public static final Identifier TOGGLE_PASSIVE =
            new Identifier(Loopypowers.MOD_ID, "toggle_passive");

    // CLIENT PACKETS
    public static final Identifier RESONANCE_TRAIL =
            new Identifier(Loopypowers.MOD_ID, "resonance_trail");
    public static final Identifier RESONANCE_RING  =
            new Identifier(Loopypowers.MOD_ID, "resonance_ring");
    public static final Identifier RESONANCE_LINE =
            new Identifier(Loopypowers.MOD_ID, "resonance_line");
    public static final Identifier HIDE_PLAYER =
            new Identifier(Loopypowers.MOD_ID, "hide_player");
    public static final Identifier STUN_AUDIO =
            new Identifier(Loopypowers.MOD_ID, "stun_audio");
    public static final Identifier SYNC_STRENGTH_POWER =
            new Identifier(Loopypowers.MOD_ID, "sync_strength_power");

    // server receiver
    public static void registerServer() {
        //Primary reciever
        ServerPlayNetworking.registerGlobalReceiver(
                PRIMARY_ABILITY,
                (server, player, handler, buf, responseSender) -> {
                    server.execute(() -> {
                        PowerManager.usePrimary(player);
                    });
                }
        );
        //Secondary reciever
        ServerPlayNetworking.registerGlobalReceiver(
                SECONDARY_ABILITY,
                (server, player, handler, buf, responseSender) -> {
                    server.execute(() -> {
                        PowerManager.useSecondary(player);
                    });
                }
        );
        ServerPlayNetworking.registerGlobalReceiver(
                ULTIMATE_ABILITY,
                (server, player, handler, buf, responseSender) -> {
                    server.execute(() -> {
                        PowerManager.useUltimate(player);
                    });
                }
        );



        ServerPlayNetworking.registerGlobalReceiver(FLIGHT_GLIDE_REQUEST, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                // only attempt if they have flight
                var p = PowerManager.getPower(player);
                if (!(p instanceof FlightPower)) return; // kinda obsolete

                // request to fly next tick
                player.getCommandTags().add("fl_glide_req");
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(
                AbilityPackets.TOGGLE_PASSIVE,
                (server, player, handler, buf, responseSender) -> {
                    server.execute(() -> {
                        PassiveManager.toggle(player);
                    });
                }
        );
    }
}
