package dev.overgrown.apoli.macros;

import com.google.gson.JsonElement;
import com.mojang.serialization.Dynamic;
import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.power.PowerTypeRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MacroLoader {
    public static Map<ResourceLocation, Dynamic<JsonElement>> load(Map<ResourceLocation, Dynamic<JsonElement>> input) {
        var data = new HashMap<>(input);

        MacroRegistry.clear();
        Set<ResourceLocation> toRemove = new HashSet<>();

        for (var elem : data.entrySet()) {
            Set<String> toRemoveSub = new HashSet<>();

            ResourceLocation declared = ResourceLocation.tryParse(elem.getValue().get("type").asString().result().orElse(""));
            var type = declared == null ? null : PowerTypeRegistry.resolveId(declared);

            if (type == null) continue;

            if(type.equals(Apoli.id("multiple"))) {
                for (var sub : elem.getValue().getMapValues().result().orElse(Map.of()).entrySet()) {
                    ResourceLocation declaredSub = ResourceLocation.tryParse(sub.getValue().get("type").asString().result().orElse(""));
                    var typeSub = declaredSub == null ? null : PowerTypeRegistry.resolveId(declaredSub);

                    ResourceLocation key = ResourceLocation.tryParse(elem.getKey() + "_" + sub.getKey().asString().result().orElse(""));
                    if (key == null || typeSub == null) continue;

                    if (typeSub.equals(Apoli.id("macro"))) {
                        if (sub.getValue().get("macro").asString().result().orElse(null) != null) continue;

                        toRemoveSub.add(sub.getKey().asString().result().orElse(null));

                        Macro.CODEC.parse(sub.getValue()).resultOrPartial((err) -> Apoli.LOGGER.error("[Apoli] Failed to parse macro {}: {}", key, err)).ifPresent((macro) -> {
                            MacroRegistry.macros.put(key, macro);
                            MacroRegistry.putSuperBySub(key, elem.getKey());
                        });
                    }
                }
            }

            if (type.equals(Apoli.id("macro"))) {
                if (elem.getValue().get("macro").asString().result().orElse(null) != null) continue;

                toRemove.add(elem.getKey());

                Macro.CODEC.parse(elem.getValue()).resultOrPartial((err) -> Apoli.LOGGER.error("[Apoli] Failed to parse macro {}: {}", elem.getKey(), err)).ifPresent((macro) -> MacroRegistry.macros.put(elem.getKey(), macro));
            }

            Dynamic<JsonElement> current = elem.getValue();
            for (var sub : toRemoveSub) {
                if (sub == null) continue;

                current = current.remove(sub);
            }
            data.replace(elem.getKey(), current);
        }

        for (var id : toRemove) {
            data.remove(id);
        }

        return data;
    }
}
