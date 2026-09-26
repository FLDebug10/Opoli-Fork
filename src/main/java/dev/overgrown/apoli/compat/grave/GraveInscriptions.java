package dev.overgrown.apoli.compat.grave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.data.TextComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GraveInscriptions {
    public static final Codec<List<Component>> CODEC = TextComponent.CODEC.listOf();

    private static final Component SEPARATOR = Component.literal(" / ");
    private static final Map<ResourceLocation, Provider> REGISTERED = new LinkedHashMap<>();
    private static volatile Provider[] providers = new Provider[0];

    @FunctionalInterface
    public interface Provider {
        @Nullable Component inscribe(ServerPlayer player);
    }

    private GraveInscriptions() {}

    public static synchronized void register(ResourceLocation id, Provider provider) {
        REGISTERED.put(id, provider);
        providers = REGISTERED.values().toArray(new Provider[0]);
    }

    public static List<Component> collect(ServerPlayer player) {
        Provider[] snapshot = providers;
        if (snapshot.length == 0) return List.of();
        List<Component> lines = new ArrayList<>(snapshot.length);
        for (Provider provider : snapshot) {
            try {
                Component line = provider.inscribe(player);
                if (line != null) lines.add(line);
            } catch (RuntimeException e) {
                Apoli.LOGGER.error("[Apoli] A grave inscription failed for {}", player.getGameProfile().getName(), e);
            }
        }
        return List.copyOf(lines);
    }

    public static Component join(List<Component> lines) {
        if (lines.size() == 1) return lines.get(0);
        MutableComponent joined = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) joined.append(SEPARATOR);
            joined.append(lines.get(i));
        }
        return joined;
    }

    public static void write(CompoundTag tag, String key, List<Component> lines, DynamicOps<Tag> ops) {
        if (lines.isEmpty()) return;
        CODEC.encodeStart(ops, lines).result().ifPresent(encoded -> tag.put(key, encoded));
    }

    public static void write(CompoundTag tag, String key, List<Component> lines) {
        write(tag, key, lines, NbtOps.INSTANCE);
    }

    public static List<Component> read(CompoundTag tag, String key, DynamicOps<Tag> ops) {
        Tag encoded = tag.get(key);
        if (encoded == null) return List.of();
        return CODEC.parse(ops, encoded).result().map(List::copyOf).orElse(List.of());
    }

    public static List<Component> read(CompoundTag tag, String key) {
        return read(tag, key, NbtOps.INSTANCE);
    }
}
