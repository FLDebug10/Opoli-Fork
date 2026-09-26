package dev.overgrown.apoli.network;

import dev.overgrown.apoli.network.payload.ProtocolVersionPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtocolCompat {

    public static final int VERSION = 13;

    private static final Set<UUID> SENT_LEGACY = ConcurrentHashMap.newKeySet();

    private ProtocolCompat() {}

    public static boolean useLegacyFormats(ServerPlayer player) {
        return !ServerPlayNetworking.canSend(player, ProtocolVersionPayload.CHANNEL);
    }

    public static void markSentLegacy(ServerPlayer player) {
        SENT_LEGACY.add(player.getUUID());
    }

    public static boolean consumeSentLegacy(ServerPlayer player) {
        return SENT_LEGACY.remove(player.getUUID());
    }

    public static void forget(UUID player) {
        SENT_LEGACY.remove(player);
    }
}
