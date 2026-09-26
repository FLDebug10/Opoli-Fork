package dev.overgrown.apoli.client;

import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.effects.CustomEffectNetworking;
import dev.overgrown.apoli.effects.CustomEffectRegistry;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class SyncCustomEffectRegistry {
    private SyncCustomEffectRegistry() {}

    public static void sync(CustomEffectNetworking.SyncCustomEffectsPayload payload, IPayloadContext context) {
        boolean success;
        try {
            CustomEffectRegistry.install(payload.toEffects());
            success = true;
        } catch (RuntimeException e) {
            Apoli.LOGGER.error("[Apoli] Could not apply the server's custom effects", e);
            success = false;
        }
        context.reply(new CustomEffectNetworking.SyncCustomEffectsResponsePayload(success));
    }
}
