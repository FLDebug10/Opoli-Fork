package dev.overgrown.apoli.client.render;

import dev.overgrown.apoli.Apoli;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public final class AnimatedTextures {
    private record Entry(ResourceLocation bound, @Nullable AnimatedTexture texture) {}

    private static final Map<ResourceLocation, Entry> BY_SOURCE = new HashMap<>();

    private AnimatedTextures() {}

    public static ResourceLocation bindable(ResourceLocation source) {
        Entry entry = BY_SOURCE.get(source);
        if (entry == null) {
            entry = detect(source);
            BY_SOURCE.put(source, entry);
        }
        if (entry.texture() != null) entry.texture().markUsed();
        return entry.bound();
    }

    public static void clearCache() {
        if (BY_SOURCE.isEmpty()) return;
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        for (Entry entry : BY_SOURCE.values()) {
            AnimatedTexture texture = entry.texture();
            if (texture != null && textures.getTexture(entry.bound(), null) == texture) textures.release(entry.bound());
        }
        BY_SOURCE.clear();
    }

    private static Entry detect(ResourceLocation source) {
        Minecraft minecraft = Minecraft.getInstance();
        Optional<Resource> resource = minecraft.getResourceManager().getResource(source);
        if (resource.isEmpty() || !animated(resource.get())) return new Entry(source, null);
        ResourceLocation key = Apoli.id("animated/" + source.getNamespace() + "/" + source.getPath());
        AnimatedTexture texture = new AnimatedTexture(source);
        TextureManager textures = minecraft.getTextureManager();
        textures.register(key, texture);
        if (textures.getTexture(key, null) != texture) return new Entry(source, null);
        return new Entry(key, texture);
    }

    private static boolean animated(Resource resource) {
        try {
            return resource.metadata().getSection(AnimationMetadataSection.SERIALIZER).isPresent();
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
