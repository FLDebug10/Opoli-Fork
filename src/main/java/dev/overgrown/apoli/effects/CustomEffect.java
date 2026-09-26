package dev.overgrown.apoli.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.data.ColorCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record CustomEffect(
        ResourceLocation id,
        List<ResourceLocation> powers,
        Vec3 color,
        Optional<ResourceLocation> icon,
        int loadingPriority,
        MobEffectCategory mobEffectCategory,
        Optional<String> name
    ) {

    private static final Codec<MobEffectCategory> CATEGORY_CODEC = Codec.STRING.comapFlatMap(
            name -> switch (name) {
                case "beneficial", "BENEFICIAL" -> DataResult.success(MobEffectCategory.BENEFICIAL);
                case "harmful", "HARMFUL" -> DataResult.success(MobEffectCategory.HARMFUL);
                case "neutral", "NEUTRAL" -> DataResult.success(MobEffectCategory.NEUTRAL);
                default -> DataResult.error(() -> "Unknown effect type '" + name
                        + "' (expected beneficial, harmful or neutral, upper case accepted)");
            },
            category -> category.name().toLowerCase(Locale.ROOT)
    );

    public static Codec<CustomEffect> codec(ResourceLocation id) {
        return RecordCodecBuilder.create(instance -> instance.group(
                IdCodecs.ID.listOf().fieldOf("powers").forGetter(CustomEffect::powers),
                Codec.DOUBLE.optionalFieldOf("r", 0d).forGetter(effect -> effect.color.x),
                Codec.DOUBLE.optionalFieldOf("g", 0d).forGetter(effect -> effect.color.y),
                Codec.DOUBLE.optionalFieldOf("b", 0d).forGetter(effect -> effect.color.z),
                IdCodecs.ID.optionalFieldOf("icon").forGetter(CustomEffect::icon),
                Codec.INT.optionalFieldOf("loading_priority", 0).forGetter(CustomEffect::loadingPriority),
                CATEGORY_CODEC.fieldOf("type").forGetter(CustomEffect::mobEffectCategory),
                Codec.STRING.optionalFieldOf("name").forGetter(CustomEffect::name)
        ).apply(instance, (powers, r, g, b, icon, priority, category, name) ->
                new CustomEffect(id, powers, new Vec3(r, g, b), icon, priority, category, name))
        );
    }

    public int colorInt() {
        return ColorCodecs.pack((float) color.x, (float) color.y, (float) color.z, 0.0F);
    }
}
