package dev.overgrown.apoli;

import dev.overgrown.apoli.client.SyncCustomEffectRegistry;
import dev.overgrown.apoli.effects.CustomEffectNetworking;
import dev.overgrown.apoli.effects.CustomEffectRegistry;
import dev.overgrown.apoli.network.payload.ApplyVelocityS2C;
import dev.overgrown.apoli.network.payload.BuySkillC2S;
import dev.overgrown.apoli.network.payload.DisguiseUpdateS2C;
import dev.overgrown.apoli.network.payload.KeyHeldC2S;
import dev.overgrown.apoli.network.payload.SkillDefsSyncS2C;
import dev.overgrown.apoli.network.payload.SkillStateSyncS2C;
import dev.overgrown.apoli.skill.SkillData;
import dev.overgrown.apoli.skill.SkillDataAttachment;
import dev.overgrown.apoli.skill.SkillTrees;
import dev.overgrown.apoli.network.payload.PowerActivatedS2C;
import dev.overgrown.apoli.network.payload.PowerActivationC2S;
import dev.overgrown.apoli.network.payload.PowerToggleC2S;
import dev.overgrown.apoli.network.payload.RopeChangeLengthC2S;
import dev.overgrown.apoli.network.payload.RopeCreateS2C;
import dev.overgrown.apoli.network.payload.RopeDeleteS2C;
import dev.overgrown.apoli.network.payload.RopeSwingC2S;
import dev.overgrown.apoli.network.payload.RopeVerletLengthS2C;
import dev.overgrown.apoli.network.payload.SyncEntityPowersS2C;
import dev.overgrown.apoli.network.payload.SyncKeybindsS2C;
import dev.overgrown.apoli.network.payload.SyncPowersS2C;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ApoliNetwork {

    private static final String PROTOCOL_VERSION = "13";

    private ApoliNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Apoli.MOD_ID).versioned(PROTOCOL_VERSION);
        registrar.playToClient(SyncPowersS2C.TYPE, SyncPowersS2C.STREAM_CODEC, ApoliNetwork::onSyncPowers);
        registrar.playToClient(dev.overgrown.apoli.network.payload.SyncPowersChunkS2C.TYPE,
            dev.overgrown.apoli.network.payload.SyncPowersChunkS2C.STREAM_CODEC, ApoliNetwork::onSyncPowersChunk);
        registrar.playToClient(SyncEntityPowersS2C.TYPE, SyncEntityPowersS2C.STREAM_CODEC, ApoliNetwork::onSyncEntityPowers);
        registrar.playToClient(dev.overgrown.apoli.network.payload.SyncResourceTablesS2C.TYPE,
            dev.overgrown.apoli.network.payload.SyncResourceTablesS2C.STREAM_CODEC, ApoliNetwork::onSyncResourceTables);
        registrar.playToClient(dev.overgrown.apoli.network.payload.SyncAuxIntsS2C.TYPE,
            dev.overgrown.apoli.network.payload.SyncAuxIntsS2C.STREAM_CODEC, ApoliNetwork::onSyncAuxInts);
        registrar.playToClient(dev.overgrown.apoli.network.payload.DevModeS2C.TYPE,
            dev.overgrown.apoli.network.payload.DevModeS2C.STREAM_CODEC, ApoliNetwork::onDevMode);
        registrar.playToClient(dev.overgrown.apoli.network.payload.SyncEntitySetsS2C.TYPE,
            dev.overgrown.apoli.network.payload.SyncEntitySetsS2C.STREAM_CODEC, ApoliNetwork::onSyncEntitySets);
        registrar.playToClient(PowerActivatedS2C.TYPE, PowerActivatedS2C.STREAM_CODEC, ApoliNetwork::onPowerActivated);
        registrar.playToClient(SyncKeybindsS2C.TYPE, SyncKeybindsS2C.STREAM_CODEC, ApoliNetwork::onSyncKeybinds);
        registrar.playToClient(ApplyVelocityS2C.TYPE, ApplyVelocityS2C.STREAM_CODEC, ApoliNetwork::onApplyVelocity);
        registrar.playToClient(dev.overgrown.apoli.network.payload.TickRateS2C.TYPE,
            dev.overgrown.apoli.network.payload.TickRateS2C.STREAM_CODEC, ApoliNetwork::onTickRate);
        registrar.playToClient(dev.overgrown.apoli.network.payload.PowerInventoryS2C.TYPE,
            dev.overgrown.apoli.network.payload.PowerInventoryS2C.STREAM_CODEC, ApoliNetwork::onPowerInventory);
        registrar.playToClient(dev.overgrown.apoli.network.payload.MountOffsetS2C.TYPE,
            dev.overgrown.apoli.network.payload.MountOffsetS2C.STREAM_CODEC, ApoliNetwork::onMountOffset);
        registrar.playToClient(DisguiseUpdateS2C.TYPE, DisguiseUpdateS2C.STREAM_CODEC, ApoliNetwork::onDisguiseUpdate);
        registrar.playToClient(dev.overgrown.apoli.network.payload.ScaleSyncS2C.TYPE,
            dev.overgrown.apoli.network.payload.ScaleSyncS2C.STREAM_CODEC, ApoliNetwork::onScaleSync);
        registrar.playToClient(dev.overgrown.apoli.network.payload.TextDisplayS2C.TYPE,
            dev.overgrown.apoli.network.payload.TextDisplayS2C.STREAM_CODEC, ApoliNetwork::onTextDisplay);
        registrar.playToClient(dev.overgrown.apoli.network.payload.ForceKeyS2C.TYPE,
            dev.overgrown.apoli.network.payload.ForceKeyS2C.STREAM_CODEC, ApoliNetwork::onForceKey);
        registrar.playToClient(dev.overgrown.apoli.network.payload.SyncShaderS2C.TYPE,
            dev.overgrown.apoli.network.payload.SyncShaderS2C.STREAM_CODEC, ApoliNetwork::onSyncShader);
        registrar.playToClient(dev.overgrown.apoli.network.payload.LabelUpdateS2C.TYPE,
            dev.overgrown.apoli.network.payload.LabelUpdateS2C.STREAM_CODEC, ApoliNetwork::onLabelUpdate);
        registrar.playToClient(SkillDefsSyncS2C.TYPE, SkillDefsSyncS2C.STREAM_CODEC, ApoliNetwork::onSkillDefs);
        registrar.playToClient(SkillStateSyncS2C.TYPE, SkillStateSyncS2C.STREAM_CODEC, ApoliNetwork::onSkillState);
        registrar.playToServer(BuySkillC2S.TYPE, BuySkillC2S.STREAM_CODEC, ApoliNetwork::onBuySkill);
        registrar.playToServer(dev.overgrown.apoli.network.payload.RequestSkillStateC2S.TYPE,
            dev.overgrown.apoli.network.payload.RequestSkillStateC2S.STREAM_CODEC, ApoliNetwork::onRequestSkillState);
        registrar.playToServer(dev.overgrown.apoli.network.payload.RefundSkillC2S.TYPE,
            dev.overgrown.apoli.network.payload.RefundSkillC2S.STREAM_CODEC, ApoliNetwork::onRefundSkill);
        registrar.playToServer(PowerActivationC2S.TYPE, PowerActivationC2S.STREAM_CODEC, ApoliNetwork::onPowerActivation);
        registrar.playToServer(PowerToggleC2S.TYPE, PowerToggleC2S.STREAM_CODEC, ApoliNetwork::onPowerToggle);
        registrar.playToServer(KeyHeldC2S.TYPE, KeyHeldC2S.STREAM_CODEC, ApoliNetwork::onKeyHeld);
        registrar.playToServer(dev.overgrown.apoli.network.payload.ScrollWheelC2S.TYPE,
            dev.overgrown.apoli.network.payload.ScrollWheelC2S.STREAM_CODEC, ApoliNetwork::onScrollWheel);
        registrar.playToServer(dev.overgrown.apoli.network.payload.MouseMovementC2S.TYPE,
            dev.overgrown.apoli.network.payload.MouseMovementC2S.STREAM_CODEC, ApoliNetwork::onMouseMovement);
        registrar.playToServer(
            dev.overgrown.apoli.network.payload.PlayerModelTypeC2S.TYPE,
            dev.overgrown.apoli.network.payload.PlayerModelTypeC2S.STREAM_CODEC,
            ApoliNetwork::onPlayerModelType);
        registrar.playToServer(
            dev.overgrown.apoli.network.payload.CameraPerspectiveC2S.TYPE,
            dev.overgrown.apoli.network.payload.CameraPerspectiveC2S.STREAM_CODEC,
            ApoliNetwork::onCameraPerspective);
        registrar.playToServer(dev.overgrown.apoli.network.payload.SpeechTriggerC2S.TYPE,
            dev.overgrown.apoli.network.payload.SpeechTriggerC2S.STREAM_CODEC, ApoliNetwork::onSpeechTrigger);

        registrar.playToClient(dev.overgrown.apoli.network.payload.RadialMenuOpenS2C.TYPE,
            dev.overgrown.apoli.network.payload.RadialMenuOpenS2C.STREAM_CODEC, ApoliNetwork::onRadialMenuOpen);
        registrar.playToServer(dev.overgrown.apoli.network.payload.RadialMenuSelectC2S.TYPE,
            dev.overgrown.apoli.network.payload.RadialMenuSelectC2S.STREAM_CODEC, ApoliNetwork::onRadialMenuSelect);

        registrar.playToClient(RopeCreateS2C.TYPE, RopeCreateS2C.STREAM_CODEC, ApoliNetwork::onRopeCreate);
        registrar.playToClient(RopeDeleteS2C.TYPE, RopeDeleteS2C.STREAM_CODEC, ApoliNetwork::onRopeDelete);
        registrar.playToClient(RopeVerletLengthS2C.TYPE, RopeVerletLengthS2C.STREAM_CODEC, ApoliNetwork::onRopeVerletLength);
        registrar.playToServer(RopeChangeLengthC2S.TYPE, RopeChangeLengthC2S.STREAM_CODEC, ApoliNetwork::onRopeChangeLength);
        registrar.playToServer(RopeSwingC2S.TYPE, RopeSwingC2S.STREAM_CODEC, ApoliNetwork::onRopeSwing);

        registrar.configurationToClient(CustomEffectNetworking.SyncCustomEffectsPayload.TYPE,
            CustomEffectNetworking.SyncCustomEffectsPayload.CODEC, SyncCustomEffectRegistry::sync);
        registrar.configurationToServer(CustomEffectNetworking.SyncCustomEffectsResponsePayload.TYPE,
            CustomEffectNetworking.SyncCustomEffectsResponsePayload.CODEC, ApoliNetwork::onCustomEffectsConfigured);
        registrar.playToClient(CustomEffectNetworking.SyncCustomEffectsPayload.TYPE,
            CustomEffectNetworking.SyncCustomEffectsPayload.CODEC, SyncCustomEffectRegistry::sync);
        registrar.playToServer(CustomEffectNetworking.SyncCustomEffectsResponsePayload.TYPE,
            CustomEffectNetworking.SyncCustomEffectsResponsePayload.CODEC, ApoliNetwork::onCustomEffectsAck);
    }

    private static void onCustomEffectsConfigured(CustomEffectNetworking.SyncCustomEffectsResponsePayload payload, IPayloadContext context) {
        context.finishCurrentTask(CustomEffectNetworking.SyncCustomEffectConfigurationTask.TYPE);
    }

    private static void onCustomEffectsAck(CustomEffectNetworking.SyncCustomEffectsResponsePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) CustomEffectRegistry.acknowledge(player, payload.success());
    }

    private static void onSyncPowers(SyncPowersS2C payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onSyncPowers(payload));
    }

    private static void onSyncPowersChunk(dev.overgrown.apoli.network.payload.SyncPowersChunkS2C payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onSyncPowersChunk(payload));
    }

    private static void onSyncEntityPowers(SyncEntityPowersS2C payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onSyncEntityPowers(payload));
    }

    private static void onSyncResourceTables(dev.overgrown.apoli.network.payload.SyncResourceTablesS2C payload,
                                             IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPowerState.applyResourceTables(payload));
    }

    private static void onSyncAuxInts(dev.overgrown.apoli.network.payload.SyncAuxIntsS2C payload,
                                      IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPowerState.applyAuxInts(payload));
    }

    private static void onDevMode(dev.overgrown.apoli.network.payload.DevModeS2C payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientDevMode.set(payload.enabled()));
    }

    private static void onSyncEntitySets(dev.overgrown.apoli.network.payload.SyncEntitySetsS2C payload,
                                         IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientEntitySets.apply(payload.powerId(), payload.members()));
    }

    private static void onPowerActivated(PowerActivatedS2C payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onPowerActivated(payload));
    }

    private static void onSyncKeybinds(SyncKeybindsS2C payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onSyncKeybinds(payload));
    }

    private static void onPowerInventory(dev.overgrown.apoli.network.payload.PowerInventoryS2C payload,
                                         IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onPowerInventory(payload));
    }

    private static void onMountOffset(dev.overgrown.apoli.network.payload.MountOffsetS2C payload,
                                      IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onMountOffset(payload));
    }

    private static void onApplyVelocity(ApplyVelocityS2C payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onApplyVelocity(payload));
    }

    private static void onPowerActivation(PowerActivationC2S payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                Apoli.handleActivation(sp, payload);
            }
        });
    }

    private static void onPowerToggle(PowerToggleC2S payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                Apoli.handleToggle(sp, payload);
            }
        });
    }

    private static void onPlayerModelType(
        dev.overgrown.apoli.network.payload.PlayerModelTypeC2S payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.entity.PlayerModelTypes.set(
            ctx.player().getUUID(), payload.modelType()));
    }

    private static void onCameraPerspective(
        dev.overgrown.apoli.network.payload.CameraPerspectiveC2S payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.entity.CameraPerspectives.set(
            ctx.player().getUUID(), payload.firstPerson()));
    }

    private static void onTickRate(dev.overgrown.apoli.network.payload.TickRateS2C payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onTickRate(payload));
    }

    private static void onScrollWheel(dev.overgrown.apoli.network.payload.ScrollWheelC2S payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                dev.overgrown.apoli.power.builtin.ActionOnScrollWheelPower.scroll(sp,
                    payload.up() ? dev.overgrown.apoli.data.ScrollDirection.UP
                        : dev.overgrown.apoli.data.ScrollDirection.DOWN,
                    payload.notches());
            }
        });
    }

    private static void onMouseMovement(dev.overgrown.apoli.network.payload.MouseMovementC2S payload,
                                        IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                dev.overgrown.apoli.power.builtin.ActionOnMouseMovementPower.moved(sp, payload.yaw(), payload.pitch());
            }
        });
    }

    private static void onKeyHeld(KeyHeldC2S payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                dev.overgrown.apoli.keybind.HeldKeys.setServerHeld(sp, payload.keys());
            }
        });
    }

    private static void onSpeechTrigger(dev.overgrown.apoli.network.payload.SpeechTriggerC2S payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                dev.overgrown.apoli.compat.voicechat.ActionOnSpeechPower.fireTrigger(sp, payload.power());
            }
        });
    }

    private static void onRopeCreate(RopeCreateS2C payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onRopeCreate(payload));
    }

    private static void onRopeDelete(RopeDeleteS2C payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onRopeDelete(payload));
    }

    private static void onRopeVerletLength(RopeVerletLengthS2C payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onRopeVerletLength(payload));
    }

    private static void onRopeChangeLength(RopeChangeLengthC2S payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                dev.overgrown.apoli.rope.RopeManager.handleLengthChange(sp, payload.ropeId(), payload.delta());
            }
        });
    }

    private static void onRopeSwing(RopeSwingC2S payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                dev.overgrown.apoli.rope.RopeManager.applySwingInput(sp, payload.ropeId(), payload.inputDir());
            }
        });
    }

    public static boolean connected(ServerPlayer recipient) {
        return recipient != null && recipient.connection != null;
    }

    public static void sendPowers(ServerPlayer recipient) {
        if (!connected(recipient)) return;
        dev.overgrown.apoli.network.PowerSyncCache.sendTo(recipient);
        dev.overgrown.apoli.power.builtin.InventoryPower.syncAll(recipient);
        dev.overgrown.apoli.mount.MountOffsets.syncAll(recipient);
    }

    public static void sendPowerInventory(ServerPlayer recipient,
                                          dev.overgrown.apoli.network.payload.PowerInventoryS2C payload) {
        if (!connected(recipient)) return;
        PacketDistributor.sendToPlayer(recipient, payload);
    }

    public static void sendMountOffset(ServerPlayer recipient,
                                       dev.overgrown.apoli.network.payload.MountOffsetS2C payload) {
        if (!connected(recipient)) return;
        PacketDistributor.sendToPlayer(recipient, payload);
    }

    public static void broadcastMountOffset(Entity passenger,
                                            dev.overgrown.apoli.network.payload.MountOffsetS2C payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(passenger, payload);
    }

    public static void broadcastRopeCreate(net.minecraft.server.level.ServerLevel level, RopeCreateS2C payload) {
        for (ServerPlayer p : level.players()) { if (connected(p)) PacketDistributor.sendToPlayer(p, payload); }
    }

    public static void broadcastRopeDelete(net.minecraft.server.level.ServerLevel level, RopeDeleteS2C payload) {
        for (ServerPlayer p : level.players()) { if (connected(p)) PacketDistributor.sendToPlayer(p, payload); }
    }

    public static void broadcastRopeVerletLength(net.minecraft.server.level.ServerLevel level, RopeVerletLengthS2C payload) {
        for (ServerPlayer p : level.players()) { if (connected(p)) PacketDistributor.sendToPlayer(p, payload); }
    }

    public static void sendEntityPowers(ServerPlayer recipient, SyncEntityPowersS2C payload) {
        if (!connected(recipient)) return;
        PacketDistributor.sendToPlayer(recipient, payload);
    }

    public static void sendResourceTables(ServerPlayer recipient,
                                          dev.overgrown.apoli.network.payload.SyncResourceTablesS2C payload) {
        if (!connected(recipient)) return;
        PacketDistributor.sendToPlayer(recipient, payload);
    }

    public static void sendResourceTablesToTrackersAndSelf(Entity entity,
                                                           dev.overgrown.apoli.network.payload.SyncResourceTablesS2C payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    public static void sendAuxIntsToTrackersAndSelf(Entity entity,
                                                    dev.overgrown.apoli.network.payload.SyncAuxIntsS2C payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    public static void sendEntityPowersToTrackers(Entity entity, SyncEntityPowersS2C payload) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
    }

    public static void sendEntityPowersToTrackersAndSelf(Entity entity, SyncEntityPowersS2C payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    public static void sendActivated(ServerPlayer recipient, PowerActivatedS2C payload) {
        if (!connected(recipient)) return;
        PacketDistributor.sendToPlayer(recipient, payload);
    }

    public static void sendDevMode(ServerPlayer recipient,
                                   dev.overgrown.apoli.network.payload.DevModeS2C payload) {
        if (!connected(recipient)) return;
        PacketDistributor.sendToPlayer(recipient, payload);
    }

    public static void sendEntitySets(ServerPlayer recipient,
                                      dev.overgrown.apoli.network.payload.SyncEntitySetsS2C payload) {
        if (!connected(recipient)) return;
        PacketDistributor.sendToPlayer(recipient, payload);
    }

    public static void sendKeybinds(ServerPlayer recipient, SyncKeybindsS2C payload) {
        if (!connected(recipient)) return;
        PacketDistributor.sendToPlayer(recipient, payload);
    }

    public static void broadcastTickRate(MinecraftServer server, dev.overgrown.apoli.network.payload.TickRateS2C payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }

    public static void sendTickRateToTrackers(Entity entity, dev.overgrown.apoli.network.payload.TickRateS2C payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    public static void sendApplyVelocityToTrackers(Entity entity, ApplyVelocityS2C payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    private static void onDisguiseUpdate(DisguiseUpdateS2C payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onDisguiseUpdate(payload));
    }

    private static void onScaleSync(dev.overgrown.apoli.network.payload.ScaleSyncS2C payload,
                                    net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onScaleSync(payload));
    }

    public static void sendScale(ServerPlayer recipient, dev.overgrown.apoli.network.payload.ScaleSyncS2C payload) {
        PacketDistributor.sendToPlayer(recipient, payload);
    }

    public static void broadcastScale(Entity entity, dev.overgrown.apoli.network.payload.ScaleSyncS2C payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    public static void broadcastDisguise(Entity entity, DisguiseUpdateS2C payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    private static void onTextDisplay(dev.overgrown.apoli.network.payload.TextDisplayS2C payload,
                                      net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onTextDisplay(payload));
    }

    private static void onLabelUpdate(dev.overgrown.apoli.network.payload.LabelUpdateS2C payload,
                                      net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onLabelUpdate(payload));
    }

    public static void sendTextDisplay(ServerPlayer recipient, dev.overgrown.apoli.network.payload.TextDisplayS2C payload) {
        if (!connected(recipient)) return;
        PacketDistributor.sendToPlayer(recipient, payload);
    }

    private static void onForceKey(dev.overgrown.apoli.network.payload.ForceKeyS2C payload,
                                   net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onForceKey(payload));
    }

    public static void sendForceKey(ServerPlayer recipient, dev.overgrown.apoli.network.payload.ForceKeyS2C payload) {
        if (!connected(recipient)) return;
        PacketDistributor.sendToPlayer(recipient, payload);
    }

    private static void onSyncShader(dev.overgrown.apoli.network.payload.SyncShaderS2C payload,
                                     net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onSyncShader(payload));
    }

    public static void sendShader(ServerPlayer recipient, dev.overgrown.apoli.network.payload.SyncShaderS2C payload) {
        if (!connected(recipient)) return;
        PacketDistributor.sendToPlayer(recipient, payload);
    }

    public static void broadcastLabel(Entity entity, dev.overgrown.apoli.network.payload.LabelUpdateS2C payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    public static void sendLabel(ServerPlayer recipient, dev.overgrown.apoli.network.payload.LabelUpdateS2C payload) {
        if (!connected(recipient)) return;
        PacketDistributor.sendToPlayer(recipient, payload);
    }

    private static void onSkillDefs(SkillDefsSyncS2C payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onSkillDefs(payload));
    }

    private static void onSkillState(SkillStateSyncS2C payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onSkillState(payload));
    }

    private static void onBuySkill(BuySkillC2S payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && SkillTrees.tryPurchase(player, payload.skill())) {
                sendSkillState(player);
            }
        });
    }

    private static void onRequestSkillState(dev.overgrown.apoli.network.payload.RequestSkillStateC2S payload,
                                            net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                sendSkillState(player);
            }
        });
    }

    private static void onRefundSkill(dev.overgrown.apoli.network.payload.RefundSkillC2S payload,
                                      net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player
                && SkillTrees.tryRefund(player, payload.skill(), false)) {
                sendSkillState(player);
            }
        });
    }

    private static void onRadialMenuOpen(dev.overgrown.apoli.network.payload.RadialMenuOpenS2C payload,
                                         net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.overgrown.apoli.client.ClientPayloadHandlers.onRadialMenuOpen(payload));
    }

    private static void onRadialMenuSelect(dev.overgrown.apoli.network.payload.RadialMenuSelectC2S payload,
                                           net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                dev.overgrown.apoli.radial.RadialMenuManager.select(player, payload.nonce(), payload.index());
            }
        });
    }

    public static void openRadialMenu(ServerPlayer player,
                                      java.util.Optional<net.minecraft.resources.ResourceLocation> sprite,
                                      java.util.List<dev.overgrown.apoli.network.payload.RadialMenuOpenS2C.Entry> display,
                                      java.util.List<dev.overgrown.apoli.action.EntityAction> actions) {
        if (!connected(player)) return;
        int nonce = dev.overgrown.apoli.radial.RadialMenuManager.open(player, actions);
        PacketDistributor.sendToPlayer(player, new dev.overgrown.apoli.network.payload.RadialMenuOpenS2C(nonce, sprite, display));
    }

    public static void sendSkillDefs(ServerPlayer recipient) {
        if (!connected(recipient)) return;
        PacketDistributor.sendToPlayer(recipient, SkillDefsSyncS2C.fromCurrent());
    }

    public static void sendSkillState(ServerPlayer recipient) {
        if (!connected(recipient)) return;
        SkillData data = SkillDataAttachment.get(recipient);
        dev.overgrown.apoli.skill.SkillTrees.Visibility vis =
            dev.overgrown.apoli.skill.SkillTrees.computeVisibility(recipient);
        java.util.Set<net.minecraft.resources.ResourceLocation> nonRefundable = new java.util.HashSet<>();
        for (dev.overgrown.apoli.skill.SkillTree tree : dev.overgrown.apoli.skill.SkillRegistry.trees()) {
            if (!tree.refundable()) nonRefundable.add(tree.id());
        }
        PacketDistributor.sendToPlayer(recipient, new SkillStateSyncS2C(
            new java.util.HashMap<>(data.pointsView()), new java.util.HashSet<>(data.purchasedView()),
            vis.hidden(), vis.locked(), nonRefundable));
    }

    public static void sendDisguise(ServerPlayer recipient, DisguiseUpdateS2C payload) {
        if (!connected(recipient)) return;
        PacketDistributor.sendToPlayer(recipient, payload);
    }

    public static void broadcastPowers(MinecraftServer server) {
        dev.overgrown.apoli.network.PowerSyncCache.broadcast(server);
    }

    public static void broadcastKeybinds(MinecraftServer server, SyncKeybindsS2C payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }
}
