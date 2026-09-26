package dev.overgrown.apoli.client;

import dev.overgrown.apoli.PowerContainerAttachment;
import dev.overgrown.apoli.effects.CustomEffectNetworking;
import dev.overgrown.apoli.keybind.HeldKeys;
import dev.overgrown.apoli.network.payload.KeyHeldC2S;
import dev.overgrown.apoli.client.rope.RopeClientManager;
import dev.overgrown.apoli.client.rope.RopeRenderer;
import dev.overgrown.apoli.client.rope.VerletRopeState;
import dev.overgrown.apoli.network.payload.ApplyVelocityS2C;
import dev.overgrown.apoli.network.payload.PowerActivatedS2C;
import dev.overgrown.apoli.network.payload.RopeCreateS2C;
import dev.overgrown.apoli.network.payload.RopeDeleteS2C;
import dev.overgrown.apoli.network.payload.RopeVerletLengthS2C;
import dev.overgrown.apoli.network.payload.SyncEntityPowersS2C;
import dev.overgrown.apoli.network.payload.SyncKeybindsS2C;
import dev.overgrown.apoli.network.payload.SyncPowersS2C;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public final class ApoliClient implements ClientModInitializer {
    private static final net.minecraft.client.KeyMapping SKILL_TREE_KEY = new net.minecraft.client.KeyMapping(
        "key.apoli.skill_tree", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
        org.lwjgl.glfw.GLFW.GLFW_KEY_K, "key.categories.apoli");

    private static final net.minecraft.client.KeyMapping SPEECH_KEY = new net.minecraft.client.KeyMapping(
        "key.apoli.speech_to_action", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
        com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), "key.categories.apoli");

    @Override
    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
            dev.overgrown.apoli.entity.ApoliEntities.CUSTOM_PROJECTILE,
            dev.overgrown.apoli.client.CustomProjectileRenderer::new);

        net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance().register(
            dev.overgrown.apoli.particle.ApoliParticles.CUSTOM,
            new dev.overgrown.apoli.client.particle.CustomParticle.Provider());

        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
            dev.overgrown.apoli.client.summon.SummonModelLayers.MINION,
            dev.overgrown.apoli.client.summon.MinionModel::createBodyLayer);
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
            dev.overgrown.apoli.client.summon.SummonModelLayers.CLONE,
            () -> net.minecraft.client.model.geom.builders.LayerDefinition.create(
                net.minecraft.client.model.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, false), 64, 64));
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
            dev.overgrown.apoli.client.summon.SummonModelLayers.CLONE_SLIM,
            () -> net.minecraft.client.model.geom.builders.LayerDefinition.create(
                net.minecraft.client.model.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, true), 64, 64));
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
            dev.overgrown.apoli.entity.ApoliEntities.MINION,
            dev.overgrown.apoli.client.summon.MinionRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
            dev.overgrown.apoli.entity.ApoliEntities.CLONE,
            dev.overgrown.apoli.client.summon.CloneRenderer::new);

        PowerContainerAttachment.setClientLookup(ClientPowerState::containerFor);

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            ClientPowerState.removeEntity(entity.getId());
            ClientLabelState.removeEntity(entity.getId());
        });

        dev.overgrown.apoli.power.PowerResources.setClientCooldownLookup((owner, powerId) ->
            owner == net.minecraft.client.Minecraft.getInstance().player
                ? ClientPowerState.getCooldown(powerId)
                : 0);

        HeldKeys.setClientLookup((entity, key, grace) ->
            entity == net.minecraft.client.Minecraft.getInstance().player && KeyPressWatcher.isLocalHeld(key, grace));
        KeyPressWatcher.setSender(keys -> ClientPlayNetworking.send(new KeyHeldC2S(keys)));

        ClientPlayNetworking.registerGlobalReceiver(CustomEffectNetworking.SyncCustomEffectsPayload.TYPE, SyncCustomEffectRegistry::onPlay);
        ClientConfigurationNetworking.registerGlobalReceiver(CustomEffectNetworking.SyncCustomEffectsPayload.TYPE, (payload, context) -> SyncCustomEffectRegistry.onConfiguration(payload));
        net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents.COMPLETE.register((handler, client) ->
            SyncCustomEffectRegistry.onConfigurationComplete());
        net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) ->
            SyncCustomEffectRegistry.onDisconnect());

        ClientPlayNetworking.registerGlobalReceiver(SyncPowersS2C.TYPE, (payload, context) ->
            context.client().execute(() -> ClientPowerState.applyPowersSync(payload)));

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.SyncPowersChunkS2C.TYPE, (payload, context) ->
            context.client().execute(() -> ClientPowerState.applyPowersChunk(payload)));

        ClientPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.SyncResourceTablesS2C.TYPE, (payload, context) ->
                context.client().execute(() -> ClientPowerState.applyResourceTables(payload)));
        ClientPlayNetworking.registerGlobalReceiver(SyncEntityPowersS2C.TYPE, (payload, context) ->
            context.client().execute(() -> ClientPowerState.applyEntityPowersSync(payload)));

        ClientPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.SyncAuxIntsS2C.TYPE, (payload, context) ->
                context.client().execute(() -> ClientPowerState.applyAuxInts(payload)));

        ClientPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.DevModeS2C.TYPE, (payload, context) ->
                context.client().execute(() -> ClientDevMode.set(payload.enabled())));

        ClientPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.SyncEntitySetsS2C.TYPE, (payload, context) ->
                context.client().execute(() -> dev.overgrown.apoli.client.ClientEntitySets.apply(
                    payload.powerId(), payload.members())));

        ClientPlayNetworking.registerGlobalReceiver(PowerActivatedS2C.TYPE, (payload, context) ->
            context.client().execute(() -> ClientPowerState.setCooldown(payload.power(), payload.cooldown())));

        ClientPlayNetworking.registerGlobalReceiver(SyncKeybindsS2C.TYPE, (payload, context) ->
            context.client().execute(() -> DynamicKeyMappingManager.applyKeybinds(payload.keybinds())));

        ClientPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.PowerInventoryS2C.TYPE, (payload, context) ->
                context.client().execute(() -> ClientPowerState.applyPowerInventory(payload)));

        dev.overgrown.apoli.power.builtin.InventoryPower.setClientLookup((holder, powerId) ->
            holder == net.minecraft.client.Minecraft.getInstance().player
                ? ClientPowerState.powerInventory(powerId)
                : null);

        ClientPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.MountOffsetS2C.TYPE, (payload, context) ->
                context.client().execute(() -> dev.overgrown.apoli.mount.MountOffsets.put(
                    payload.passengerId(),
                    new dev.overgrown.apoli.mount.MountOffsets.Offset(
                        payload.x(), payload.y(), payload.z(), payload.space(), payload.rotation()))));

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.ScaleSyncS2C.TYPE,
            (payload, context) -> context.client().execute(() -> {
                if (context.client().level == null) return;
                net.minecraft.world.entity.Entity entity = context.client().level.getEntity(payload.entityId());
                if (entity == null) return;
                dev.overgrown.apoli.scale.ScaleSync.decode(entity, payload.data());
            }));

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.TickRateS2C.TYPE,
            (payload, context) -> context.client().execute(() ->
                dev.overgrown.apoli.client.ClientTickRates.set(payload.entityId(), payload.rate(), payload.baseRate())));

        ClientPlayNetworking.registerGlobalReceiver(ApplyVelocityS2C.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (context.client().level == null) return;
                net.minecraft.world.entity.Entity e = context.client().level.getEntity(payload.entityId());
                if (e == null) return;
                net.minecraft.world.phys.Vec3 delta = new net.minecraft.world.phys.Vec3(payload.x(), payload.y(), payload.z());
                e.setDeltaMovement(payload.set() ? delta : e.getDeltaMovement().add(delta));
            }));

        dev.overgrown.apoli.client.disguise.ClientDisguiseManager.install();
        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.DisguiseUpdateS2C.TYPE, (payload, context) ->
            context.client().execute(() -> payload.data().ifPresentOrElse(
                data -> dev.overgrown.apoli.client.disguise.ClientDisguiseManager.apply(payload.entityId(), data),
                () -> dev.overgrown.apoli.client.disguise.ClientDisguiseManager.remove(payload.entityId()))));

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.ProtocolVersionPayload.TYPE, (payload, context) ->
            context.client().execute(() -> ClientProtocolState.setServerVersion(payload.version())));

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.TextDisplayS2C.TYPE, (payload, context) ->
            context.client().execute(() -> TextOverlayRenderer.apply(payload)));
        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.LabelUpdateS2C.TYPE, (payload, context) ->
            context.client().execute(() -> ClientLabelState.apply(payload.entityId(), payload.texts())));
        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.ForceKeyS2C.TYPE, (payload, context) ->
            context.client().execute(() -> ForcedKeys.force(payload.key(), payload.duration(), payload.release())));
        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.SyncShaderS2C.TYPE, (payload, context) ->
            context.client().execute(() -> ShaderPowerState.accept(payload.shader(), payload.toggleable())));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientPlayNetworking.send(new dev.overgrown.apoli.network.payload.ProtocolVersionPayload(
                dev.overgrown.apoli.network.ProtocolCompat.VERSION));
            if (ApoliClientConfig.get().speechToAction()) {
                dev.overgrown.apoli.client.speech.SpeechClient.onJoin();
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.SkillDefsSyncS2C.TYPE, (payload, context) ->
            context.client().execute(() -> dev.overgrown.apoli.client.skill.ClientSkillState.applyDefs(payload)));
        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.SkillStateSyncS2C.TYPE, (payload, context) ->
            context.client().execute(() -> dev.overgrown.apoli.client.skill.ClientSkillState.applyState(payload)));

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.RadialMenuOpenS2C.TYPE, (payload, context) ->
            context.client().execute(() -> context.client().setScreen(new dev.overgrown.apoli.client.radial.RadialMenuScreen(payload))));

        ClientPlayNetworking.registerGlobalReceiver(RopeCreateS2C.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (context.client().level != null) RopeClientManager.attach(payload, context.client().level);
            }));

        ClientPlayNetworking.registerGlobalReceiver(RopeDeleteS2C.TYPE, (payload, context) ->
            context.client().execute(() -> RopeClientManager.detach(payload.id())));

        ClientPlayNetworking.registerGlobalReceiver(RopeVerletLengthS2C.TYPE, (payload, context) ->
            context.client().execute(() -> {
                VerletRopeState rope = RopeClientManager.get(payload.id());
                if (rope != null) rope.targetLength = payload.length();
            }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) ->
            mc.execute(() -> {
                DynamicKeyMappingManager.unregisterAll();
                ClientPowerState.clear();
                dev.overgrown.apoli.client.ClientEntitySets.clear();
                ClientDevMode.clear();
                dev.overgrown.apoli.mount.MountOffsets.clearAll();
                ShaderPowerState.clear();
                dev.overgrown.apoli.client.render.BlockRenderRules.clear();
                dev.overgrown.apoli.client.render.ClientRenderFlags.clear();
                TextOverlayRenderer.clear();
                ClientLabelState.clear();
                RopeClientManager.clear();
                KeyPressWatcher.reset();
                ApoliKeyMappings.reset();
                ForcedKeys.clear();
                BlockedKeys.clear();
                CursorSpeedState.reset();
                dev.overgrown.apoli.client.disguise.ClientDisguiseManager.clear();
                dev.overgrown.apoli.client.skill.ClientSkillState.clear();
                dev.overgrown.apoli.compat.figura.FiguraModelPowerManager.clear();
                dev.overgrown.apoli.client.speech.SpeechClient.onLeave();
                ClientProtocolState.reset();
            }));

        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                @Override
                public net.minecraft.resources.ResourceLocation getFabricId() {
                    return dev.overgrown.apoli.Apoli.id("figura_avatars");
                }

                @Override
                public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                    dev.overgrown.apoli.compat.figura.FiguraModelPowerManager.onResourcesReloaded();
                    IconRenderer.clearCache();
                    ShaderPowerState.invalidate();
                    dev.overgrown.apoli.client.particle.ParticleSheet.clearCache();
                    dev.overgrown.apoli.client.particle.ParticleTextures.clearCache();
                    dev.overgrown.apoli.client.render.AnimatedTextures.clearCache();
                }
            });

        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(dev.overgrown.apoli.client.render.CustomModelManager.INSTANCE);
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(dev.overgrown.apoli.client.render.AnimationManager.INSTANCE);

        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(SKILL_TREE_KEY);
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(SPEECH_KEY);
        dev.overgrown.apoli.client.speech.SpeechClient.setPushToTalkKey(SPEECH_KEY);

        if (dev.overgrown.apoli.compat.ModCompat.LAMBDYNLIGHTS) {
            dev.overgrown.apoli.compat.lambdynlights.LambDynamicLightsCompat.init();
        }
        if (dev.overgrown.apoli.compat.ModCompat.EARS) {
            dev.overgrown.apoli.compat.ears.EarsCompat.init();
        }
        if (dev.overgrown.apoli.compat.ModCompat.ENTITY_MODEL_FEATURES) {
            dev.overgrown.apoli.compat.entitymodelfeatures.EntityModelFeaturesCompat.init();
        }

        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (mc.player != null && !mc.isPaused()) ApoliKeyHandler.onClientTick();
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            CursorSpeedState.tick(mc);
            dev.overgrown.apoli.client.MouseMovementWatcher.clientTick(mc);
            dev.overgrown.apoli.client.render.ClientRenderFlags.clientTick(mc);
            dev.overgrown.apoli.client.render.BlockRenderRules.clientTick(mc);
            PhasingRenderState.clientTick(mc);
            RopeClientManager.tick();
            TextOverlayRenderer.tick();
            dev.overgrown.apoli.client.disguise.ClientDisguiseManager.tick(mc);
            dev.overgrown.apoli.client.speech.SpeechClient.clientTick(mc);
            if (dev.overgrown.apoli.compat.ModCompat.FIGURA) {
                dev.overgrown.apoli.compat.figura.FiguraModelPowerManager.tick(mc);
            }
            while (SKILL_TREE_KEY.consumeClick()) {
                if (mc.screen == null && dev.overgrown.apoli.client.skill.ClientSkillState.hasAnyTree()) {
                    if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(dev.overgrown.apoli.network.payload.RequestSkillStateC2S.TYPE)) {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(dev.overgrown.apoli.network.payload.RequestSkillStateC2S.INSTANCE);
                    }
                    mc.setScreen(new dev.overgrown.apoli.client.skill.SkillTreeScreen());
                }
            }
            PlayerModelTypeReporter.tick(mc);
            CameraPerspectiveReporter.tick(mc);
            ForcedKeys.tick();
            dev.overgrown.apoli.power.builtin.ModifyFogInterpolator.tick(mc.player);
        });

        WorldRenderEvents.AFTER_ENTITIES.register(RopeRenderer::render);

        HudRenderCallback.EVENT.register((gfx, tracker) -> OverlayRenderer.renderBelowHud(gfx, tracker.getGameTimeDeltaPartialTick(false)));
        HudRenderCallback.EVENT.register((gfx, tracker) -> PowerHudRenderer.render(gfx, tracker.getGameTimeDeltaPartialTick(false)));
        HudRenderCallback.EVENT.register((gfx, tracker) -> TextOverlayRenderer.render(gfx, tracker.getGameTimeDeltaPartialTick(false)));
        HudRenderCallback.EVENT.register((gfx, tracker) -> OverlayRenderer.renderAboveHud(gfx, tracker.getGameTimeDeltaPartialTick(false)));
        HudRenderCallback.EVENT.register((gfx, tracker) -> DevHudRenderer.render(gfx, tracker.getGameTimeDeltaPartialTick(false)));
    }
}
