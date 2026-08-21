package dev.overgrown.apoli.action.builtin.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.compat.ModCompat;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.resources.ResourceLocation;
import dev.latvian.mods.rhino.Context;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;

import dev.architectury.event.events.common.*;
import dev.architectury.platform.Platform;
import org.slf4j.LoggerFactory;

public final class JavaScriptEntityAction implements ActionType<EntityCtx, JavaScriptEntityAction.Config> {

    @Override
    public MapCodec<Config> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("script").forGetter(Config::script)
        ).apply(i, Config::new));
    }

    @Override
    public void run(Config config, EntityCtx entityCtx) {
        ResourceLocation script_location = ResourceLocation.fromNamespaceAndPath(config.script.getNamespace(), "scripts/" + config.script.getPath());

        MinecraftServer server = entityCtx.entity().getServer();
        if (server == null) {
            return;
        }
        Reader script = null;
        try {
            script = server.getResourceManager().getResource(script_location).stream().toList().getFirst().openAsReader(); //TODO: Cache Script at Load-Time instead of reading it at Run-Time
        } catch (IOException e) {
            throw new RuntimeException(e); //TODO: Add softer Error Handling (currently crashes Minecraft)
        }

        ContextFactory factory = new ContextFactory();

        Context cx = factory.enter();

        ScriptableObject scope = cx.initSafeStandardObjects();

        Scriptable api = cx.newObject(scope);

        if(ModCompat.ARCHITECTURY_API) {
            api.put(cx, "Platform", api, Platform.class);
            api.put(cx, "EntityEvent", api, EntityEvent.class);
            api.put(cx, "TickEvent", api, TickEvent.class);
            api.put(cx, "PlayerEvent", api, PlayerEvent.class);
            api.put(cx, "LootEvent", api, LootEvent.class);
            cx.addToScope(scope, "api", api);
        }

        cx.addToScope(scope, "ctx", entityCtx);
        cx.addToScope(scope, "logger", LoggerFactory.getLogger("js"));

        try {
            cx.evaluateReader(scope, script, config.script.toString(), 1, null); //TODO: Add Security, This `null` is a placeholder!
        } catch (IOException e) {
            throw new RuntimeException(e); //TODO: Add softer Error Handling (currently crashes Minecraft)
        }
    }

    public record Config(
        ResourceLocation script
    ){}
}
