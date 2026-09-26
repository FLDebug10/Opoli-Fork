package dev.overgrown.apoli;

import com.mojang.logging.LogUtils;
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
import dev.overgrown.apoli.power.PowerType;
import dev.overgrown.apoli.power.PowerTypeRegistry;
import dev.overgrown.apoli.power.PowerTypes;
import dev.overgrown.apoli.power.builtin.ActionOnCallbackPower;
import dev.overgrown.apoli.power.builtin.ActionOnKeyPressPower;
import dev.overgrown.apoli.power.builtin.FireProjectilePower;
import dev.overgrown.apoli.power.builtin.InventoryPower;
import dev.overgrown.apoli.power.builtin.ActionOnUseHandler;
import dev.overgrown.apoli.power.builtin.EntitySetPower;
import dev.overgrown.apoli.power.builtin.HitActionHandler;
import dev.overgrown.apoli.power.builtin.ModifyDamageHandler;
import dev.overgrown.apoli.power.builtin.ModifyProjectileDamageHandler;
import dev.overgrown.apoli.power.builtin.TogglePower;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import dev.overgrown.apoli.power.builtin.EffectImmunityPower;
import dev.overgrown.apoli.power.PowerResources;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(Apoli.MOD_ID)
public final class Apoli {
    public static final String MOD_ID = "apoli";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private final ApoliReloadListener powerLoader = new ApoliReloadListener();
    private final ApoliKeybindLoader keybindLoader = new ApoliKeybindLoader();
    private final dev.overgrown.apoli.skill.SkillTreeLoader skillLoader = new dev.overgrown.apoli.skill.SkillTreeLoader();

    public Apoli(IEventBus modBus, ModContainer container) {
        LOGGER.info("[Apoli] Initializing...");

        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            dev.overgrown.apoli.client.config.ApoliConfigScreens.register(container);
        }

        ApoliAliases.bootstrap();
        ConditionTypes.bootstrap();
        ActionTypes.bootstrap();
        PowerTypes.bootstrap();
        dev.overgrown.apoli.scale.ScaleTypes.bootstrap();
        dev.overgrown.apoli.power.PowerSources.bootstrap();
        dev.overgrown.apoli.attribution.PowerCause.bootstrap();
        dev.overgrown.apoli.compat.accessory.AccessoryCompat.init();
        if (dev.overgrown.apoli.compat.ModCompat.HARDCORE_REVIVAL) {
            dev.overgrown.apoli.compat.hardcorerevival.HardcoreRevivalCompat.init();
        }

        PowerContainerAttachment.register(modBus);
        dev.overgrown.apoli.skill.SkillDataAttachment.register(modBus);
        dev.overgrown.apoli.advancement.ApoliCriteria.register(modBus);
        dev.overgrown.apoli.entity.ApoliEntities.register(modBus);
        dev.overgrown.apoli.particle.ApoliParticles.register(modBus);
        modBus.addListener(dev.overgrown.apoli.entity.ApoliEntities::registerAttributes);
        modBus.addListener(ApoliNetwork::register);
        modBus.addListener(dev.overgrown.apoli.item.ApoliLootFunctions::register);
        modBus.addListener(Apoli::onConfigTasks);

        NeoForge.EVENT_BUS.register(this);

        dev.overgrown.apoli.scale.Scales.setSyncer(entity ->
            ApoliNetwork.broadcastScale(entity, new dev.overgrown.apoli.network.payload.ScaleSyncS2C(
                entity.getId(), dev.overgrown.apoli.scale.ScaleSync.encode(entity))));
        dev.overgrown.apoli.entity.disguise.DisguiseManager.setBroadcaster((entity, data) ->
            ApoliNetwork.broadcastDisguise(entity, new dev.overgrown.apoli.network.payload.DisguiseUpdateS2C(entity.getId(), data)));
        dev.overgrown.apoli.entity.LabelManager.setBroadcaster((entity, texts) ->
            ApoliNetwork.broadcastLabel(entity, new dev.overgrown.apoli.network.payload.LabelUpdateS2C(entity.getId(), texts)));

