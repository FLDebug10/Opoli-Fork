package dev.overgrown.apoli.client;

import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.effects.CustomEffectNetworking;
import dev.overgrown.apoli.effects.CustomEffectRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

public final class SyncCustomEffectRegistry {
    private SyncCustomEffectRegistry() {}

    public static void sync(Minecraft client, FriendlyByteBuf buf, PacketSender sender) {
        CustomEffectNetworking.SyncCustomEffectsPacket packet = new CustomEffectNetworking.SyncCustomEffectsPacket(buf);
        client.execute(() -> {
            boolean success;
            try {
                CustomEffectRegistry.install(packet.toEffects());
                success = true;
            } catch (RuntimeException e) {
                Apoli.LOGGER.error("[Apoli] Could not apply the server's custom effects", e);
                success = false;
            }
            sender.sendPacket(new CustomEffectNetworking.CustomEffectResponsePacket(success));
        });
    }
}
