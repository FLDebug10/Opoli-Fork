package dev.overgrown.apoli.client;

import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.effects.CustomEffectNetworking;
import dev.overgrown.apoli.effects.CustomEffectRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class SyncCustomEffectRegistry {
    private static volatile CustomEffectNetworking.SyncCustomEffectsPayload pendingConfiguration;

    private SyncCustomEffectRegistry() {}

    public static void onConfiguration(CustomEffectNetworking.SyncCustomEffectsPayload payload) {
        pendingConfiguration = payload;
    }

    public static void onConfigurationComplete() {
        CustomEffectNetworking.SyncCustomEffectsPayload payload = pendingConfiguration;
        pendingConfiguration = null;
        if (payload != null) apply(payload);
    }

    public static void onDisconnect() {
        pendingConfiguration = null;
    }

    public static void onPlay(CustomEffectNetworking.SyncCustomEffectsPayload payload, ClientPlayNetworking.Context context) {
        boolean success = apply(payload);
        context.responseSender().sendPacket(new CustomEffectNetworking.SyncCustomEffectsResponsePayload(success));
    }

    private static boolean apply(CustomEffectNetworking.SyncCustomEffectsPayload payload) {
        try {
            CustomEffectRegistry.install(payload.toEffects());
            return true;
        } catch (RuntimeException e) {
            Apoli.LOGGER.error("[Apoli] Could not apply the server's custom effects", e);
            return false;
        }
    }
}
