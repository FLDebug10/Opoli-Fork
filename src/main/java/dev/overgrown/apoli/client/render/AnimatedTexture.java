package dev.overgrown.apoli.client.render;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.SpriteTicker;
import net.minecraft.client.renderer.texture.Tickable;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@Environment(EnvType.CLIENT)
public final class AnimatedTexture extends AbstractTexture implements Tickable {
    private static final SpriteResourceLoader LOADER = SpriteResourceLoader.create(SpriteLoader.DEFAULT_METADATA_SECTIONS);
    private static final int IDLE_TICKS = 40;

    private final ResourceLocation source;
    @Nullable
    private SpriteContents contents;
    @Nullable
    private SpriteTicker ticker;
    private int idle;

    public AnimatedTexture(ResourceLocation source) {
        this.source = source;
    }

    @Override
    public void load(ResourceManager manager) throws IOException {
        SpriteContents loaded = LOADER.loadSprite(source, manager.getResourceOrThrow(source));
        if (loaded == null) throw new IOException("Could not read animated texture " + source);
        release();
        contents = loaded;
        ticker = loaded.createTicker();
        if (RenderSystem.isOnRenderThreadOrInit()) {
            upload();
        } else {
            RenderSystem.recordRenderCall(this::upload);
        }
    }

    private void upload() {
        SpriteContents current = contents;
        if (current == null) return;
        TextureUtil.prepareImage(getId(), current.width(), current.height());
        current.uploadFirstFrame(0, 0);
    }

    public void markUsed() {
        idle = 0;
    }

    @Override
    public void tick() {
        SpriteTicker current = ticker;
        if (current == null || idle++ > IDLE_TICKS) return;
        bind();
        current.tickAndUpload(0, 0);
    }

    @Override
    public void close() {
        release();
        super.close();
    }

    private void release() {
        if (ticker != null) {
            ticker.close();
            ticker = null;
        }
        if (contents != null) {
            contents.close();
            contents = null;
        }
    }
}
