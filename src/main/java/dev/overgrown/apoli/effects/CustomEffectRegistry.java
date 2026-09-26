package dev.overgrown.apoli.effects;

import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.PowerContainer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CustomEffectRegistry {
    private static final int SYNC_TIMEOUT_TICKS = 100;

    public static volatile boolean reloading = false;
    public static final Set<UUID> waiting = new HashSet<>();
    public static int timeout;

    private static List<CustomEffect> definitions = List.of();
    private static List<CustomEffect> previousDefinitions = List.of();
    private static List<CustomMobEffect> active = List.of();
    private static Set<ResourceLocation> ids = Set.of();
    private static CustomEffectNetworking.SyncCustomEffectsPayload payload =
            new CustomEffectNetworking.SyncCustomEffectsPayload(List.of());

    private CustomEffectRegistry() {}

    public static List<CustomMobEffect> active() {
        return active;
    }

    public static Set<ResourceLocation> ids() {
        return ids;
    }

    public static CustomEffectNetworking.SyncCustomEffectsPayload payload() {
        return payload;
    }

    public static boolean blocksCustomEffects() {
        return reloading || !waiting.isEmpty();
    }

    public static void stage(ResourceManager resourceManager) {
        previousDefinitions = definitions;
        definitions = CustomEffectLoader.load(resourceManager);
        installDefinitions(definitions);
    }

    public static void finishReload(boolean success) {
        if (!success && previousDefinitions != definitions) {
            definitions = previousDefinitions;
            installDefinitions(definitions);
        }
        previousDefinitions = definitions;
        validate();
        reloading = false;
    }

    public static void validate() {
        for (CustomEffect definition : definitions) {
            for (ResourceLocation power : definition.powers()) {
                if (ApoliPowers.get(power) == null) {
                    Apoli.LOGGER.warn("[Apoli] Custom effect {} lists unknown power {}; it will not be granted.", definition.id(), power);
                }
            }
        }
    }

    private static void installDefinitions(List<CustomEffect> source) {
        List<CustomMobEffect> effects = new ArrayList<>(source.size());
        for (CustomEffect definition : source) {
            effects.add(new CustomMobEffect(definition.id(), definition.mobEffectCategory(), definition.colorInt(),
                    definition.powers(), definition.name(), definition.icon()));
        }
        install(effects);
        payload = CustomEffectNetworking.SyncCustomEffectsPayload.of(active);
    }

    public static void install(List<CustomMobEffect> effects) {
        RuntimeMobEffectRegistry registry = (RuntimeMobEffectRegistry) BuiltInRegistries.MOB_EFFECT;
        registry.apoli$unregisterCustom();
        List<CustomMobEffect> installed = new ArrayList<>(effects.size());
        Set<ResourceLocation> installedIds = new HashSet<>();
        for (CustomMobEffect effect : effects) {
            if (BuiltInRegistries.MOB_EFFECT.containsKey(effect.id) || !installedIds.add(effect.id)) {
                Apoli.LOGGER.error("[Apoli] Skipped custom effect {}: a status effect with that id already exists.", effect.id);
                continue;
            }
            registry.apoli$register(effect);
            installed.add(effect);
        }
        active = List.copyOf(installed);
        ids = Set.copyOf(installedIds);
    }

    public static void purge(MinecraftServer server) {
        if (active.isEmpty()) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living) purge(living);
            }
        }
    }

    private static void purge(LivingEntity living) {
        Map<Holder<MobEffect>, MobEffectInstance> effects = living.getActiveEffectsMap();
        if (effects.isEmpty()) return;
        List<Holder<MobEffect>> custom = null;
        for (Holder<MobEffect> holder : effects.keySet()) {
            if (holder.value() instanceof CustomMobEffect) {
                if (custom == null) custom = new ArrayList<>(2);
                custom.add(holder);
            }
        }
        if (custom == null) return;
        for (int i = 0; i < custom.size(); i++) {
            living.removeEffect(custom.get(i));
        }
    }

    public static void dropOrphanedGrants(LivingEntity entity) {
        List<CustomMobEffect> effects = active;
        if (effects.isEmpty()) return;
        PowerContainer container = PowerContainer.of(entity);
        if (container == null || container.isEmpty()) return;
        Set<ResourceLocation> sources = container.allSources();
        for (int i = 0; i < effects.size(); i++) {
            CustomMobEffect effect = effects.get(i);
            if (!sources.contains(effect.id)) continue;
            if (!entity.hasEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect))) {
                container.removeAllFromSource(effect.id);
            }
        }
    }

    public static void awaitAck(ServerPlayer player) {
        waiting.add(player.getUUID());
        timeout = player.server.getTickCount();
    }

    public static void acknowledge(ServerPlayer player, boolean success) {
        waiting.remove(player.getUUID());
        if (!success) {
            Apoli.LOGGER.warn("[Apoli] {} could not apply the server's custom effects.", player.getGameProfile().getName());
        }
    }

    public static void tick(MinecraftServer server) {
        if (waiting.isEmpty() || server.getTickCount() <= timeout + SYNC_TIMEOUT_TICKS) return;
        List<String> names = new ArrayList<>(waiting.size());
        for (UUID uuid : waiting) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            names.add(player != null ? player.getGameProfile().getName() : uuid.toString());
        }
        Apoli.LOGGER.warn("[Apoli] Custom effect sync timed out for: {}", String.join(", ", names));
        waiting.clear();
    }
}
