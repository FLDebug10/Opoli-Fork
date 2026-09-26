package dev.overgrown.apoli;

import dev.overgrown.apoli.client.*;
import dev.overgrown.apoli.effects.CustomEffectNetworking;
import dev.overgrown.apoli.keybind.HeldKeys;
import dev.overgrown.apoli.client.rope.RopeClientManager;
import dev.overgrown.apoli.client.rope.RopeRenderer;
import dev.overgrown.apoli.client.rope.VerletRopeState;
import dev.overgrown.apoli.network.payload.ApplyVelocityS2C;
import dev.overgrown.apoli.network.payload.KeyHeldC2S;
import dev.overgrown.apoli.network.payload.PowerActivatedS2C;
import dev.overgrown.apoli.network.payload.RopeCreateS2C;
import dev.overgrown.apoli.network.payload.RopeDeleteS2C;
import dev.overgrown.apoli.network.payload.RopeVerletLengthS2C;
import dev.overgrown.apoli.network.payload.SyncEntityPowersS2C;
import dev.overgrown.apoli.network.payload.SyncKeybindsS2C;
import dev.overgrown.apoli.network.payload.SyncPowersS2C;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

public final class ApoliClient implements ClientModInitializer {
    private static final net.minecraft.client.KeyMapping SKILL_TREE_KEY = new net.minecraft.client.KeyMapping(
        "key.apoli.skill_tree", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
        org.lwjgl.glfw.GLFW.GLFW_KEY_K, "key.categories.apoli");

