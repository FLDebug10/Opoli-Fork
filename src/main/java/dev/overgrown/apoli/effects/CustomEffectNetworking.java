package dev.overgrown.apoli.effects;

import dev.overgrown.apoli.Apoli;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.effect.MobEffectCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CustomEffectNetworking {
    private static final StreamCodec<ByteBuf, MobEffectCategory> CATEGORY_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(MobEffectCategory::ordinal, MobEffectCategory.values(), ByIdMap.OutOfBoundsStrategy.ZERO),
            MobEffectCategory::ordinal);

    public record ClientEffectData(ResourceLocation id, MobEffectCategory category, int color,
                                   Optional<String> name, Optional<ResourceLocation> icon) {
        public static final StreamCodec<FriendlyByteBuf, ClientEffectData> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, ClientEffectData::id,
                CATEGORY_CODEC, ClientEffectData::category,
                ByteBufCodecs.INT, ClientEffectData::color,
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), ClientEffectData::name,
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), ClientEffectData::icon,
                ClientEffectData::new
        );

        static ClientEffectData of(CustomMobEffect effect) {
            return new ClientEffectData(effect.id, effect.getCategory(), effect.getColor(), effect.name, effect.icon);
        }

        public CustomMobEffect toEffect() {
            return new CustomMobEffect(id, category, color, List.of(), name, icon);
        }
    }

    public record SyncCustomEffectsPayload(List<ClientEffectData> effects) implements CustomPacketPayload {
        public static final Type<SyncCustomEffectsPayload> TYPE = new Type<>(Apoli.id("sync_custom_effects"));

        public static final StreamCodec<FriendlyByteBuf, SyncCustomEffectsPayload> CODEC = ClientEffectData.CODEC.apply(ByteBufCodecs.list()).map(SyncCustomEffectsPayload::new, SyncCustomEffectsPayload::effects);

        static SyncCustomEffectsPayload of(List<CustomMobEffect> effects) {
            List<ClientEffectData> data = new ArrayList<>(effects.size());
            for (CustomMobEffect effect : effects) {
                data.add(ClientEffectData.of(effect));
            }
            return new SyncCustomEffectsPayload(List.copyOf(data));
        }

        public List<CustomMobEffect> toEffects() {
            List<CustomMobEffect> out = new ArrayList<>(effects.size());
            for (ClientEffectData data : effects) {
                out.add(data.toEffect());
            }
            return out;
        }

        @Override public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SyncCustomEffectsResponsePayload(boolean success) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, SyncCustomEffectsResponsePayload> CODEC = ByteBufCodecs.BOOL.map(SyncCustomEffectsResponsePayload::new, SyncCustomEffectsResponsePayload::success);

        public static final Type<SyncCustomEffectsResponsePayload> TYPE = new Type<>(Apoli.id("sync_custom_effects_response"));
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void sync(@Nullable ServerPlayer player, @Nullable ServerConfigurationPacketListenerImpl config, boolean isSinglePlayer) {
        if (isSinglePlayer || !EffectConfig.get().enabled()) {
            return;
        }

        SyncCustomEffectsPayload payload = CustomEffectRegistry.payload();

        if (config != null) {
            ServerConfigurationNetworking.send(config, payload);
        } else if (player != null) {
            ServerPlayNetworking.send(player, payload);
            CustomEffectRegistry.awaitAck(player);
        }
    }
}
