package dev.overgrown.apoli.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.ApoliNetwork;
import dev.overgrown.apoli.alias.AliasDefault;
import dev.overgrown.apoli.alias.NamespaceAlias;
import dev.overgrown.apoli.condition.StaticCondition;
import dev.overgrown.apoli.macros.MacroLoader;
import dev.overgrown.apoli.macros.MacroRegistry;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.LegacyPowerShapes;
import dev.overgrown.apoli.power.PowerTypeRegistry;
import dev.overgrown.apoli.power.builtin.MultiplePower;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApoliReloadListener extends SimpleJsonResourceReloadListener {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final String DIR = "powers";
    private static final ResourceLocation MULTIPLE = Apoli.id("multiple");

    private MinecraftServer server;

    public ApoliReloadListener() {
        super(GSON, DIR);
    }

    public void attachServer(MinecraftServer server) {
        this.server = server;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager rm, ProfilerFiller profiler) {
        Map<ResourceLocation, Dynamic<JsonElement>> dynamicMap = new HashMap<>();

        for (var entry : data.entrySet()) {
            dynamicMap.put(entry.getKey(), new Dynamic<>(JsonOps.INSTANCE, entry.getValue()));
        }

        dynamicMap = MacroLoader.load(dynamicMap);
        data = MacroRegistry.applyAll(dynamicMap);

        Map<ResourceLocation, Dynamic<JsonElement>> expanded = new LinkedHashMap<>(dynamicMap.size());
        for (Map.Entry<ResourceLocation, JsonElement> e : data.entrySet()) {
            ResourceLocation id = e.getKey();
            try {
                Dynamic<JsonElement> power = new Dynamic<>(JsonOps.INSTANCE, e.getValue());
                if (!loadConditionPasses(power, id)) continue;
                expandMultiples(id, power, expanded);
            } catch (Exception ex) {
                LOG.error("[Apoli] Failed to expand power {}: {}", id, ex.getMessage());
            }
        }

        Map<ResourceLocation, Power> loaded = new HashMap<>(expanded.size());
        for (Map.Entry<ResourceLocation, Dynamic<JsonElement>> e : expanded.entrySet()) {
            ResourceLocation id = e.getKey();
            Dynamic<JsonElement> power = prepare(e.getValue(), id);
            dev.overgrown.apoli.codec.LoggedOptionalField.setContext(id);
            try {
                Power.CODEC.parse(dev.overgrown.apoli.codec.ApoliOps.attach(power))
                    .resultOrPartial(err -> LOG.error("Failed to parse power {}: {}", id, err))
                    .ifPresent(parsed -> loaded.put(id, parsed));
            } finally {
                dev.overgrown.apoli.codec.LoggedOptionalField.clearContext();
            }
        }
        ApoliPowers.replaceAll(loaded);
        dev.overgrown.apoli.data.MacroArguments.resetWarnings();
        LOG.info("[Apoli] Loaded {} power(s).", loaded.size());

        Map<ResourceLocation, dev.overgrown.apoli.skill.Skill> powerSkills = new HashMap<>();
        for (Map.Entry<ResourceLocation, Power> e : loaded.entrySet()) {
            e.getValue().toSkill(e.getKey()).ifPresent(skill -> powerSkills.put(e.getKey(), skill));
        }
        dev.overgrown.apoli.skill.SkillRegistry.setPowerSkills(powerSkills);

        if (server != null) {
            ApoliNetwork.broadcastPowers(server);
        }
    }

    public static <T> Dynamic<T> prepare(Dynamic<T> power, ResourceLocation id) {
        Dynamic<T> out = IdWildcards.apply(power, id);
        out = LegacyPowerShapes.apply(out);
        out = applyAliasFieldRenames(out);
        return applyAliasDefaults(out);
    }

    private static <T> void expandMultiples(ResourceLocation id, Dynamic<T> power,
                                            Map<ResourceLocation, Dynamic<T>> out) {
        Map<Dynamic<T>, Dynamic<T>> fields = power.getMapValues().result().orElse(null);
        if (fields == null || !isMultiple(power)) {
            out.put(id, power);
            return;
        }
        List<ResourceLocation> subIds = new ArrayList<>();
        Dynamic<T> superPower = power.emptyMap();
        for (Map.Entry<Dynamic<T>, Dynamic<T>> field : fields.entrySet()) {
            String key = field.getKey().asString().result().orElse(null);
            if (key == null) continue;
            Dynamic<T> value = field.getValue();
            if (MultiplePower.RESERVED_FIELDS.contains(key)) {
                superPower = superPower.set(key, value);
                continue;
            }
            if (value.getMapValues().result().isEmpty()) {
                LOG.warn("[Apoli] '{}' on {} is not a recognized field of apoli:multiple and is not an object, "
                    + "so it cannot be a sub-power either - it is being ignored.", key, id);
                continue;
            }
            ResourceLocation subId = subPowerId(id, key);
            if (subId == null) {
                LOG.error("[Apoli] Sub-power key '{}' on {} would produce an invalid identifier — skipping.", key, id);
                continue;
            }
            Dynamic<T> substituted = IdWildcards.apply(value, id);
            if (substituted.get("type").result().isEmpty()) {
                LOG.error("[Apoli] Sub-power '{}' of {} has no 'type' field — skipping. (If this was meant to be power data rather than a sub-power, it is not a recognized field of apoli:multiple.)", key, id);
                continue;
            }
            if (isMultiple(substituted)) {
                LOG.error("[Apoli] Nested apoli:multiple is not allowed (sub-power '{}' of {}) — skipping.", key, id);
                continue;
            }
            if (!loadConditionPasses(substituted, subId)) continue;
            if (out.containsKey(subId)) {
                LOG.warn("[Apoli] Sub-power id {} (from {}/{}) collides with an existing power — overwriting.", subId, id, key);
            }
            out.put(subId, substituted);
            subIds.add(subId);
        }
        superPower = superPower.set("type", power.createString(MULTIPLE.toString()));
        superPower = superPower.set("sub_powers",
            power.createList(subIds.stream().map(sub -> power.createString(sub.toString()))));
        out.put(id, superPower);
    }

    private static <T> Dynamic<T> applyAliasFieldRenames(Dynamic<T> power) {
        ResourceLocation aliasId = declaredType(power);
        if (aliasId == null) return power;
        Map<String, String> renames = PowerTypeRegistry.aliasFieldRenames(aliasId);
        if (renames.isEmpty() && NamespaceAlias.hasAlias(aliasId.getNamespace())) {
            renames = PowerTypeRegistry.aliasFieldRenames(NamespaceAlias.resolve(aliasId));
        }
        if (renames.isEmpty()) return power;
        Dynamic<T> out = power;
        for (Map.Entry<String, String> rename : renames.entrySet()) {
            Dynamic<T> value = out.get(rename.getKey()).result().orElse(null);
            if (value == null || out.get(rename.getValue()).result().isPresent()) continue;
            out = out.remove(rename.getKey()).set(rename.getValue(), value);
        }
        return out;
    }

    private static <T> Dynamic<T> applyAliasDefaults(Dynamic<T> power) {
        ResourceLocation aliasId = declaredType(power);
        if (aliasId == null) return power;
        List<AliasDefault<?>> defaults = PowerTypeRegistry.aliasDefaults(aliasId);
        if (defaults.isEmpty() && NamespaceAlias.hasAlias(aliasId.getNamespace())) {
            defaults = PowerTypeRegistry.aliasDefaults(NamespaceAlias.resolve(aliasId));
        }
        if (defaults.isEmpty()) return power;
        DynamicOps<T> ops = power.getOps();
        Dynamic<T> out = power;
        for (AliasDefault<?> def : defaults) {
            if (out.get(def.field()).result().isPresent()) continue;
            T encoded = def.encode(ops).resultOrPartial(err ->
                LOG.error("[Apoli] Could not build the default '{}' for {}: {}", def.field(), aliasId, err)).orElse(null);
            if (encoded != null) out = out.set(def.field(), new Dynamic<>(ops, encoded));
        }
        return out;
    }

    public static <T> boolean loadConditionPasses(Dynamic<T> power, ResourceLocation id) {
        Dynamic<T> declared = power.get("load_condition").result().orElse(null);
        if (declared == null) return true;
        StaticCondition condition = StaticCondition.CODEC.parse(declared)
            .resultOrPartial(err -> LOG.error("[Apoli] Ignoring the load_condition on {}: {}", id, err))
            .orElse(null);
        if (condition == null) return true;
        if (condition.test()) return true;
        LOG.debug("[Apoli] Skipping power {} — its load_condition does not hold on this installation.", id);
        return false;
    }

    private static <T> ResourceLocation declaredType(Dynamic<T> power) {
        String declared = power.get("type").asString().result().orElse(null);
        return declared == null ? null : ResourceLocation.tryParse(declared);
    }

    private static <T> boolean isMultiple(Dynamic<T> power) {
        ResourceLocation declared = declaredType(power);
        return declared != null && MULTIPLE.equals(PowerTypeRegistry.resolveId(declared));
    }

    private static ResourceLocation subPowerId(ResourceLocation superId, String key) {
        return ResourceLocation.tryParse(superId.getNamespace() + ":" + superId.getPath() + "_" + key);
    }
}
