package dev.overgrown.apoli;

import dev.overgrown.apoli.effects.CustomEffectNetworking;
import dev.overgrown.apoli.network.ProtocolCompat;
import dev.overgrown.apoli.network.payload.ApplyVelocityS2C;
import dev.overgrown.apoli.network.payload.BuySkillC2S;
import dev.overgrown.apoli.network.payload.DisguiseUpdateS2C;
import dev.overgrown.apoli.network.payload.KeyHeldC2S;
import dev.overgrown.apoli.network.payload.ProtocolVersionPayload;
import dev.overgrown.apoli.network.payload.SkillDefsSyncS2C;
import dev.overgrown.apoli.network.payload.SkillStateSyncS2C;
import dev.overgrown.apoli.skill.SkillData;
import dev.overgrown.apoli.skill.SkillDataAttachment;
import dev.overgrown.apoli.network.payload.PowerActivatedS2C;
import dev.overgrown.apoli.network.payload.PowerActivationC2S;
import dev.overgrown.apoli.network.payload.PowerToggleC2S;
import dev.overgrown.apoli.network.payload.RopeChangeLengthC2S;
import dev.overgrown.apoli.network.payload.RopeCreateS2C;
import dev.overgrown.apoli.network.payload.RopeDeleteS2C;
import dev.overgrown.apoli.network.payload.RopeSwingC2S;
import dev.overgrown.apoli.network.payload.RopeVerletLengthS2C;
import dev.overgrown.apoli.network.PowerSyncCache;
import dev.overgrown.apoli.network.payload.SyncEntityPowersS2C;
import dev.overgrown.apoli.network.payload.SyncAuxIntsS2C;
import dev.overgrown.apoli.network.payload.SyncResourceTablesS2C;
import dev.overgrown.apoli.network.payload.SyncKeybindsS2C;
import dev.overgrown.apoli.network.payload.SyncPowersChunkS2C;
import dev.overgrown.apoli.network.payload.SyncPowersS2C;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class ApoliNetwork {
    private ApoliNetwork() {}

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(ProtocolVersionPayload.TYPE, ProtocolVersionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ProtocolVersionPayload.TYPE, ProtocolVersionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncPowersS2C.TYPE, SyncPowersS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncPowersChunkS2C.TYPE, SyncPowersChunkS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncEntityPowersS2C.TYPE, SyncEntityPowersS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncResourceTablesS2C.TYPE, SyncResourceTablesS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncAuxIntsS2C.TYPE, SyncAuxIntsS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(dev.overgrown.apoli.network.payload.DevModeS2C.TYPE,
            dev.overgrown.apoli.network.payload.DevModeS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(dev.overgrown.apoli.network.payload.SyncEntitySetsS2C.TYPE,
            dev.overgrown.apoli.network.payload.SyncEntitySetsS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PowerActivatedS2C.TYPE, PowerActivatedS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncKeybindsS2C.TYPE, SyncKeybindsS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ApplyVelocityS2C.TYPE, ApplyVelocityS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(dev.overgrown.apoli.network.payload.TickRateS2C.TYPE,
            dev.overgrown.apoli.network.payload.TickRateS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(DisguiseUpdateS2C.TYPE, DisguiseUpdateS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(dev.overgrown.apoli.network.payload.ScaleSyncS2C.TYPE,
            dev.overgrown.apoli.network.payload.ScaleSyncS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(dev.overgrown.apoli.network.payload.TextDisplayS2C.TYPE,
            dev.overgrown.apoli.network.payload.TextDisplayS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(dev.overgrown.apoli.network.payload.LabelUpdateS2C.TYPE,
            dev.overgrown.apoli.network.payload.LabelUpdateS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(dev.overgrown.apoli.network.payload.ForceKeyS2C.TYPE,
            dev.overgrown.apoli.network.payload.ForceKeyS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(dev.overgrown.apoli.network.payload.SyncShaderS2C.TYPE,
            dev.overgrown.apoli.network.payload.SyncShaderS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SkillDefsSyncS2C.TYPE, SkillDefsSyncS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SkillStateSyncS2C.TYPE, SkillStateSyncS2C.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(BuySkillC2S.TYPE, BuySkillC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PowerActivationC2S.TYPE, PowerActivationC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PowerToggleC2S.TYPE, PowerToggleC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(dev.overgrown.apoli.network.payload.RequestSkillStateC2S.TYPE, dev.overgrown.apoli.network.payload.RequestSkillStateC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(dev.overgrown.apoli.network.payload.RefundSkillC2S.TYPE, dev.overgrown.apoli.network.payload.RefundSkillC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(KeyHeldC2S.TYPE, KeyHeldC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(dev.overgrown.apoli.network.payload.ScrollWheelC2S.TYPE,
            dev.overgrown.apoli.network.payload.ScrollWheelC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(dev.overgrown.apoli.network.payload.MouseMovementC2S.TYPE,
            dev.overgrown.apoli.network.payload.MouseMovementC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(
            dev.overgrown.apoli.network.payload.PlayerModelTypeC2S.TYPE,
            dev.overgrown.apoli.network.payload.PlayerModelTypeC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(
            dev.overgrown.apoli.network.payload.CameraPerspectiveC2S.TYPE,
            dev.overgrown.apoli.network.payload.CameraPerspectiveC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(dev.overgrown.apoli.network.payload.SpeechTriggerC2S.TYPE, dev.overgrown.apoli.network.payload.SpeechTriggerC2S.STREAM_CODEC);

        PayloadTypeRegistry.playS2C().register(dev.overgrown.apoli.network.payload.RadialMenuOpenS2C.TYPE,
            dev.overgrown.apoli.network.payload.RadialMenuOpenS2C.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(dev.overgrown.apoli.network.payload.RadialMenuSelectC2S.TYPE,
            dev.overgrown.apoli.network.payload.RadialMenuSelectC2S.STREAM_CODEC);

        PayloadTypeRegistry.playS2C().register(
            dev.overgrown.apoli.network.payload.PowerInventoryS2C.TYPE,
            dev.overgrown.apoli.network.payload.PowerInventoryS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(
            dev.overgrown.apoli.network.payload.MountOffsetS2C.TYPE,
            dev.overgrown.apoli.network.payload.MountOffsetS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RopeCreateS2C.TYPE, RopeCreateS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RopeDeleteS2C.TYPE, RopeDeleteS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RopeVerletLengthS2C.TYPE, RopeVerletLengthS2C.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RopeChangeLengthC2S.TYPE, RopeChangeLengthC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RopeSwingC2S.TYPE, RopeSwingC2S.STREAM_CODEC);
        PayloadTypeRegistry.configurationS2C().register(CustomEffectNetworking.SyncCustomEffectsPayload.TYPE, CustomEffectNetworking.SyncCustomEffectsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CustomEffectNetworking.SyncCustomEffectsPayload.TYPE, CustomEffectNetworking.SyncCustomEffectsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CustomEffectNetworking.SyncCustomEffectsResponsePayload.TYPE, CustomEffectNetworking.SyncCustomEffectsResponsePayload.CODEC);
    }

    public static void broadcastRopeCreate(ServerLevel level, RopeCreateS2C payload) {
        for (ServerPlayer p : level.players()) ServerPlayNetworking.send(p, payload);
    }

    public static void broadcastRopeDelete(ServerLevel level, RopeDeleteS2C payload) {
        for (ServerPlayer p : level.players()) ServerPlayNetworking.send(p, payload);
    }

    public static void broadcastRopeVerletLength(ServerLevel level, RopeVerletLengthS2C payload) {
        for (ServerPlayer p : level.players()) ServerPlayNetworking.send(p, payload);
    }

    public static void sendPowers(ServerPlayer recipient) {
        if (!connected(recipient)) return;
        PowerSyncCache.sendTo(recipient);
        dev.overgrown.apoli.power.builtin.InventoryPower.syncAll(recipient);
        dev.overgrown.apoli.mount.MountOffsets.syncAll(recipient);
    }

    public static void sendPowerInventory(ServerPlayer recipient,
                                         dev.overgrown.apoli.network.payload.PowerInventoryS2C payload) {
        if (connected(recipient)
            && ServerPlayNetworking.canSend(recipient, dev.overgrown.apoli.network.payload.PowerInventoryS2C.TYPE)) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    public static void sendMountOffset(ServerPlayer recipient,
                                      dev.overgrown.apoli.network.payload.MountOffsetS2C payload) {
        if (connected(recipient)
            && ServerPlayNetworking.canSend(recipient, dev.overgrown.apoli.network.payload.MountOffsetS2C.TYPE)) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    public static void broadcastMountOffset(Entity passenger,
                                            dev.overgrown.apoli.network.payload.MountOffsetS2C payload) {
        for (ServerPlayer viewer : PlayerLookup.tracking(passenger)) sendMountOffset(viewer, payload);
        if (passenger instanceof ServerPlayer self) sendMountOffset(self, payload);
    }

    public static boolean connected(ServerPlayer recipient) {
        return recipient != null && recipient.connection != null;
    }

    public static void sendEntityPowers(ServerPlayer recipient, SyncEntityPowersS2C payload) {
        if (connected(recipient)) ServerPlayNetworking.send(recipient, payload);
    }

    public static void sendResourceTables(ServerPlayer recipient, SyncResourceTablesS2C payload) {
        if (connected(recipient) && ServerPlayNetworking.canSend(recipient, SyncResourceTablesS2C.TYPE)) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    public static boolean canSendAuxInts(ServerPlayer recipient) {
        return connected(recipient) && ServerPlayNetworking.canSend(recipient, SyncAuxIntsS2C.TYPE);
    }

    public static void sendAuxInts(ServerPlayer recipient, SyncAuxIntsS2C payload) {
        ServerPlayNetworking.send(recipient, payload);
    }

    public static void sendActivated(ServerPlayer recipient, PowerActivatedS2C payload) {
        if (connected(recipient)) ServerPlayNetworking.send(recipient, payload);
    }

    public static void sendDevMode(ServerPlayer recipient,
                                   dev.overgrown.apoli.network.payload.DevModeS2C payload) {
        if (connected(recipient)
            && ServerPlayNetworking.canSend(recipient, dev.overgrown.apoli.network.payload.DevModeS2C.TYPE)) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    public static void sendEntitySets(ServerPlayer recipient,
                                      dev.overgrown.apoli.network.payload.SyncEntitySetsS2C payload) {
        if (connected(recipient)
            && ServerPlayNetworking.canSend(recipient, dev.overgrown.apoli.network.payload.SyncEntitySetsS2C.TYPE)) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    public static void sendKeybinds(ServerPlayer recipient, SyncKeybindsS2C payload) {
        if (connected(recipient)) ServerPlayNetworking.send(recipient, payload);
    }

    public static void sendShader(ServerPlayer recipient, dev.overgrown.apoli.network.payload.SyncShaderS2C payload) {
        if (connected(recipient)
            && ServerPlayNetworking.canSend(recipient, dev.overgrown.apoli.network.payload.SyncShaderS2C.TYPE)) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    public static void sendScale(ServerPlayer recipient, dev.overgrown.apoli.network.payload.ScaleSyncS2C payload) {
        if (connected(recipient)
            && ServerPlayNetworking.canSend(recipient, dev.overgrown.apoli.network.payload.ScaleSyncS2C.TYPE)) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    public static void broadcastScale(Entity entity, dev.overgrown.apoli.network.payload.ScaleSyncS2C payload) {
        for (ServerPlayer viewer : PlayerLookup.tracking(entity)) sendScale(viewer, payload);
        if (entity instanceof ServerPlayer self) sendScale(self, payload);
    }

    public static void broadcastTickRate(MinecraftServer server, dev.overgrown.apoli.network.payload.TickRateS2C payload) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (connected(p)) ServerPlayNetworking.send(p, payload);
        }
    }

    public static void sendTickRateToTrackers(Entity entity, dev.overgrown.apoli.network.payload.TickRateS2C payload) {
        for (ServerPlayer viewer : PlayerLookup.tracking(entity)) {
            if (connected(viewer)) ServerPlayNetworking.send(viewer, payload);
        }
        if (entity instanceof ServerPlayer self && connected(self)) {
            ServerPlayNetworking.send(self, payload);
        }
    }

    public static void sendApplyVelocityToTrackers(Entity entity, ApplyVelocityS2C payload) {
        for (ServerPlayer viewer : PlayerLookup.tracking(entity)) {
            if (connected(viewer)) ServerPlayNetworking.send(viewer, payload);
        }
        if (entity instanceof ServerPlayer self && connected(self)) {
            ServerPlayNetworking.send(self, payload);
        }
    }

    public static void broadcastDisguise(Entity entity, DisguiseUpdateS2C payload) {
        for (ServerPlayer viewer : PlayerLookup.tracking(entity)) {
            if (connected(viewer)) ServerPlayNetworking.send(viewer, payload);
        }
        if (entity instanceof ServerPlayer self && connected(self)) {
            ServerPlayNetworking.send(self, payload);
        }
    }

    public static void sendDisguise(ServerPlayer recipient, DisguiseUpdateS2C payload) {
        if (connected(recipient)) ServerPlayNetworking.send(recipient, payload);
    }

    public static void sendTextDisplay(ServerPlayer recipient, dev.overgrown.apoli.network.payload.TextDisplayS2C payload) {
        if (connected(recipient) && ServerPlayNetworking.canSend(recipient, dev.overgrown.apoli.network.payload.TextDisplayS2C.TYPE)) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    public static void sendForceKey(ServerPlayer recipient, dev.overgrown.apoli.network.payload.ForceKeyS2C payload) {
        if (connected(recipient) && ServerPlayNetworking.canSend(recipient, dev.overgrown.apoli.network.payload.ForceKeyS2C.TYPE)) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    public static void broadcastLabel(Entity entity, dev.overgrown.apoli.network.payload.LabelUpdateS2C payload) {
        for (ServerPlayer viewer : PlayerLookup.tracking(entity)) {
            sendLabel(viewer, payload);
        }
        if (entity instanceof ServerPlayer self) {
            sendLabel(self, payload);
        }
    }

    public static void sendLabel(ServerPlayer recipient, dev.overgrown.apoli.network.payload.LabelUpdateS2C payload) {
        if (connected(recipient) && ServerPlayNetworking.canSend(recipient, dev.overgrown.apoli.network.payload.LabelUpdateS2C.TYPE)) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    public static void sendSkillDefs(ServerPlayer recipient) {
        if (!connected(recipient)) return;
        boolean legacy = ProtocolCompat.useLegacyFormats(recipient);
        if (legacy) {
            ProtocolCompat.markSentLegacy(recipient);
        }
        ServerPlayNetworking.send(recipient, SkillDefsSyncS2C.fromCurrent(legacy));
    }

    public static void sendSkillState(ServerPlayer recipient) {
        if (!connected(recipient)) return;
        SkillData data = SkillDataAttachment.get(recipient);
        dev.overgrown.apoli.skill.SkillTrees.Visibility vis =
            dev.overgrown.apoli.skill.SkillTrees.computeVisibility(recipient);
        boolean legacy = ProtocolCompat.useLegacyFormats(recipient);
        if (legacy) {
            ProtocolCompat.markSentLegacy(recipient);
        }
        java.util.Set<net.minecraft.resources.ResourceLocation> nonRefundable = new java.util.HashSet<>();
        for (dev.overgrown.apoli.skill.SkillTree tree : dev.overgrown.apoli.skill.SkillRegistry.trees()) {
            if (!tree.refundable()) nonRefundable.add(tree.id());
        }
        ServerPlayNetworking.send(recipient, new SkillStateSyncS2C(
            new java.util.HashMap<>(data.pointsView()), new java.util.HashSet<>(data.purchasedView()),
            vis.hidden(), vis.locked(), nonRefundable, legacy));
    }

    public static void broadcastPowers(MinecraftServer server) {
        PowerSyncCache.broadcast(server);
    }

    public static void broadcastKeybinds(MinecraftServer server, SyncKeybindsS2C payload) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void openRadialMenu(ServerPlayer player,
                                      java.util.Optional<net.minecraft.resources.ResourceLocation> sprite,
                                      java.util.List<dev.overgrown.apoli.network.payload.RadialMenuOpenS2C.Entry> display,
                                      java.util.List<dev.overgrown.apoli.action.EntityAction> actions) {
        if (!connected(player)) return;
        if (!ServerPlayNetworking.canSend(player, dev.overgrown.apoli.network.payload.RadialMenuOpenS2C.TYPE)) return;
        int nonce = dev.overgrown.apoli.radial.RadialMenuManager.open(player, actions);
        ServerPlayNetworking.send(player, new dev.overgrown.apoli.network.payload.RadialMenuOpenS2C(nonce, sprite, display));
    }
}