    private static final net.minecraft.client.KeyMapping SPEECH_KEY = new net.minecraft.client.KeyMapping(
        "key.apoli.speech_to_action", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
        com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), "key.categories.apoli");


    @Override
    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(SKILL_TREE_KEY);
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(SPEECH_KEY);
        dev.overgrown.apoli.client.speech.SpeechClient.setPushToTalkKey(SPEECH_KEY);
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

        PowerContainerAttachment.setClientLookup(dev.overgrown.apoli.client.ClientPowerState::containerFor);

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            dev.overgrown.apoli.client.ClientPowerState.removeEntity(entity.getId());
            dev.overgrown.apoli.client.ClientLabelState.removeEntity(entity.getId());
        });

        dev.overgrown.apoli.power.PowerResources.setClientCooldownLookup((owner, powerId) ->
            owner == Minecraft.getInstance().player
                ? dev.overgrown.apoli.client.ClientPowerState.getCooldown(powerId)
                : 0);

        HeldKeys.setClientLookup((entity, key, grace) ->
            entity == Minecraft.getInstance().player && KeyPressWatcher.isLocalHeld(key, grace));
        KeyPressWatcher.setSender(keys -> {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            new KeyHeldC2S(keys).write(buf);
            ClientPlayNetworking.send(KeyHeldC2S.CHANNEL, buf);
        });

        ClientPlayNetworking.registerGlobalReceiver(CustomEffectNetworking.SyncCustomEffectsPacket.CHANNEL, (mc, listener, byteBuf, sender) -> SyncCustomEffectRegistry.sync(mc, byteBuf, sender));

        ClientPlayNetworking.registerGlobalReceiver(SyncPowersS2C.CHANNEL, (mc, handler, buf, sender) -> {
            SyncPowersS2C payload = SyncPowersS2C.read(buf);
            mc.execute(() -> ClientPowerState.applyPowersSync(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.SyncPowersChunkS2C.CHANNEL,
            (mc, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.SyncPowersChunkS2C payload =
                    dev.overgrown.apoli.network.payload.SyncPowersChunkS2C.read(buf);
                mc.execute(() -> ClientPowerState.applyPowersChunk(payload));
            });

        ClientPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.SyncResourceTablesS2C.CHANNEL, (mc, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.SyncResourceTablesS2C tables =
                    dev.overgrown.apoli.network.payload.SyncResourceTablesS2C.read(buf);
                mc.execute(() -> ClientPowerState.applyResourceTables(tables));
            });

        ClientPlayNetworking.registerGlobalReceiver(SyncEntityPowersS2C.CHANNEL, (mc, handler, buf, sender) -> {
            SyncEntityPowersS2C payload = SyncEntityPowersS2C.read(buf);
            mc.execute(() -> ClientPowerState.applyEntityPowersSync(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.DevModeS2C.CHANNEL, (mc, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.DevModeS2C mode =
                    dev.overgrown.apoli.network.payload.DevModeS2C.read(buf);
                mc.execute(() -> dev.overgrown.apoli.client.ClientDevMode.set(mode.enabled()));
            });

        ClientPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.SyncEntitySetsS2C.CHANNEL, (mc, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.SyncEntitySetsS2C sets =
                    dev.overgrown.apoli.network.payload.SyncEntitySetsS2C.read(buf);
                mc.execute(() -> dev.overgrown.apoli.client.ClientEntitySets.apply(sets.powerId(), sets.members()));
            });

        ClientPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.SyncAuxIntsS2C.CHANNEL, (mc, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.SyncAuxIntsS2C aux =
                    dev.overgrown.apoli.network.payload.SyncAuxIntsS2C.read(buf);
                mc.execute(() -> ClientPowerState.applyAuxInts(aux));
            });

        ClientPlayNetworking.registerGlobalReceiver(PowerActivatedS2C.CHANNEL, (mc, handler, buf, sender) -> {
            PowerActivatedS2C payload = PowerActivatedS2C.read(buf);
            mc.execute(() -> ClientPowerState.setCooldown(payload.power(), payload.cooldown()));
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncKeybindsS2C.CHANNEL, (mc, handler, buf, sender) -> {
            SyncKeybindsS2C payload = SyncKeybindsS2C.read(buf);
            mc.execute(() -> DynamicKeyMappingManager.applyKeybinds(payload.keybinds()));
        });

        ClientPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.PowerInventoryS2C.CHANNEL, (mc, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.PowerInventoryS2C payload =
                    dev.overgrown.apoli.network.payload.PowerInventoryS2C.read(buf);
                mc.execute(() -> dev.overgrown.apoli.client.ClientPowerState.applyPowerInventory(payload));
            });

        ClientPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.MountOffsetS2C.CHANNEL, (mc, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.MountOffsetS2C payload =
                    dev.overgrown.apoli.network.payload.MountOffsetS2C.read(buf);
                mc.execute(() -> dev.overgrown.apoli.mount.MountOffsets.put(payload.passengerId(),
                    new dev.overgrown.apoli.mount.MountOffsets.Offset(
                        payload.x(), payload.y(), payload.z(), payload.space(), payload.rotation())));
            });

        dev.overgrown.apoli.power.builtin.InventoryPower.setClientLookup((holder, powerId) ->
            holder == net.minecraft.client.Minecraft.getInstance().player
                ? dev.overgrown.apoli.client.ClientPowerState.powerInventory(powerId)
                : null);

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.ScaleSyncS2C.CHANNEL,
            (client, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.ScaleSyncS2C payload =
                    dev.overgrown.apoli.network.payload.ScaleSyncS2C.read(buf);
                client.execute(() -> {
                    if (client.level == null) return;
                    net.minecraft.world.entity.Entity entity = client.level.getEntity(payload.entityId());
                    if (entity == null) return;
                    dev.overgrown.apoli.scale.ScaleSync.decode(entity, payload.data());
                });
            });

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.TickRateS2C.CHANNEL,
            (client, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.TickRateS2C payload =
                    dev.overgrown.apoli.network.payload.TickRateS2C.read(buf);
                client.execute(() -> dev.overgrown.apoli.client.ClientTickRates.set(
                    payload.entityId(), payload.rate(), payload.baseRate()));
            });

        ClientPlayNetworking.registerGlobalReceiver(ApplyVelocityS2C.CHANNEL, (mc, handler, buf, sender) -> {
            ApplyVelocityS2C payload = ApplyVelocityS2C.read(buf);
            mc.execute(() -> {
                if (mc.level == null) return;
                net.minecraft.world.entity.Entity e = mc.level.getEntity(payload.entityId());
                if (e == null) return;
                net.minecraft.world.phys.Vec3 delta = new net.minecraft.world.phys.Vec3(payload.x(), payload.y(), payload.z());
                e.setDeltaMovement(payload.set() ? delta : e.getDeltaMovement().add(delta));
            });
        });

        dev.overgrown.apoli.client.disguise.ClientDisguiseManager.install();
        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.DisguiseUpdateS2C.CHANNEL, (mc, handler, buf, sender) -> {
            dev.overgrown.apoli.network.payload.DisguiseUpdateS2C payload = dev.overgrown.apoli.network.payload.DisguiseUpdateS2C.read(buf);
            mc.execute(() -> payload.data().ifPresentOrElse(
                data -> dev.overgrown.apoli.client.disguise.ClientDisguiseManager.apply(payload.entityId(), data),
                () -> dev.overgrown.apoli.client.disguise.ClientDisguiseManager.remove(payload.entityId())));
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.ProtocolVersionPayload.CHANNEL, (mc, handler, buf, sender) -> {
            dev.overgrown.apoli.network.payload.ProtocolVersionPayload payload =
                dev.overgrown.apoli.network.payload.ProtocolVersionPayload.read(buf);
            mc.execute(() -> dev.overgrown.apoli.client.ClientProtocolState.setServerVersion(payload.version()));
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.TextDisplayS2C.CHANNEL, (mc, handler, buf, sender) -> {
            dev.overgrown.apoli.network.payload.TextDisplayS2C payload = dev.overgrown.apoli.network.payload.TextDisplayS2C.read(buf);
            mc.execute(() -> dev.overgrown.apoli.client.TextOverlayRenderer.apply(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.LabelUpdateS2C.CHANNEL, (mc, handler, buf, sender) -> {
            dev.overgrown.apoli.network.payload.LabelUpdateS2C payload = dev.overgrown.apoli.network.payload.LabelUpdateS2C.read(buf);
            mc.execute(() -> dev.overgrown.apoli.client.ClientLabelState.apply(payload.entityId(), payload.texts()));
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.ForceKeyS2C.CHANNEL, (mc, handler, buf, sender) -> {
            dev.overgrown.apoli.network.payload.ForceKeyS2C payload = dev.overgrown.apoli.network.payload.ForceKeyS2C.read(buf);
            mc.execute(() -> dev.overgrown.apoli.client.ForcedKeys.force(payload.key(), payload.duration(), payload.release()));
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.SyncShaderS2C.CHANNEL, (mc, handler, buf, sender) -> {
            dev.overgrown.apoli.network.payload.SyncShaderS2C payload = dev.overgrown.apoli.network.payload.SyncShaderS2C.read(buf);
            mc.execute(() -> dev.overgrown.apoli.client.ShaderPowerState.accept(payload.shader(), payload.toggleable()));
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            new dev.overgrown.apoli.network.payload.ProtocolVersionPayload(
                dev.overgrown.apoli.network.ProtocolCompat.VERSION).write(buf);
            ClientPlayNetworking.send(dev.overgrown.apoli.network.payload.ProtocolVersionPayload.CHANNEL, buf);
            if (dev.overgrown.apoli.client.ApoliClientConfig.get().speechToAction()) {
                dev.overgrown.apoli.client.speech.SpeechClient.onJoin();
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.SkillDefsSyncS2C.CHANNEL, (mc, handler, buf, sender) -> {
            dev.overgrown.apoli.network.payload.SkillDefsSyncS2C payload = dev.overgrown.apoli.network.payload.SkillDefsSyncS2C.read(buf);
            mc.execute(() -> dev.overgrown.apoli.client.skill.ClientSkillState.applyDefs(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.SkillStateSyncS2C.CHANNEL, (mc, handler, buf, sender) -> {
            dev.overgrown.apoli.network.payload.SkillStateSyncS2C payload = dev.overgrown.apoli.network.payload.SkillStateSyncS2C.read(buf);
            mc.execute(() -> dev.overgrown.apoli.client.skill.ClientSkillState.applyState(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.RadialMenuOpenS2C.CHANNEL, (mc, handler, buf, sender) -> {
            dev.overgrown.apoli.network.payload.RadialMenuOpenS2C payload = dev.overgrown.apoli.network.payload.RadialMenuOpenS2C.read(buf);
            mc.execute(() -> mc.setScreen(new dev.overgrown.apoli.client.radial.RadialMenuScreen(payload)));
        });

        ClientPlayNetworking.registerGlobalReceiver(RopeCreateS2C.CHANNEL, (mc, handler, buf, sender) -> {
            RopeCreateS2C payload = RopeCreateS2C.read(buf);
            mc.execute(() -> {
                if (mc.level != null) RopeClientManager.attach(payload, mc.level);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(RopeDeleteS2C.CHANNEL, (mc, handler, buf, sender) -> {
            RopeDeleteS2C payload = RopeDeleteS2C.read(buf);
            mc.execute(() -> RopeClientManager.detach(payload.id()));
        });

        ClientPlayNetworking.registerGlobalReceiver(RopeVerletLengthS2C.CHANNEL, (mc, handler, buf, sender) -> {
            RopeVerletLengthS2C payload = RopeVerletLengthS2C.read(buf);
            mc.execute(() -> {
                VerletRopeState rope = RopeClientManager.get(payload.id());
                if (rope != null) rope.targetLength = payload.length();
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) ->
            mc.execute(() -> {
                DynamicKeyMappingManager.unregisterAll();
                ClientPowerState.clear();
                dev.overgrown.apoli.client.ClientEntitySets.clear();
                dev.overgrown.apoli.client.ClientDevMode.clear();
                dev.overgrown.apoli.mount.MountOffsets.clearAll();
                dev.overgrown.apoli.client.ShaderPowerState.clear();
                dev.overgrown.apoli.client.render.BlockRenderRules.clear();
                dev.overgrown.apoli.client.render.ClientRenderFlags.clear();
                dev.overgrown.apoli.client.TextOverlayRenderer.clear();
                dev.overgrown.apoli.client.ClientLabelState.clear();
                RopeClientManager.clear();
                KeyPressWatcher.reset();
                dev.overgrown.apoli.client.ApoliKeyMappings.reset();
                dev.overgrown.apoli.client.ForcedKeys.clear();
                dev.overgrown.apoli.client.BlockedKeys.clear();
                dev.overgrown.apoli.client.CursorSpeedState.reset();
                dev.overgrown.apoli.client.disguise.ClientDisguiseManager.clear();
                dev.overgrown.apoli.client.skill.ClientSkillState.clear();
                dev.overgrown.apoli.compat.figura.FiguraModelPowerManager.clear();
                dev.overgrown.apoli.client.ClientProtocolState.reset();
                dev.overgrown.apoli.client.speech.SpeechClient.onLeave();
            }));

        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                @Override
                public net.minecraft.resources.ResourceLocation getFabricId() {
                    return Apoli.id("figura_avatars");
                }

                @Override
                public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                    dev.overgrown.apoli.compat.figura.FiguraModelPowerManager.onResourcesReloaded();
                    dev.overgrown.apoli.client.ShaderPowerState.invalidate();
                    dev.overgrown.apoli.client.particle.ParticleSheet.clearCache();
                    dev.overgrown.apoli.client.particle.ParticleTextures.clearCache();
                    dev.overgrown.apoli.client.render.AnimatedTextures.clearCache();
                }
            });

        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(dev.overgrown.apoli.client.render.CustomModelManager.INSTANCE);
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(dev.overgrown.apoli.client.render.AnimationManager.INSTANCE);

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
            dev.overgrown.apoli.client.CursorSpeedState.tick(mc);
            dev.overgrown.apoli.client.MouseMovementWatcher.clientTick(mc);
            dev.overgrown.apoli.client.render.ClientRenderFlags.clientTick(mc);
            dev.overgrown.apoli.client.render.BlockRenderRules.clientTick(mc);
            if (mc.player != null && !mc.isPaused()) {
                while (SKILL_TREE_KEY.consumeClick()) {
                    if (mc.screen == null && dev.overgrown.apoli.client.skill.ClientSkillState.hasAnyTree()) {
                        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(dev.overgrown.apoli.network.payload.RequestSkillStateC2S.CHANNEL)) {
                            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(dev.overgrown.apoli.network.payload.RequestSkillStateC2S.CHANNEL, net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create());
                        }
                        mc.setScreen(new dev.overgrown.apoli.client.skill.SkillTreeScreen());
                    }
                }
            }
            PhasingRenderState.clientTick(mc);
            RopeClientManager.tick();
            dev.overgrown.apoli.client.TextOverlayRenderer.tick();
            dev.overgrown.apoli.client.disguise.ClientDisguiseManager.tick(mc);
            dev.overgrown.apoli.client.speech.SpeechClient.clientTick(mc);
            if (dev.overgrown.apoli.compat.ModCompat.FIGURA) {
                dev.overgrown.apoli.compat.figura.FiguraModelPowerManager.tick(mc);
            }
            dev.overgrown.apoli.client.PlayerModelTypeReporter.tick(mc);
            dev.overgrown.apoli.client.CameraPerspectiveReporter.tick(mc);
            dev.overgrown.apoli.client.ForcedKeys.tick();
            dev.overgrown.apoli.power.builtin.ModifyFogInterpolator.tick(mc.player);
        });

        WorldRenderEvents.AFTER_ENTITIES.register(RopeRenderer::render);

        HudRenderCallback.EVENT.register(OverlayRenderer::renderBelowHud);
        HudRenderCallback.EVENT.register(PowerHudRenderer::render);
        HudRenderCallback.EVENT.register(dev.overgrown.apoli.client.TextOverlayRenderer::render);
        HudRenderCallback.EVENT.register(OverlayRenderer::renderAboveHud);
        HudRenderCallback.EVENT.register(dev.overgrown.apoli.client.DevHudRenderer::render);
    }
}