        if (dev.overgrown.apoli.compat.ModCompat.PUFFISH_SKILLS) {
            dev.overgrown.apoli.compat.skills.SkillsCompat.init();
        }

        if (dev.overgrown.apoli.compat.ModCompat.YIGD) {
            dev.overgrown.apoli.compat.yigd.YigdCompat.init();
        }

        LOGGER.info("[Apoli] Ready. {} power type(s).", PowerTypeRegistry.view().size());
    }

    static void onConfigTasks(RegisterConfigurationTasksEvent event) {
        var listener = event.getListener();
        if (EffectConfig.get().enabled() && listener.hasChannel(CustomEffectNetworking.SyncCustomEffectsPayload.TYPE) && !listener.getConnection().isMemoryConnection()) {
            event.register(new CustomEffectNetworking.SyncCustomEffectConfigurationTask(CustomEffectRegistry.payload()));
        }
    }

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        dev.overgrown.apoli.codec.ApoliOps.setLoadingRegistries(event.getRegistryAccess());
        event.addListener(powerLoader);
        event.addListener(keybindLoader);
        event.addListener(skillLoader);
        event.addListener(new dev.overgrown.apoli.global.GlobalPowerLoader());
        event.addListener(new dev.overgrown.apoli.script.ScriptLoader());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ApoliPowerCommand.register(event.getDispatcher());
        ApoliResourceCommand.register(event.getDispatcher());
        dev.overgrown.apoli.command.ApoliSkillTreeCommand.register(event.getDispatcher());
        dev.overgrown.apoli.command.ApoliDisguiseCommand.register(event.getDispatcher());
        dev.overgrown.apoli.command.ApoliCloneCommand.register(event.getDispatcher());
        dev.overgrown.apoli.command.ApoliMountCommand.register(event.getDispatcher());
        dev.overgrown.apoli.command.ApoliKeyCommand.register(event.getDispatcher());
        dev.overgrown.apoli.command.ApoliDevModeCommand.register(event.getDispatcher());
        dev.overgrown.apoli.command.ApoliTickCommand.register(event.getDispatcher());
        dev.overgrown.apoli.command.ApoliScaleCommand.register(event.getDispatcher());
        if (dev.overgrown.apoli.compat.ModCompat.anyAccessory()) {
            dev.overgrown.apoli.compat.accessory.command.AccessoryCommand.register(event.getDispatcher());
        }
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("apoli:speech")
            .requires(source -> source.hasPermission(0))
            .then(net.minecraft.commands.Commands.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    String text = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "text");
                    String language = dev.overgrown.apoli.util.SpeechLanguage.of(player);
                    dev.overgrown.apoli.compat.voicechat.ActionOnSpeechPower.fire(player, text, language);
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("[Apoli] Simulated speech: \"" + text + "\""), false);
                    return 1;
                })));
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("apoli:message")
            .requires(source -> source.hasPermission(0))
            .then(net.minecraft.commands.Commands.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                .executes(ctx -> {
                    net.minecraft.world.entity.Entity holder = ctx.getSource().getEntityOrException();
                    String text = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "text");
                    dev.overgrown.apoli.compat.voicechat.ActionOnSendingMessagePower.Report report =
                        dev.overgrown.apoli.compat.voicechat.ActionOnSendingMessagePower.diagnose(
                            holder, text, net.minecraft.network.chat.ChatType.CHAT.location());
                    for (String line : report.lines()) {
                        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(line), false);
                    }
                    return report.matched() ? 1 : 0;
                })));
    }

    @SubscribeEvent
    public void onServerChat(net.neoforged.neoforge.event.ServerChatEvent event) {
        ServerPlayer sender = event.getPlayer();
        if (sender == null) {
            return;
        }
        dev.overgrown.apoli.compat.voicechat.MessagePowerHandler.handle(event);
    }

    @SubscribeEvent
    public void onPermissionGather(PermissionGatherEvent.Nodes event) {
        dev.overgrown.apoli.command.ApoliPermissions.gatherNodes(event);
    }

    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            ApoliNetwork.sendKeybinds(event.getPlayer(), SyncKeybindsS2C.fromCurrent());
            ApoliNetwork.sendPowers(event.getPlayer());
            sendEntitySync(event.getPlayer());
        } else {
            ApoliNetwork.broadcastPowers(event.getPlayerList().getServer());
            ApoliNetwork.broadcastKeybinds(event.getPlayerList().getServer(), SyncKeybindsS2C.fromCurrent());
            dev.overgrown.apoli.recipe.ApoliPowerRecipes.inject(event.getPlayerList().getServer());
            dev.overgrown.apoli.global.GlobalPowers.reapplyAll(event.getPlayerList().getServer());
            dev.overgrown.apoli.skill.SkillRegistry.reportOrphanedSkills();
            if (EffectConfig.get().enabled()) CustomEffectRegistry.finishReload(true);
            for (ServerPlayer player : event.getPlayerList().getPlayers()) {
                dev.overgrown.apoli.skill.SkillTrees.grantOnJoin(player);
                ApoliNetwork.sendSkillDefs(player);
                ApoliNetwork.sendSkillState(player);
                CustomEffectNetworking.sync(player);
            }
            CustomEffectRegistry.reloading = false;
        }
    }

    @SubscribeEvent
    public void onServerAboutToStart(net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) {
        dev.overgrown.apoli.codec.ApoliOps.setRegistries(event.getServer().registryAccess());
    }

    @SubscribeEvent
    public void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
            dev.overgrown.apoli.skill.SkillRegistry.reportOrphanedSkills();
        dev.overgrown.apoli.recipe.ApoliPowerRecipes.inject(event.getServer());
        dev.overgrown.apoli.entity.GrabManager.clearAll();
        dev.overgrown.apoli.block.GhostBlocks.clear();
        dev.overgrown.apoli.compat.voicechat.VoiceState.setServer(event.getServer());
        dev.overgrown.apoli.compat.voicechat.VoiceState.setCallbacks(
            dev.overgrown.apoli.compat.voicechat.VoicePowerHandler::onSpeakStart,
            dev.overgrown.apoli.compat.voicechat.VoicePowerHandler::onSpeakStop);
        if (EffectConfig.get().enabled()) CustomEffectRegistry.finishReload(true);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            dev.overgrown.apoli.entity.PlayerModelTypes.resolveFrom(sp.getUUID(), sp.getGameProfile());
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
        }
    }

    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        LivingEntity victim = event.getEntity();
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity le ? le : null;
        float original = event.getAmount();
        float afterProjectile = ModifyProjectileDamageHandler.modifyAmount(attacker, victim, event.getSource(),
            original, victim.level());
        float modified = ModifyDamageHandler.modifyAmount(attacker, victim, event.getSource(),
            afterProjectile, victim.level());
        if (original > 0 && modified <= 0) {
            event.setCanceled(true);
            return;
        }
        if (modified != original) event.setAmount(modified);
    }

    @SubscribeEvent
    public void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        LivingEntity victim = event.getEntity();
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity le ? le : null;
        HitActionHandler.fire(attacker, victim, event.getSource(), event.getNewDamage(), victim.level(), true);
    }

    @SubscribeEvent
    public void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (dev.overgrown.apoli.power.builtin.PreventDeathHandler.tryPrevent(event.getEntity(), event.getSource(), 0.0F)) {
            event.setCanceled(true);
            return;
        }
        dev.overgrown.apoli.power.builtin.DeathHandler.onDeath(event.getEntity(), event.getSource());
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onLivingDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        dev.overgrown.apoli.power.builtin.DeathHandler.dropPowerInventories(event.getEntity(), event.getDrops(), event.isCanceled());
    }

    @SubscribeEvent
    public void onEffectApplicable(MobEffectEvent.Applicable event) {
        LivingEntity victim = event.getEntity();
        if (EffectImmunityPower.isImmuneTo(victim, event.getEffectInstance())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        InteractionResult result = dev.overgrown.apoli.power.builtin.BlockUseHandler.handle(
            event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
        if (result != InteractionResult.PASS) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (dev.overgrown.apoli.power.builtin.PreventUseHandler.isPrevented(
                event.getEntity(), event.getTarget(), event.getHand())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (dev.overgrown.apoli.power.builtin.PreventUseHandler.isPrevented(
                event.getEntity(), event.getTarget(), event.getHand())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }
        if (event.getLevel().isClientSide()) return;
        InteractionResult result = ActionOnUseHandler.fire(event.getEntity(), event.getTarget(), event.getHand());
        if (result != InteractionResult.PASS) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity e = event.getEntity();
        ActionOnCallbackPower.fireLifecycle(e, ActionOnCallbackPower.Config::entityActionRemoved);
        PoweredEntities.unregister(e);
        dev.overgrown.apoli.rope.RopeManager.onEntityGone(e.getUUID());
        dev.overgrown.apoli.entity.LabelManager.onEntityGone(e.getUUID());
        Entity.RemovalReason reason = e.getRemovalReason();
        if (reason != null && reason.shouldDestroy()) {
            EntitySetPower.onEntityGone(e.getUUID());
            dev.overgrown.apoli.power.builtin.AttributePower.onEntityGone(e.getUUID());
        }
    }

    @SubscribeEvent
    public void onLivingConversion(LivingConversionEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        EntitySetPower.onEntityConverted(event.getEntity().getUUID(), event.getOutcome().getUUID());
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        dev.overgrown.apoli.global.GlobalPowers.applyTo(event.getEntity());
        PowerContainer c = PowerContainer.of(event.getEntity());
        if (c == null || c.isEmpty()) return;
        PoweredEntities.register(event.getEntity());
        for (net.minecraft.resources.ResourceLocation powerId : c.allPowers()) {
            dev.overgrown.apoli.power.builtin.ResourcePower.onEntityLoad(c, powerId);
        }
        ActionOnCallbackPower.fireLifecycle(event.getEntity(), ActionOnCallbackPower.Config::entityActionAdded);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            dev.overgrown.apoli.keybind.HeldKeys.clearServer(sp.getUUID());
            dev.overgrown.apoli.dev.DevMode.forget(sp.getUUID());
            dev.overgrown.apoli.compat.voicechat.ActionOnSpeechPower.forget(sp.getUUID());
            if (dev.overgrown.apoli.power.PowerTypeRegistry.get(dev.overgrown.apoli.power.ApoliIds.ACTION_ON_KEY_SEQUENCE)
                instanceof dev.overgrown.apoli.power.builtin.ActionOnKeySequencePower seq) {
                seq.forget(sp.getUUID());
            }
        }
        dev.overgrown.apoli.entity.GrabManager.release(event.getEntity().getUUID());
        dev.overgrown.apoli.entity.CameraPerspectives.remove(event.getEntity().getUUID());
        dev.overgrown.apoli.radial.RadialMenuManager.forget(event.getEntity().getUUID());
        dev.overgrown.apoli.power.builtin.ShaderPower.forget(event.getEntity().getUUID());
        dev.overgrown.apoli.entity.disguise.DisguiseManager.onPlayerLeave(event.getEntity().getUUID());
        dev.overgrown.apoli.entity.LabelManager.onEntityGone(event.getEntity().getUUID());
        dev.overgrown.apoli.action.builtin.entity.TextAction.onPlayerLeave(event.getEntity().getUUID());
        dev.overgrown.apoli.entity.PlayerModelTypes.remove(event.getEntity().getUUID());
        dev.overgrown.apoli.power.builtin.InventoryPower.onPlayerLeave(event.getEntity().getUUID());
        dev.overgrown.apoli.compat.voicechat.VoiceHearing.forget(event.getEntity().getUUID());
        dev.overgrown.apoli.compat.voicechat.VoiceState.forget(event.getEntity().getUUID());
        CustomEffectRegistry.waiting.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            dev.overgrown.apoli.power.builtin.ActionOverTimePower.resetEdges(sp);
            CustomEffectRegistry.dropOrphanedGrants(sp);
            resumePowers(sp);
            dev.overgrown.apoli.scale.Scales.syncIfScaled(sp);
            dev.overgrown.apoli.power.PowerLookup.forEach(sp,
                dev.overgrown.apoli.power.ApoliIds.STARTING_EQUIPMENT,
                dev.overgrown.apoli.power.builtin.StartingEquipmentPower.Config.class,
                cfg -> dev.overgrown.apoli.power.builtin.StartingEquipmentPower.onRespawn(sp, cfg));
            ActionOnCallbackPower.fireRespawn(sp);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            resumePowers(sp);
            dev.overgrown.apoli.scale.Scales.syncIfScaled(sp);
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        PoweredEntities.unregister(event.getOriginal());
        dev.overgrown.apoli.scale.Scales.transfer(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        dev.overgrown.apoli.block.GhostBlocks.restoreAll(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        dev.overgrown.apoli.codec.ApoliOps.setRegistries(null);
        dev.overgrown.apoli.codec.ApoliOps.setLoadingRegistries(null);
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
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer)) return;
        Entity target = event.getTarget();
        PowerContainer c = PowerContainer.of(target);
        if (c instanceof PowerContainerImpl impl && !impl.isEmpty()) {
            ApoliNetwork.sendEntityPowers(viewer, new SyncEntityPowersS2C(
                target.getId(), impl.snapshot(), impl.auxIntSnapshot(), impl.suppressedPowers()));
            dev.overgrown.apoli.network.payload.SyncResourceTablesS2C tables = tablePayload(target, impl);
            if (tables != null) ApoliNetwork.sendResourceTables(viewer, tables);
        }
        dev.overgrown.apoli.entity.disguise.DisguiseData disguise =
            dev.overgrown.apoli.entity.disguise.DisguiseManager.get(target.getUUID());
        if (disguise != null) {
            ApoliNetwork.sendDisguise(viewer, new dev.overgrown.apoli.network.payload.DisguiseUpdateS2C(
                target.getId(), java.util.Optional.of(disguise)));
        }
        java.util.Map<net.minecraft.resources.ResourceLocation, net.minecraft.network.chat.Component> labels =
            dev.overgrown.apoli.entity.LabelManager.textsFor(target.getUUID());
        if (!labels.isEmpty()) {
            ApoliNetwork.sendLabel(viewer, new dev.overgrown.apoli.network.payload.LabelUpdateS2C(
                target.getId(), labels));
        }
        dev.overgrown.apoli.scale.ScaleState scales = dev.overgrown.apoli.scale.Scales.stateOf(target);
        if (scales != null && !scales.isDefault()) {
            ApoliNetwork.sendScale(viewer, new dev.overgrown.apoli.network.payload.ScaleSyncS2C(
                target.getId(), dev.overgrown.apoli.scale.ScaleSync.encode(target)));
        }
        dev.overgrown.apoli.mount.MountOffsets.syncTo(viewer, target);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        DelayedActionQueue.tick();
        CustomEffectRegistry.tick(event.getServer());
        dev.overgrown.apoli.keybind.HeldKeys.tickForced();
        dev.overgrown.apoli.compat.voicechat.VoiceState.tick(event.getServer());
        dev.overgrown.apoli.compat.voicechat.VoiceHearing.tick(event.getServer());
        dev.overgrown.apoli.rope.RopeManager.tick(event.getServer());
        dev.overgrown.apoli.entity.GrabManager.tick(event.getServer());
        dev.overgrown.apoli.entity.ProjectileTickManager.tick(event.getServer());
        dev.overgrown.apoli.tick.TickRates.serverTick(event.getServer());
        boolean forcedKeys = dev.overgrown.apoli.keybind.HeldKeys.anyForced();
        PoweredEntities.forEach(entity -> {
            PowerContainer c = PowerContainer.of(entity);
            if (!(c instanceof PowerContainerImpl impl)) return;
            impl.tickActive();
            if (forcedKeys) dev.overgrown.apoli.keybind.KeyDispatch.tickForcedNonPlayer(entity);
            if (impl.isStructureDirty()) {
                ApoliNetwork.sendEntityPowersToTrackersAndSelf(entity, new SyncEntityPowersS2C(
                    entity.getId(), impl.snapshot(), impl.auxIntSnapshot(), impl.suppressedPowers()));
                dev.overgrown.apoli.network.payload.SyncResourceTablesS2C tables = tablePayload(entity, impl);
                if (tables != null) ApoliNetwork.sendResourceTablesToTrackersAndSelf(entity, tables);
                impl.clearStructureDirty();
                impl.clearDirty();
            } else if (impl.isDirty()) {
                ApoliNetwork.sendAuxIntsToTrackersAndSelf(entity,
                    new dev.overgrown.apoli.network.payload.SyncAuxIntsS2C(entity.getId(), impl.auxIntSnapshot()));
                dev.overgrown.apoli.network.payload.SyncResourceTablesS2C tables = tablePayload(entity, impl);
                if (tables != null) ApoliNetwork.sendResourceTablesToTrackersAndSelf(entity, tables);
                impl.clearDirty();
            }
            if (impl.isEmpty()) PoweredEntities.unregister(entity);
        });
        dev.overgrown.apoli.block.GhostBlocks.tick(event.getServer());
        dev.overgrown.apoli.power.builtin.ShaderPower.tick(event.getServer());
        EntitySetPower.flushPendingRemovals();
        EntitySetPower.flushSync(event.getServer());
    }

    public static void resumePowers(net.minecraft.world.entity.Entity entity) {
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
        ApoliNetwork.sendEntityPowersToTrackers(entity, new SyncEntityPowersS2C(
            entity.getId(), impl.snapshot(), impl.auxIntSnapshot(), impl.suppressedPowers()));
        dev.overgrown.apoli.network.payload.SyncResourceTablesS2C tables = tablePayload(entity, impl);
        if (tables != null) ApoliNetwork.sendResourceTablesToTrackersAndSelf(entity, tables);
    }

    private static dev.overgrown.apoli.network.payload.SyncResourceTablesS2C tablePayload(Entity entity,
                                                                                          PowerContainerImpl impl) {
        java.util.Map<net.minecraft.resources.ResourceLocation, int[]> tables = impl.auxIntsSnapshot();
        if (tables.isEmpty()) return null;
        return new dev.overgrown.apoli.network.payload.SyncResourceTablesS2C(entity.getId(), tables);
    }

    public static void sendEntitySync(ServerPlayer player) {
        PowerContainer c = PowerContainer.of(player);
        if (!(c instanceof PowerContainerImpl impl)) return;
        ApoliNetwork.sendEntityPowersToTrackersAndSelf(player, new SyncEntityPowersS2C(
            player.getId(), impl.snapshot(), impl.auxIntSnapshot(), impl.suppressedPowers()));
        dev.overgrown.apoli.network.payload.SyncResourceTablesS2C tables = tablePayload(player, impl);
        if (tables != null) ApoliNetwork.sendResourceTablesToTrackersAndSelf(player, tables);
        impl.clearDirty();
        impl.clearStructureDirty();
    }

    public static void handleActivation(ServerPlayer player, PowerActivationC2S payload) {
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

    public static void handleToggle(ServerPlayer player, PowerToggleC2S payload) {
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
}
