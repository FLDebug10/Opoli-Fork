package dev.overgrown.apoli.effects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.overgrown.apoli.codec.ApoliOps;
import dev.overgrown.apoli.loader.IdWildcards;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class CustomEffectLoader {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final FileToIdConverter FILES = FileToIdConverter.json("effects");

    private CustomEffectLoader() {}

    public static List<CustomEffect> load(ResourceManager resourceManager) {
        List<CustomEffect> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, List<Resource>> entry : FILES.listMatchingResourceStacks(resourceManager).entrySet()) {
            ResourceLocation id = FILES.fileToId(entry.getKey());
            CustomEffect chosen = null;
            for (Resource resource : entry.getValue()) {
                CustomEffect effect = parse(id, resource);
                if (effect != null && (chosen == null || effect.loadingPriority() >= chosen.loadingPriority())) {
                    chosen = effect;
                }
            }
            if (chosen != null) loaded.add(chosen);
        }
        loaded.sort(Comparator.comparing(CustomEffect::id));
        if (!loaded.isEmpty()) {
            LOG.info("[Apoli] Loaded {} custom effect(s).", loaded.size());
        }
        return List.copyOf(loaded);
    }

    private static CustomEffect parse(ResourceLocation id, Resource resource) {
        JsonElement json;
        try (Reader reader = resource.openAsReader()) {
            json = GsonHelper.fromJson(GSON, reader, JsonElement.class);
        } catch (Exception e) {
            LOG.error("[Apoli] Failed to read custom effect {} from pack {}", id, resource.sourcePackId(), e);
            return null;
        }
        return CustomEffect.codec(id).parse(IdWildcards.apply(new Dynamic<>(ApoliOps.of(JsonOps.INSTANCE), json), id))
                .resultOrPartial(err -> LOG.error("[Apoli] Failed to parse custom effect {} from pack {}: {}", id, resource.sourcePackId(), err))
                .orElse(null);
    }
}
