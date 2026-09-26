package dev.overgrown.apoli.effects;

import dev.overgrown.apoli.Apoli;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CustomEffectNetworking {
    private static final Set<UUID> PENDING_JOIN = new HashSet<>();

    public record ClientEffectData(ResourceLocation id, MobEffectCategory category, int color,
                                   Optional<String> name, Optional<ResourceLocation> icon) {
        public ClientEffectData(FriendlyByteBuf buf) {
            this(buf.readResourceLocation(),
                    buf.readEnum(MobEffectCategory.class),
                    buf.readInt(),
                    buf.readOptional(FriendlyByteBuf::readUtf),
                    buf.readOptional(FriendlyByteBuf::readResourceLocation));
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(id);
            buf.writeEnum(category);
            buf.writeInt(color);
            buf.writeOptional(name, FriendlyByteBuf::writeUtf);
            buf.writeOptional(icon, FriendlyByteBuf::writeResourceLocation);
        }

        static ClientEffectData of(CustomMobEffect effect) {
            return new ClientEffectData(effect.id, effect.getCategory(), effect.getColor(), effect.name, effect.icon);
        }

        public CustomMobEffect toEffect() {
            return new CustomMobEffect(id, category, color, List.of(), name, icon);
        }
    }

    public record SyncCustomEffectsPacket(List<ClientEffectData> effects) implements FabricPacket {
        public static final ResourceLocation CHANNEL = Apoli.id("sync_custom_effects");
        public static final PacketType<SyncCustomEffectsPacket> TYPE = PacketType.create(CHANNEL, SyncCustomEffectsPacket::new);

        public SyncCustomEffectsPacket(FriendlyByteBuf buf) {
            this(buf.readList(ClientEffectData::new));
        }

        static SyncCustomEffectsPacket of(List<CustomMobEffect> effects) {
            List<ClientEffectData> data = new ArrayList<>(effects.size());
            for (CustomMobEffect effect : effects) {
                data.add(ClientEffectData.of(effect));
            }
            return new SyncCustomEffectsPacket(List.copyOf(data));
        }

        public List<CustomMobEffect> toEffects() {
            List<CustomMobEffect> out = new ArrayList<>(effects.size());
            for (ClientEffectData data : effects) {
                out.add(data.toEffect());
            }
            return out;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeCollection(effects, (b, e) -> e.write(b));
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }
    }

    public record CustomEffectResponsePacket(boolean success) implements FabricPacket {
        public static final ResourceLocation CHANNEL = Apoli.id("custom_effect_response");
        public static final PacketType<CustomEffectResponsePacket> TYPE = PacketType.create(CHANNEL, CustomEffectResponsePacket::new);

        public CustomEffectResponsePacket(FriendlyByteBuf buf) {
            this(buf.readBoolean());
        }
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(success);
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }
    }

    public static void sync(ServerPlayer player, boolean isSinglePlayer) {
        if (send(player, isSinglePlayer)) CustomEffectRegistry.awaitAck(player);
    }

    public static void syncOnJoin(ServerPlayer player, boolean isSinglePlayer) {
        if (ServerPlayNetworking.canSend(player, SyncCustomEffectsPacket.CHANNEL)) {
            send(player, isSinglePlayer);
        } else {
            PENDING_JOIN.add(player.getUUID());
        }
    }

    public static void syncIfPending(ServerPlayer player, boolean isSinglePlayer) {
        if (!PENDING_JOIN.remove(player.getUUID())) return;
        if (ServerPlayNetworking.canSend(player, SyncCustomEffectsPacket.CHANNEL)) {
            send(player, isSinglePlayer);
        }
    }

    private static boolean send(ServerPlayer player, boolean isSinglePlayer) {
        if (isSinglePlayer || !EffectConfig.get().enabled()) return false;
        ServerPlayNetworking.send(player, CustomEffectRegistry.payload());
        return true;
    }

    public static void forget(UUID player) {
        PENDING_JOIN.remove(player);
    }
}
