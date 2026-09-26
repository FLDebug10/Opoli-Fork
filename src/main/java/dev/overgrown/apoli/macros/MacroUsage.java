package dev.overgrown.apoli.macros;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public record MacroUsage (
    ResourceLocation macro,
    Map<String, Dynamic<?>> args
) {
    public static final Codec<MacroUsage> CODEC = RecordCodecBuilder.create(i -> i.group(
        ResourceLocation.CODEC.fieldOf("macro").forGetter(MacroUsage::macro),
        Codec.unboundedMap(Codec.STRING, Codec.PASSTHROUGH).optionalFieldOf("arguments", Map.of()).forGetter(MacroUsage::args)
    ).apply(i, MacroUsage::new));
}
