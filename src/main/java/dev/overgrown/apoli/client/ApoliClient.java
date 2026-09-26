package dev.overgrown.apoli.client;

import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.PowerContainerAttachment;
import dev.overgrown.apoli.keybind.HeldKeys;
import dev.overgrown.apoli.network.payload.KeyHeldC2S;
import dev.overgrown.apoli.client.rope.RopeClientManager;
import dev.overgrown.apoli.client.rope.RopeRenderer;
import dev.overgrown.apoli.entity.ApoliEntities;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.ApoliIds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Apoli.MOD_ID, value = Dist.CLIENT)
public final class ApoliClient {
    private ApoliClient() {}

    public static final net.minecraft.client.KeyMapping SKILL_TREE_KEY = new net.minecraft.client.KeyMapping(
        "key.apoli.skill_tree", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
        org.lwjgl.glfw.GLFW.GLFW_KEY_K, "key.categories.apoli");

    public static final net.minecraft.client.KeyMapping SPEECH_KEY = new net.minecraft.client.KeyMapping(
        "key.apoli.speech_to_action", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
        com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), "key.categories.apoli");

    @SubscribeEvent
    public static void onRegisterKeyMappings(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(SKILL_TREE_KEY);
        event.register(SPEECH_KEY);
        dev.overgrown.apoli.client.speech.SpeechClient.setPushToTalkKey(SPEECH_KEY);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerBelowAll(Apoli.id("overlay_below_hud"),
            (graphics, deltaTracker) -> OverlayRenderer.renderBelowHud(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
        event.registerBelow(VanillaGuiLayers.SELECTED_ITEM_NAME, Apoli.id("power_hud"),
            (graphics, deltaTracker) -> PowerHudRenderer.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
        event.registerAbove(VanillaGuiLayers.HOTBAR, Apoli.id("text_overlay"),
            (graphics, deltaTracker) -> TextOverlayRenderer.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
        event.registerAboveAll(Apoli.id("overlay_above_hud"),
            (graphics, deltaTracker) -> OverlayRenderer.renderAboveHud(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
        event.registerAboveAll(Apoli.id("dev_hud"),
            (graphics, deltaTracker) -> DevHudRenderer.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
        NeoForge.EVENT_BUS.register(GameBus.class);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ApoliEntities.CUSTOM_PROJECTILE.get(), CustomProjectileRenderer::new);
        event.registerEntityRenderer(ApoliEntities.MINION.get(), dev.overgrown.apoli.client.summon.MinionRenderer::new);
        event.registerEntityRenderer(ApoliEntities.CLONE.get(), dev.overgrown.apoli.client.summon.CloneRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpecial(dev.overgrown.apoli.particle.ApoliParticles.CUSTOM.get(),
            new dev.overgrown.apoli.client.particle.CustomParticle.Provider());
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(dev.overgrown.apoli.client.summon.SummonModelLayers.MINION,
            dev.overgrown.apoli.client.summon.MinionModel::createBodyLayer);
        event.registerLayerDefinition(dev.overgrown.apoli.client.summon.SummonModelLayers.CLONE,
            () -> net.minecraft.client.model.geom.builders.LayerDefinition.create(
                net.minecraft.client.model.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, false), 64, 64));
        event.registerLayerDefinition(dev.overgrown.apoli.client.summon.SummonModelLayers.CLONE_SLIM,
            () -> net.minecraft.client.model.geom.builders.LayerDefinition.create(
                net.minecraft.client.model.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, true), 64, 64));
    }

    @SubscribeEvent
    public static void onRegisterClientReloadListeners(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> {
            dev.overgrown.apoli.compat.figura.FiguraModelPowerManager.onResourcesReloaded();
            ShaderPowerState.invalidate();
            dev.overgrown.apoli.client.particle.ParticleSheet.clearCache();
            dev.overgrown.apoli.client.particle.ParticleTextures.clearCache();
            dev.overgrown.apoli.client.render.AnimatedTextures.clearCache();
        });
        event.registerReloadListener(dev.overgrown.apoli.client.render.CustomModelManager.INSTANCE);
        event.registerReloadListener(dev.overgrown.apoli.client.render.AnimationManager.INSTANCE);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PowerContainerAttachment.setClientLookup(ClientPowerState::containerFor);
            dev.overgrown.apoli.power.PowerResources.setClientCooldownLookup((owner, powerId) ->
                owner == Minecraft.getInstance().player ? ClientPowerState.getCooldown(powerId) : 0);
            dev.overgrown.apoli.power.builtin.InventoryPower.setClientLookup((holder, powerId) ->
                holder == Minecraft.getInstance().player ? ClientPowerState.powerInventory(powerId) : null);
            HeldKeys.setClientLookup((entity, key, grace) ->
                entity == Minecraft.getInstance().player && KeyPressWatcher.isLocalHeld(key, grace));
            KeyPressWatcher.setSender(keys -> PacketDistributor.sendToServer(new KeyHeldC2S(keys)));
            dev.overgrown.apoli.client.disguise.ClientDisguiseManager.install();
            if (dev.overgrown.apoli.compat.ModCompat.LAMBDYNLIGHTS) {
                dev.overgrown.apoli.compat.lambdynlights.LambDynamicLightsCompat.init();
            }
            if (dev.overgrown.apoli.compat.ModCompat.EARS) {
                dev.overgrown.apoli.compat.ears.EarsCompat.init();
            }
            if (dev.overgrown.apoli.compat.ModCompat.ENTITY_MODEL_FEATURES) {
                dev.overgrown.apoli.compat.entitymodelfeatures.EntityModelFeaturesCompat.init();
            }
        });
    }

    public static final class GameBus {
        private GameBus() {}

        @SubscribeEvent
        public static void onClientTickPre(ClientTickEvent.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && !mc.isPaused()) ApoliKeyHandler.onClientTick();
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
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
            PlayerModelTypeReporter.tick(mc);
            CameraPerspectiveReporter.tick(mc);
            if (mc.player == null || mc.isPaused()) {
                ForcedKeys.tick();
                return;
            }
            while (SKILL_TREE_KEY.consumeClick()) {
                if (mc.screen == null && dev.overgrown.apoli.client.skill.ClientSkillState.hasAnyTree()) {
                    if (mc.getConnection() != null && mc.getConnection().hasChannel(dev.overgrown.apoli.network.payload.RequestSkillStateC2S.TYPE)) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(dev.overgrown.apoli.network.payload.RequestSkillStateC2S.INSTANCE);
                    }
                    mc.setScreen(new dev.overgrown.apoli.client.skill.SkillTreeScreen());
                }
            }
            ForcedKeys.tick();
            dev.overgrown.apoli.power.builtin.ModifyFogInterpolator.tick(mc.player);
        }

        @SubscribeEvent
        public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
            DynamicKeyMappingManager.unregisterAll();
            ClientPowerState.clear();
            dev.overgrown.apoli.client.ClientEntitySets.clear();
            dev.overgrown.apoli.client.ClientDevMode.clear();
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
        }

        @SubscribeEvent
        public static void onEntityLeaveLevel(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
            if (!event.getLevel().isClientSide()) return;
            ClientPowerState.removeEntity(event.getEntity().getId());
            ClientLabelState.removeEntity(event.getEntity().getId());
        }

        @SubscribeEvent
        public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
            if (ApoliClientConfig.get().speechToAction()) {
                dev.overgrown.apoli.client.speech.SpeechClient.onJoin();
            }
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            RopeRenderer.render(event);
        }

        @SubscribeEvent
        public static void onRenderBlockOverlay(RenderBlockScreenEffectEvent event) {
            if (event.getOverlayType() != RenderBlockScreenEffectEvent.OverlayType.BLOCK) return;
            Entity camera = Minecraft.getInstance().getCameraEntity();
            if (camera instanceof LivingEntity living && PowerLookup.hasActive(living, ApoliIds.PHASING)) {
                event.setCanceled(true);
            }
        }
    }
}
