package dev.overgrown.apoli.effects;

import dev.overgrown.apoli.PowerContainerAttachment;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.PowerContainer;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public final class CustomMobEffect extends MobEffect {
    public final ResourceLocation id;
    public final Optional<String> name;
    public final Optional<ResourceLocation> icon;
    private final List<ResourceLocation> powers;
    private final String descriptionId;

    CustomMobEffect(ResourceLocation id, MobEffectCategory category, int color, List<ResourceLocation> powers,
                    Optional<String> name, Optional<ResourceLocation> icon) {
        super(category, color);
        this.id = id;
        this.powers = List.copyOf(powers);
        this.name = name;
        this.icon = icon;
        this.descriptionId = name.orElseGet(() -> Util.makeDescriptionId("effect", id));
    }

    public List<ResourceLocation> powers() {
        return powers;
    }

    public void grantPowers(LivingEntity entity) {
        if (powers.isEmpty() || entity.level().isClientSide()) return;
        PowerContainer holder = null;
        for (int i = 0; i < powers.size(); i++) {
            ResourceLocation power = powers.get(i);
            if (ApoliPowers.get(power) == null) continue;
            if (holder == null) {
                holder = PowerContainerAttachment.getOrCreate(entity);
                if (holder == null) return;
            }
            holder.addPower(power, id);
        }
    }

    @Override
    public @NotNull String getDescriptionId() {
        return descriptionId;
    }
}
