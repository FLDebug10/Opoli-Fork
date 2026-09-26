package dev.overgrown.apoli;

import dev.overgrown.apoli.action.ActionTypes;
import dev.overgrown.apoli.action.DelayedActionQueue;
import dev.overgrown.apoli.alias.ApoliAliases;
import dev.overgrown.apoli.command.ApoliPowerCommand;
import dev.overgrown.apoli.command.ApoliResourceCommand;
import dev.overgrown.apoli.condition.ConditionTypes;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.effects.CustomEffectNetworking;
import dev.overgrown.apoli.effects.CustomEffectRegistry;
import dev.overgrown.apoli.effects.EffectConfig;
import dev.overgrown.apoli.loader.ApoliKeybindLoader;
import dev.overgrown.apoli.loader.ApoliReloadListener;
import dev.overgrown.apoli.keybind.HeldKeys;
import dev.overgrown.apoli.network.payload.KeyHeldC2S;
import dev.overgrown.apoli.network.payload.PowerActivatedS2C;
import dev.overgrown.apoli.network.payload.PowerActivationC2S;
import dev.overgrown.apoli.network.payload.PowerToggleC2S;
import dev.overgrown.apoli.network.payload.SyncEntityPowersS2C;
import dev.overgrown.apoli.network.payload.SyncKeybindsS2C;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerContainerImpl;
import dev.overgrown.apoli.power.PoweredEntities;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.PowerType;
import dev.overgrown.apoli.power.PowerTypeRegistry;
import dev.overgrown.apoli.power.PowerTypes;
import dev.overgrown.apoli.power.builtin.ActionOnCallbackPower;
import dev.overgrown.apoli.power.builtin.ActionOnKeyPressPower;
import dev.overgrown.apoli.power.builtin.ActionOnUseHandler;
import dev.overgrown.apoli.power.builtin.FireProjectilePower;
import dev.overgrown.apoli.power.builtin.InventoryPower;
import dev.overgrown.apoli.power.builtin.TogglePower;
import dev.overgrown.apoli.power.PowerResources;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.world.InteractionResult;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class Apoli implements ModInitializer {
    public static final String MOD_ID = "apoli";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private ApoliReloadListener powerLoader;
    private ApoliKeybindLoader keybindLoader;
    private dev.overgrown.apoli.skill.SkillTreeLoader skillLoader;

    @Override
    public void onInitialize() {
        LOGGER.info("[Apoli] Initializing...");

        ApoliAliases.bootstrap();
        ConditionTypes.bootstrap();
        ActionTypes.bootstrap();
        PowerTypes.bootstrap();
        dev.overgrown.apoli.scale.ScaleTypes.bootstrap();
        dev.overgrown.apoli.power.PowerSources.bootstrap();
        dev.overgrown.apoli.advancement.ApoliCriteria.register();
        dev.overgrown.apoli.attribution.PowerCause.bootstrap();
        dev.overgrown.apoli.compat.accessory.AccessoryCompat.init();
        if (dev.overgrown.apoli.compat.ModCompat.HARDCORE_REVIVAL) {
            dev.overgrown.apoli.compat.hardcorerevival.HardcoreRevivalCompat.init();
        }
        dev.overgrown.apoli.attribute.ApoliAttributes.register();
        dev.overgrown.apoli.entity.ApoliEntities.register();
        dev.overgrown.apoli.particle.ApoliParticles.register();
        dev.overgrown.apoli.item.ApoliLootFunctions.register();
        java.util.Objects.requireNonNull(PowerContainerAttachment.TYPE);
        java.util.Objects.requireNonNull(dev.overgrown.apoli.skill.SkillDataAttachment.TYPE);

        powerLoader = new ApoliReloadListener();
        keybindLoader = new ApoliKeybindLoader();
        skillLoader = new dev.overgrown.apoli.skill.SkillTreeLoader();
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
            new IdentifiedReloader(id("powers_reloader"), powerLoader));
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
            new IdentifiedReloader(id("keybinds_reloader"), keybindLoader));
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
            new IdentifiedReloader(id("skill_trees_reloader"), skillLoader));
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
            new IdentifiedReloader(id("global_powers_reloader"), new dev.overgrown.apoli.global.GlobalPowerLoader()));
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
            new IdentifiedReloader(id("scripts_reloader"), new dev.overgrown.apoli.script.ScriptLoader()));

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            powerLoader.attachServer(server);
            keybindLoader.attachServer(server);
            dev.overgrown.apoli.entity.GrabManager.clearAll();
            dev.overgrown.apoli.block.GhostBlocks.clear();
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            dev.overgrown.apoli.skill.SkillRegistry.reportOrphanedSkills();
            ApoliNetwork.broadcastPowers(server);
            ApoliNetwork.broadcastKeybinds(server, SyncKeybindsS2C.fromCurrent());
            if (EffectConfig.get().enabled()) CustomEffectRegistry.finishReload(true);
            dev.overgrown.apoli.recipe.ApoliPowerRecipes.inject(server);
            dev.overgrown.apoli.compat.voicechat.VoiceState.setServer(server);
            dev.overgrown.apoli.compat.voicechat.VoiceState.setCallbacks(
                dev.overgrown.apoli.compat.voicechat.VoicePowerHandler::onSpeakStart,
                dev.overgrown.apoli.compat.voicechat.VoicePowerHandler::onSpeakStop);
        });
        ServerLifecycleEvents.START_DATA_PACK_RELOAD.register(((server, resourceManager) -> {
            if (EffectConfig.get().enabled()) {
                CustomEffectRegistry.reloading = true;
                CustomEffectRegistry.purge(server);
            }
        }));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            dev.overgrown.apoli.skill.SkillRegistry.reportOrphanedSkills();
            dev.overgrown.apoli.global.GlobalPowers.reapplyAll(server);
            dev.overgrown.apoli.recipe.ApoliPowerRecipes.inject(server);
            if (EffectConfig.get().enabled()) CustomEffectRegistry.finishReload(success);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                dev.overgrown.apoli.skill.SkillTrees.grantOnJoin(player);
                ApoliNetwork.sendSkillDefs(player);
                ApoliNetwork.sendSkillState(player);
                if (ServerPlayNetworking.canSend(player, CustomEffectNetworking.SyncCustomEffectsPacket.CHANNEL)) {
                    CustomEffectNetworking.sync(player, server.isSingleplayerOwner(player.getGameProfile()));
                }
            }
            CustomEffectRegistry.reloading = false;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
            dev.overgrown.apoli.block.GhostBlocks.restoreAll(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PoweredEntities.clear();
            dev.overgrown.apoli.block.GhostBlocks.clear();
            dev.overgrown.apoli.entity.PlayerModelTypes.clear();
            dev.overgrown.apoli.tick.TickRates.clear();
            DelayedActionQueue.clear();
            dev.overgrown.apoli.rope.RopeManager.clear();
            dev.overgrown.apoli.mount.MountOffsets.clearAll();
            dev.overgrown.apoli.entity.ProjectileTickManager.clearAll();
            dev.overgrown.apoli.compat.icarus.WingsAccess.clear();
            dev.overgrown.apoli.compat.voicechat.VoiceState.clear();
            dev.overgrown.apoli.compat.voicechat.VoiceHearing.reset();
            dev.overgrown.apoli.tick.TickRates.clear();
        });

        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(
            (player, level, hand, hitResult) ->
                dev.overgrown.apoli.power.builtin.BlockUseHandler.handle(player, level, hand, hitResult));

        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) ->
            dev.overgrown.apoli.compat.voicechat.MessagePowerHandler.handle(
                sender, sender, message.signedContent(), params));

        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register((message, source, params) ->
            dev.overgrown.apoli.compat.voicechat.MessagePowerHandler.handle(
                source.getEntity(), source.getPlayer(), message.signedContent(), params));

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
            ApoliPowerCommand.register(dispatcher);
            ApoliResourceCommand.register(dispatcher);
            dev.overgrown.apoli.command.ApoliSkillTreeCommand.register(dispatcher);
            dev.overgrown.apoli.command.ApoliDisguiseCommand.register(dispatcher);
            dev.overgrown.apoli.command.ApoliCloneCommand.register(dispatcher);
            dev.overgrown.apoli.command.ApoliMountCommand.register(dispatcher);
            dev.overgrown.apoli.command.ApoliKeyCommand.register(dispatcher);
            dev.overgrown.apoli.command.ApoliDevModeCommand.register(dispatcher);
            dev.overgrown.apoli.command.ApoliTickCommand.register(dispatcher);
            dev.overgrown.apoli.command.ApoliScaleCommand.register(dispatcher);
            if (dev.overgrown.apoli.compat.ModCompat.anyAccessory()) {
                dev.overgrown.apoli.compat.accessory.command.AccessoryCommand.register(dispatcher);
            }
            dispatcher.register(net.minecraft.commands.Commands.literal("apoli:speech")
                .requires(source -> source.hasPermission(0))
                .then(net.minecraft.commands.Commands.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(ctx -> {
                        net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String text = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "text");
                        String language = dev.overgrown.apoli.util.SpeechLanguage.of(player);
                        dev.overgrown.apoli.compat.voicechat.ActionOnSpeechPower.fire(player, text, language);
                        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("[Apoli] Simulated speech: \"" + text + "\""), false);
                        return 1;
                    })));
            dispatcher.register(net.minecraft.commands.Commands.literal("apoli:message")
                .requires(source -> source.hasPermission(0))
                .then(net.minecraft.commands.Commands.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(ctx -> {
                        Entity holder = ctx.getSource().getEntityOrException();
                        String text = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "text");
                        dev.overgrown.apoli.compat.voicechat.ActionOnSendingMessagePower.Report report =
                            dev.overgrown.apoli.compat.voicechat.ActionOnSendingMessagePower.diagnose(
                                holder, text, net.minecraft.network.chat.ChatType.CHAT.location());
                        for (String line : report.lines()) {
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(line), false);
                        }
                        return report.matched() ? 1 : 0;
                    })));
        });

        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(
            (player, level, hand, hitResult) ->
                dev.overgrown.apoli.power.builtin.BlockUseHandler.handle(player, level, hand, hitResult));

        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) ->
            dev.overgrown.apoli.compat.voicechat.MessagePowerHandler.handle(
                sender, sender, message.signedContent(), params));

        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register((message, source, params) ->
            dev.overgrown.apoli.compat.voicechat.MessagePowerHandler.handle(
                source.getEntity(), source.getPlayer(), message.signedContent(), params));

        if (dev.overgrown.apoli.compat.ModCompat.PUFFISH_SKILLS) {
            dev.overgrown.apoli.compat.skills.SkillsCompat.init();
        }

        if (dev.overgrown.apoli.compat.ModCompat.YIGD) {
            dev.overgrown.apoli.compat.yigd.YigdCompat.init();
        }

        if (dev.overgrown.apoli.compat.ModCompat.GRAVESTONES) {
            dev.overgrown.apoli.compat.gravestones.GravestonesCompat.init();
        }

        ServerTickEvents.END_SERVER_TICK.register(Apoli::onServerTick);

        EntityElytraEvents.CUSTOM.register((entity, tickElytra) ->
            PowerLookup.hasActive(entity, dev.overgrown.apoli.power.ApoliIds.ELYTRA_FLIGHT));

        ServerPlayNetworking.registerGlobalReceiver(CustomEffectNetworking.CustomEffectResponsePacket.CHANNEL, (server, player, handler, buf, responseSender) -> {
            CustomEffectNetworking.CustomEffectResponsePacket payload = new CustomEffectNetworking.CustomEffectResponsePacket(buf);
            server.execute(() -> CustomEffectRegistry.acknowledge(player, payload.success()));
        });

        ServerPlayNetworking.registerGlobalReceiver(PowerActivationC2S.CHANNEL, (server, player, handler, buf, sender) -> {
            PowerActivationC2S payload = PowerActivationC2S.read(buf);
            server.execute(() -> handleActivation(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(PowerToggleC2S.CHANNEL, (server, player, handler, buf, sender) -> {
            PowerToggleC2S payload = PowerToggleC2S.read(buf);
            server.execute(() -> handleToggle(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.PlayerModelTypeC2S.CHANNEL,
            (server, player, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.PlayerModelTypeC2S modelPayload =
                    dev.overgrown.apoli.network.payload.PlayerModelTypeC2S.read(buf);
                server.execute(() -> dev.overgrown.apoli.entity.PlayerModelTypes.set(
                    player.getUUID(), modelPayload.modelType()));
            });

        ServerPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.CameraPerspectiveC2S.CHANNEL,
            (server, player, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.CameraPerspectiveC2S cameraPayload =
                    dev.overgrown.apoli.network.payload.CameraPerspectiveC2S.read(buf);
                server.execute(() -> dev.overgrown.apoli.entity.CameraPerspectives.set(
                    player.getUUID(), cameraPayload.firstPerson()));
            });

        ServerPlayNetworking.registerGlobalReceiver(KeyHeldC2S.CHANNEL, (server, player, handler, buf, sender) -> {
            KeyHeldC2S payload = KeyHeldC2S.read(buf);
            server.execute(() -> HeldKeys.setServerHeld(player, payload.keys()));
        });

        ServerPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.ScrollWheelC2S.CHANNEL, (server, player, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.ScrollWheelC2S payload =
                    dev.overgrown.apoli.network.payload.ScrollWheelC2S.read(buf);
                server.execute(() -> dev.overgrown.apoli.power.builtin.ActionOnScrollWheelPower.scroll(
                    player,
                    payload.up() ? dev.overgrown.apoli.data.ScrollDirection.UP
                        : dev.overgrown.apoli.data.ScrollDirection.DOWN,
                    payload.notches()));
            });

        ServerPlayNetworking.registerGlobalReceiver(
            dev.overgrown.apoli.network.payload.MouseMovementC2S.CHANNEL, (server, player, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.MouseMovementC2S payload =
                    dev.overgrown.apoli.network.payload.MouseMovementC2S.read(buf);
                server.execute(() -> dev.overgrown.apoli.power.builtin.ActionOnMouseMovementPower.moved(
                    player, payload.yaw(), payload.pitch()));
            });

        ServerPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.SpeechTriggerC2S.CHANNEL,
            (server, player, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.SpeechTriggerC2S payload =
                    dev.overgrown.apoli.network.payload.SpeechTriggerC2S.read(buf);
                server.execute(() ->
                    dev.overgrown.apoli.compat.voicechat.ActionOnSpeechPower.fireTrigger(player, payload.power()));
            });

        ServerPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.BuySkillC2S.CHANNEL,
            (server, player, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.BuySkillC2S payload = dev.overgrown.apoli.network.payload.BuySkillC2S.read(buf);
                server.execute(() -> {
                    if (dev.overgrown.apoli.skill.SkillTrees.tryPurchase(player, payload.skill())) {
                        ApoliNetwork.sendSkillState(player);
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.RequestSkillStateC2S.CHANNEL,
            (server, player, handler, buf, sender) -> server.execute(() -> ApoliNetwork.sendSkillState(player)));

        ServerPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.RefundSkillC2S.CHANNEL,
            (server, player, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.RefundSkillC2S payload = dev.overgrown.apoli.network.payload.RefundSkillC2S.read(buf);
                server.execute(() -> {
                    if (dev.overgrown.apoli.skill.SkillTrees.tryRefund(player, payload.skill(), false)) {
                        ApoliNetwork.sendSkillState(player);
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.RadialMenuSelectC2S.CHANNEL,
            (server, player, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.RadialMenuSelectC2S payload =
                    dev.overgrown.apoli.network.payload.RadialMenuSelectC2S.read(buf);
                server.execute(() ->
                    dev.overgrown.apoli.radial.RadialMenuManager.select(player, payload.nonce(), payload.index()));
            });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            HeldKeys.clearServer(handler.player.getUUID());
            dev.overgrown.apoli.dev.DevMode.forget(handler.player.getUUID());
            dev.overgrown.apoli.network.ProtocolCompat.forget(handler.player.getUUID());
            dev.overgrown.apoli.network.PowerSyncCache.forget(handler.player.getUUID());
            dev.overgrown.apoli.compat.voicechat.ActionOnSpeechPower.forget(handler.player.getUUID());
            if (dev.overgrown.apoli.power.PowerTypeRegistry.get(dev.overgrown.apoli.power.ApoliIds.ACTION_ON_KEY_SEQUENCE)
                instanceof dev.overgrown.apoli.power.builtin.ActionOnKeySequencePower seq) {
                seq.forget(handler.player.getUUID());
            }
            dev.overgrown.apoli.radial.RadialMenuManager.forget(handler.player.getUUID());
            dev.overgrown.apoli.power.builtin.ShaderPower.forget(handler.player.getUUID());
            dev.overgrown.apoli.entity.GrabManager.release(handler.player.getUUID());
            dev.overgrown.apoli.entity.CameraPerspectives.remove(handler.player.getUUID());
        });

        ServerPlayConnectionEvents.JOIN.register(((handler, sender, server) -> {
            CustomEffectNetworking.syncOnJoin(handler.player, server.isSingleplayerOwner(handler.player.getGameProfile()));
        }));

        ServerPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.ProtocolVersionPayload.CHANNEL,
            (server, player, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.ProtocolVersionPayload payload =
                    dev.overgrown.apoli.network.payload.ProtocolVersionPayload.read(buf);
                server.execute(() -> {
                    int serverVersion = dev.overgrown.apoli.network.ProtocolCompat.VERSION;
                    if (payload.version() != serverVersion) {
                        handler.disconnect(net.minecraft.network.chat.Component.literal(
                            "Apoli network protocol mismatch (server " + serverVersion + ", client " + payload.version()
                                + "). Update Apoli to the same build on both sides."));
                        return;
                    }
                    ApoliNetwork.sendProtocolVersion(player);
                    if (dev.overgrown.apoli.network.ProtocolCompat.consumeSentLegacy(player)) {
                        ApoliNetwork.sendSkillDefs(player);
                        ApoliNetwork.sendSkillState(player);
                    }
                    dev.overgrown.apoli.network.PowerSyncCache.onProtocolEcho(player);
                    CustomEffectNetworking.syncIfPending(player, server.isSingleplayerOwner(player.getGameProfile()));
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.RopeChangeLengthC2S.CHANNEL,
            (server, player, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.RopeChangeLengthC2S p = dev.overgrown.apoli.network.payload.RopeChangeLengthC2S.read(buf);
                server.execute(() -> dev.overgrown.apoli.rope.RopeManager.handleLengthChange(player, p.ropeId(), p.delta()));
            });

        ServerPlayNetworking.registerGlobalReceiver(dev.overgrown.apoli.network.payload.RopeSwingC2S.CHANNEL,
            (server, player, handler, buf, sender) -> {
                dev.overgrown.apoli.network.payload.RopeSwingC2S p = dev.overgrown.apoli.network.payload.RopeSwingC2S.read(buf);
                server.execute(() -> dev.overgrown.apoli.rope.RopeManager.applySwingInput(player, p.ropeId(), p.inputDir()));
            });

        UseEntityCallback.EVENT.register((player, level, hand, target, hitResult) -> {
            if (dev.overgrown.apoli.power.builtin.PreventUseHandler.isPrevented(player, target, hand)) {
                return InteractionResult.FAIL;
            }
            if (level.isClientSide()) return InteractionResult.PASS;
            return ActionOnUseHandler.fireOncePerTick(player, target, hand);
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            dev.overgrown.apoli.global.GlobalPowers.applyTo(entity);
            PowerContainer c = PowerContainer.of(entity);
            if (c != null && !c.isEmpty()) {
                PoweredEntities.register(entity);
                for (ResourceLocation powerId : c.allPowers()) {
                    dev.overgrown.apoli.power.builtin.ResourcePower.onEntityLoad(c, powerId);
                }
                ActionOnCallbackPower.fireLifecycle(entity, ActionOnCallbackPower.Config::entityActionAdded);
            }
            if (entity instanceof ServerPlayer sp) {
                dev.overgrown.apoli.entity.PlayerModelTypes.resolveFrom(sp.getUUID(), sp.getGameProfile());
                level.getServer().execute(() -> {
                    ApoliNetwork.sendKeybinds(sp, SyncKeybindsS2C.fromCurrent());
                    ApoliNetwork.sendPowers(sp);
                    sendEntitySync(sp);
                    if (dev.overgrown.apoli.compat.ModCompat.PUFFISH_SKILLS) {
                        dev.overgrown.apoli.compat.skills.SkillsCompat.onJoin(sp);
                    }
                    dev.overgrown.apoli.skill.SkillTrees.grantOnJoin(sp);
                    ApoliNetwork.sendSkillDefs(sp);
                    ApoliNetwork.sendSkillState(sp);
                    dev.overgrown.apoli.power.builtin.EntitySetPower.syncAllTo(sp);
                    dev.overgrown.apoli.scale.Scales.syncIfScaled(sp);
                });
            }
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            ActionOnCallbackPower.fireLifecycle(entity, ActionOnCallbackPower.Config::entityActionRemoved);
            PoweredEntities.unregister(entity);
            dev.overgrown.apoli.rope.RopeManager.onEntityGone(entity.getUUID());
            dev.overgrown.apoli.entity.LabelManager.onEntityGone(entity.getUUID());
            Entity.RemovalReason reason = entity.getRemovalReason();
            if (reason != null && reason.shouldDestroy()) {
                dev.overgrown.apoli.power.builtin.EntitySetPower.onEntityGone(entity.getUUID());
                dev.overgrown.apoli.power.builtin.AttributePower.onEntityGone(entity.getUUID());
            }
        });

        ResourceLocation afterAttachmentTransfer = id("after_attachment_transfer");
        ServerPlayerEvents.AFTER_RESPAWN.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, afterAttachmentTransfer);
        ServerPlayerEvents.AFTER_RESPAWN.register(afterAttachmentTransfer, (oldPlayer, newPlayer, alive) ->
            CustomEffectRegistry.dropOrphanedGrants(newPlayer));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            PoweredEntities.unregister(oldPlayer);
            dev.overgrown.apoli.power.builtin.ActionOverTimePower.resetEdges(newPlayer);
            dev.overgrown.apoli.scale.Scales.transfer(oldPlayer, newPlayer);
            resumePowers(newPlayer);
            dev.overgrown.apoli.power.PowerLookup.forEach(newPlayer,
                dev.overgrown.apoli.power.ApoliIds.STARTING_EQUIPMENT,
                dev.overgrown.apoli.power.builtin.StartingEquipmentPower.Config.class,
                cfg -> dev.overgrown.apoli.power.builtin.StartingEquipmentPower.onRespawn(newPlayer, cfg));
            ActionOnCallbackPower.fireRespawn(newPlayer);
        });

        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD
            .register((player, origin, destination) -> {
                resumePowers(player);
                dev.overgrown.apoli.scale.Scales.syncIfScaled(player);
            });

        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD
            .register((originalEntity, newEntity, origin, destination) -> {
                PoweredEntities.unregister(originalEntity);
                resumePowers(newEntity);
                dev.overgrown.apoli.scale.Scales.syncIfScaled(newEntity);
            });

        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(
            (entity, source) -> dev.overgrown.apoli.power.builtin.DeathHandler.onDeath(entity, source));

        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register(
            (entity, source, amount) -> !dev.overgrown.apoli.power.builtin.PreventDeathHandler.tryPrevent(entity, source, amount));

        dev.overgrown.apoli.scale.Scales.setSyncer(entity ->
            ApoliNetwork.broadcastScale(entity, new dev.overgrown.apoli.network.payload.ScaleSyncS2C(
                entity.getId(), dev.overgrown.apoli.scale.ScaleSync.encode(entity))));

        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
            dev.overgrown.apoli.scale.ScaleState scales =
                dev.overgrown.apoli.scale.Scales.stateOf(trackedEntity);
            if (scales != null && !scales.isDefault()) {
                ApoliNetwork.sendScale(player, new dev.overgrown.apoli.network.payload.ScaleSyncS2C(
                    trackedEntity.getId(), dev.overgrown.apoli.scale.ScaleSync.encode(trackedEntity)));
            }
        });

        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
            PowerContainer c = PowerContainer.of(trackedEntity);
            if (c instanceof PowerContainerImpl impl && !impl.isEmpty()) {
                ApoliNetwork.sendEntityPowers(player, new SyncEntityPowersS2C(
                    trackedEntity.getId(), impl.snapshot(), impl.auxIntSnapshot(), impl.suppressedPowers()));
                dev.overgrown.apoli.network.payload.SyncResourceTablesS2C tables = tablePayload(trackedEntity, impl);
                if (tables != null) ApoliNetwork.sendResourceTables(player, tables);
            }
        });

        dev.overgrown.apoli.entity.disguise.DisguiseManager.setBroadcaster((entity, data) ->
            ApoliNetwork.broadcastDisguise(entity, new dev.overgrown.apoli.network.payload.DisguiseUpdateS2C(entity.getId(), data)));
        dev.overgrown.apoli.entity.LabelManager.setBroadcaster((entity, texts) ->
            ApoliNetwork.broadcastLabel(entity, new dev.overgrown.apoli.network.payload.LabelUpdateS2C(entity.getId(), texts)));
        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
            dev.overgrown.apoli.entity.disguise.DisguiseData disguise =
                dev.overgrown.apoli.entity.disguise.DisguiseManager.get(trackedEntity.getUUID());
            if (disguise != null) {
                ApoliNetwork.sendDisguise(player, new dev.overgrown.apoli.network.payload.DisguiseUpdateS2C(
                    trackedEntity.getId(), java.util.Optional.of(disguise)));
            }
            java.util.Map<ResourceLocation, net.minecraft.network.chat.Component> labels =
                dev.overgrown.apoli.entity.LabelManager.textsFor(trackedEntity.getUUID());
            if (!labels.isEmpty()) {
                ApoliNetwork.sendLabel(player, new dev.overgrown.apoli.network.payload.LabelUpdateS2C(
                    trackedEntity.getId(), labels));
            }
            dev.overgrown.apoli.mount.MountOffsets.syncTo(player, trackedEntity);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            dev.overgrown.apoli.entity.disguise.DisguiseManager.onPlayerLeave(handler.player.getUUID());
            dev.overgrown.apoli.entity.LabelManager.onEntityGone(handler.player.getUUID());
            dev.overgrown.apoli.action.builtin.entity.TextAction.onPlayerLeave(handler.player.getUUID());
            dev.overgrown.apoli.entity.PlayerModelTypes.remove(handler.player.getUUID());
            dev.overgrown.apoli.power.builtin.InventoryPower.onPlayerLeave(handler.player.getUUID());
            dev.overgrown.apoli.compat.voicechat.VoiceHearing.forget(handler.player.getUUID());
            dev.overgrown.apoli.compat.voicechat.VoiceState.forget(handler.player.getUUID());
            CustomEffectRegistry.waiting.remove(handler.player.getUUID());
            CustomEffectNetworking.forget(handler.player.getUUID());
        });

        LOGGER.info("[Apoli] Ready. {} power type(s).", PowerTypeRegistry.view().size());
    }

    private static void onServerTick(MinecraftServer server) {
        DelayedActionQueue.tick();
        dev.overgrown.apoli.keybind.HeldKeys.tickForced();
        dev.overgrown.apoli.compat.voicechat.VoiceState.tick(server);
        dev.overgrown.apoli.compat.voicechat.VoiceHearing.tick(server);
        dev.overgrown.apoli.rope.RopeManager.tick(server);
        dev.overgrown.apoli.entity.GrabManager.tick(server);
        dev.overgrown.apoli.entity.ProjectileTickManager.tick(server);
        dev.overgrown.apoli.tick.TickRates.serverTick(server);
        boolean forcedKeys = dev.overgrown.apoli.keybind.HeldKeys.anyForced();
        PoweredEntities.forEach(entity -> {
            PowerContainer c = PowerContainer.of(entity);
            if (!(c instanceof PowerContainerImpl impl)) return;
            impl.tickActive();
            if (forcedKeys) dev.overgrown.apoli.keybind.KeyDispatch.tickForcedNonPlayer(entity);
            if (impl.isStructureDirty()) {
                syncEntityToTrackers(entity, impl);
                if (entity instanceof ServerPlayer sp) {
                    ApoliNetwork.sendEntityPowers(sp, fullPayload(sp, impl));
                    dev.overgrown.apoli.network.payload.SyncResourceTablesS2C tables = tablePayload(sp, impl);
                    if (tables != null) ApoliNetwork.sendResourceTables(sp, tables);
                }
                impl.clearStructureDirty();
                impl.clearDirty();
            } else if (impl.isDirty()) {
                broadcastAuxState(entity, impl);
                impl.clearDirty();
            }
            if (impl.isEmpty()) PoweredEntities.unregister(entity);
        });
        CustomEffectRegistry.tick(server);
        dev.overgrown.apoli.block.GhostBlocks.tick(server);
        dev.overgrown.apoli.power.builtin.ShaderPower.tick(server);
        dev.overgrown.apoli.power.builtin.EntitySetPower.flushPendingRemovals();
        dev.overgrown.apoli.power.builtin.EntitySetPower.flushSync(server);
    }

    private static void resumePowers(Entity entity) {
        PowerContainer container = PowerContainer.of(entity);
        if (!(container instanceof PowerContainerImpl impl) || impl.isEmpty()) return;
        PoweredEntities.register(entity);
        if (entity instanceof ServerPlayer player) {
            player.server.execute(() -> {
                ApoliNetwork.sendPowers(player);
                sendEntitySync(player);
            });
            return;
        }
        syncEntityToTrackers(entity, impl);
    }

    private static SyncEntityPowersS2C fullPayload(Entity entity, PowerContainerImpl impl) {
        return new SyncEntityPowersS2C(entity.getId(), impl.snapshot(), impl.auxIntSnapshot(), impl.suppressedPowers());
    }

    private static void broadcastAuxState(Entity entity, PowerContainerImpl impl) {
        dev.overgrown.apoli.network.payload.SyncAuxIntsS2C aux =
            new dev.overgrown.apoli.network.payload.SyncAuxIntsS2C(entity.getId(), impl.auxIntSnapshot());
        dev.overgrown.apoli.network.payload.SyncResourceTablesS2C tables = tablePayload(entity, impl);
        SyncEntityPowersS2C fallback = null;
        if (entity instanceof ServerPlayer self) {
            if (ApoliNetwork.canSendAuxInts(self)) {
                ApoliNetwork.sendAuxInts(self, aux);
            } else {
                fallback = fullPayload(entity, impl);
                ApoliNetwork.sendEntityPowers(self, fallback);
            }
            if (tables != null) ApoliNetwork.sendResourceTables(self, tables);
        }
        for (ServerPlayer viewer : PlayerLookup.tracking(entity)) {
            if (ApoliNetwork.canSendAuxInts(viewer)) {
                ApoliNetwork.sendAuxInts(viewer, aux);
            } else {
                if (fallback == null) fallback = fullPayload(entity, impl);
                ApoliNetwork.sendEntityPowers(viewer, fallback);
            }
            if (tables != null) ApoliNetwork.sendResourceTables(viewer, tables);
        }
    }

    private static void syncEntityToTrackers(Entity entity, PowerContainerImpl impl) {
        SyncEntityPowersS2C payload = fullPayload(entity, impl);
        dev.overgrown.apoli.network.payload.SyncResourceTablesS2C tables = tablePayload(entity, impl);
        for (ServerPlayer viewer : PlayerLookup.tracking(entity)) {
            ApoliNetwork.sendEntityPowers(viewer, payload);
            if (tables != null) ApoliNetwork.sendResourceTables(viewer, tables);
        }
    }

    private static dev.overgrown.apoli.network.payload.SyncResourceTablesS2C tablePayload(Entity entity,
                                                                                          PowerContainerImpl impl) {
        java.util.Map<net.minecraft.resources.ResourceLocation, int[]> tables = impl.auxIntsSnapshot();
        if (tables.isEmpty()) return null;
        return new dev.overgrown.apoli.network.payload.SyncResourceTablesS2C(entity.getId(), tables);
    }

    private static void sendEntitySync(ServerPlayer player) {
        PowerContainer c = PowerContainer.of(player);
        if (!(c instanceof PowerContainerImpl impl)) return;
        SyncEntityPowersS2C payload = new SyncEntityPowersS2C(player.getId(), impl.snapshot(), impl.auxIntSnapshot(), impl.suppressedPowers());
        dev.overgrown.apoli.network.payload.SyncResourceTablesS2C tables = tablePayload(player, impl);
        ApoliNetwork.sendEntityPowers(player, payload);
        if (tables != null) ApoliNetwork.sendResourceTables(player, tables);
        for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
            ApoliNetwork.sendEntityPowers(viewer, payload);
            if (tables != null) ApoliNetwork.sendResourceTables(viewer, tables);
        }
        impl.clearDirty();
        impl.clearStructureDirty();
    }

    private static void handleActivation(ServerPlayer player, PowerActivationC2S payload) {
        if (dev.overgrown.apoli.entity.GrabManager.keybindsDisabled(player.getUUID())) return;
        PowerContainer c = PowerContainer.of(player);
        if (c == null || !c.hasPower(payload.power()) || c.isSuppressed(payload.power())) return;
        if (dev.overgrown.apoli.power.builtin.PowerStoragePower.keyMuted(c, payload.power())) return;
        Power loaded = ApoliPowers.get(payload.power());
        if (loaded == null) return;
        PowerType<?> type = PowerTypeRegistry.get(loaded.typeId());
        if (loaded.condition().isPresent()
            && !loaded.condition().get().test(new EntityCtx(player, player.serverLevel()))) {
            return;
        }
        if (type instanceof ActionOnKeyPressPower active
            && loaded.config() instanceof ActionOnKeyPressPower.Config cfg) {
            if (active.tryActivate(payload.power(), cfg, c)) {
                ApoliNetwork.sendActivated(player, new PowerActivatedS2C(payload.power(), PowerResources.cooldownTicks(cfg.cooldown(), c)));
            }
        } else if (type instanceof FireProjectilePower fpp
            && loaded.config() instanceof FireProjectilePower.Config cfg) {
            if (fpp.tryActivate(payload.power(), cfg, c)) {
                ApoliNetwork.sendActivated(player, new PowerActivatedS2C(payload.power(), PowerResources.cooldownTicks(cfg.params().cooldown(), c)));
            }
        } else if (type instanceof InventoryPower inv
            && loaded.config() instanceof InventoryPower.Config cfg
            && c instanceof PowerContainerImpl impl) {
            inv.open(payload.power(), cfg, player, impl);
        }
    }

    private static void handleToggle(ServerPlayer player, PowerToggleC2S payload) {
        if (dev.overgrown.apoli.entity.GrabManager.keybindsDisabled(player.getUUID())) return;
        PowerContainer c = PowerContainer.of(player);
        if (c == null || !c.hasPower(payload.power()) || c.isSuppressed(payload.power())) return;
        if (dev.overgrown.apoli.power.builtin.PowerStoragePower.keyMuted(c, payload.power())) return;
        Power loaded = ApoliPowers.get(payload.power());
        if (loaded == null) return;
        if (!(PowerTypeRegistry.get(loaded.typeId()) instanceof TogglePower)) return;
        if (!(loaded.config() instanceof TogglePower.Config cfg)) return;
        if (loaded.condition().isPresent()
            && !loaded.condition().get().test(new EntityCtx(player, player.serverLevel()))) {
            return;
        }
        if (!TogglePower.toggleByKey(c, payload.power(), cfg)) return;
        sendEntitySync(player);
    }

    private static final class IdentifiedReloader implements IdentifiableResourceReloadListener {
        private final ResourceLocation id;
        private final PreparableReloadListener delegate;

        IdentifiedReloader(ResourceLocation id, PreparableReloadListener delegate) {
            this.id = id;
            this.delegate = delegate;
        }

        @Override public ResourceLocation getFabricId() { return id; }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager rm,
                                              ProfilerFiller prep, ProfilerFiller apply,
                                              Executor prepExec, Executor applyExec) {
            return delegate.reload(barrier, rm, prep, apply, prepExec, applyExec);
        }
    }
}
