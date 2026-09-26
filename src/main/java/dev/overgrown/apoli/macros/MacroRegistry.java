package dev.overgrown.apoli.macros;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.loader.IdWildcards;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

public final class MacroRegistry {
    public static Map<ResourceLocation, Macro> macros = new HashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> superBySub = new HashMap<>();

    private static final ResourceLocation macroAltId = new ResourceLocation("origins", "macro");

    public static @Nullable JsonElement applyMacro(ResourceLocation macroId, Map<String, Dynamic<?>> args, ResourceLocation caller) {
        if (!macros.containsKey(macroId)) {
            Apoli.LOGGER.error("[Apoli] Macro {} is not defined, called from {}.", macroId, caller);

            return null;
        }

        return macros.get(macroId).get(args, caller);
    }

    private static Dynamic<JsonElement> resolve(Dynamic<JsonElement> elem, ResourceLocation keyCtx, Set<ResourceLocation> seen) {
        var ops = elem.getOps();

        String declared = elem.get("type").asString().result().orElse(null);
        var type = declared == null ? null : ResourceLocation.tryParse(declared);

        if (type == null) {
            Optional<Stream<Pair<JsonElement, JsonElement>>> entries = ops.getMapValues(elem.getValue()).result();
            if (entries.isPresent()) {
                return new Dynamic<>(ops, ops.createMap(entries.get().map(entry -> Pair.of(entry.getFirst(), resolve(new Dynamic<>(ops, entry.getSecond()), keyCtx, seen).getValue()))));
            }
            Optional<Stream<JsonElement>> values = ops.getStream(elem.getValue()).result();
            return values.map(jsonElementStream -> new Dynamic<>(ops, ops.createList(jsonElementStream.map(value -> resolve(new Dynamic<>(ops, value), keyCtx, seen).getValue())))).orElse(elem);
        }

        if (type.equals(Apoli.id("macro")) || type.equals(macroAltId)) {
            MacroUsage usage = MacroUsage.CODEC.parse(IdWildcards.apply(elem, keyCtx)).resultOrPartial((str) -> Apoli.LOGGER.error("[Apoli] failed to parse macro usage {}: {}", keyCtx, str)).orElse(null);

            if (usage == null) {
                return elem;
            }

            if (!seen.add(usage.macro())) {
                Apoli.LOGGER.error("[Apoli] detected cycle in macros: {}", seen);
                return elem;
            }

            var macroJson = applyMacro(usage.macro(), usage.args(), keyCtx);

            var res = macroJson == null ? elem : resolve(new Dynamic<>(ops, macroJson), superBySub.getOrDefault(usage.macro(), keyCtx), seen);

            seen.remove(usage.macro());

            return res;
        }

        else {
            Optional<Stream<Pair<JsonElement, JsonElement>>> entries = ops.getMapValues(elem.getValue()).result();
            if (entries.isPresent()) {
                return new Dynamic<>(ops, ops.createMap(entries.get().map(entry -> Pair.of(entry.getFirst(), resolve(new Dynamic<>(ops, entry.getSecond()), keyCtx, seen).getValue()))));
            }
            Optional<Stream<JsonElement>> values = ops.getStream(elem.getValue()).result();
            return values.map(jsonElementStream -> new Dynamic<>(ops, ops.createList(jsonElementStream.map(value -> resolve(new Dynamic<>(ops, value), keyCtx, seen).getValue())))).orElse(elem);
        }
    }

    public static Map<ResourceLocation, JsonElement> applyAll(Map<ResourceLocation, Dynamic<JsonElement>> input) {
        Map<ResourceLocation, JsonElement> data = new HashMap<>();

        for (var elem : input.entrySet()) {
            data.put(elem.getKey(), resolve(elem.getValue(), elem.getKey(), new HashSet<>()).getValue());
        }

        return data;
    }

    public static void putSuperBySub(ResourceLocation sub, ResourceLocation superP) {
        superBySub.put(sub, superP);
    }

    public static void clear() {
        superBySub.clear();
        macros.clear();
    }
}
