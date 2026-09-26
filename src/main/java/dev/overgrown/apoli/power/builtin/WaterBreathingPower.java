package dev.overgrown.apoli.power.builtin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.mixin.flag.LivingEntityAirAccessor;
import dev.overgrown.apoli.power.ApoliIds;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class WaterBreathingPower extends PowerType<WaterBreathingPower.Config> {
    public record Config(boolean suffocateOutsideWater) {}

    private static final int DROWN_AT = -20;
    private static final float DROWN_DAMAGE = 2.0F;

    @Override
    public MapCodec<Config> configCodec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("suffocate_outside_water", false).forGetter(Config::suffocateOutsideWater)
        ).apply(i, Config::new));
    }

    public static boolean canBreatheUnderwater(LivingEntity entity) {
        PowerContainer container = PowerContainer.of(entity);
        if (container == null || container.isEmpty()) return false;
        if (container.powersOfType(ApoliIds.WATER_BREATHING).isEmpty()) return false;
        return PowerLookup.hasActive(entity, ApoliIds.WATER_BREATHING);
    }

    public static boolean suffocatesOutsideWater(LivingEntity entity) {
        return PowerLookup.anyActive(entity, ApoliIds.WATER_BREATHING, Config.class, Config::suffocateOutsideWater);
    }

    public static void tick(LivingEntity entity) {
        if (!entity.isAlive() || !suffocatesOutsideWater(entity)) return;

        LivingEntityAirAccessor air = (LivingEntityAirAccessor) entity;

        if (!shouldSuffocate(entity)) {
            int current = entity.getAirSupply();
            if (current < entity.getMaxAirSupply()) entity.setAirSupply(air.apoli$increaseAirSupply(current));
            return;
        }

        int refunded = air.apoli$increaseAirSupply(0);
        if (isInRain(entity)) {
            entity.setAirSupply(entity.getAirSupply() - refunded);
            return;
        }

        entity.setAirSupply(air.apoli$decreaseAirSupply(entity.getAirSupply()) - refunded);
        if (entity.getAirSupply() > DROWN_AT) return;

        entity.setAirSupply(0);
        if (entity.level().isClientSide()) {
            Vec3 motion = entity.getDeltaMovement();
            for (int i = 0; i < 8; i++) {
                double dx = entity.getRandom().nextDouble() - entity.getRandom().nextDouble();
                double dy = entity.getRandom().nextDouble() - entity.getRandom().nextDouble();
                double dz = entity.getRandom().nextDouble() - entity.getRandom().nextDouble();
                entity.level().addParticle(ParticleTypes.BUBBLE,
                    entity.getX() + dx, entity.getY() + dy, entity.getZ() + dz, motion.x, motion.y, motion.z);
            }
        } else {
            entity.hurt(entity.damageSources().drown(), DROWN_DAMAGE);
        }
    }

    private static boolean shouldSuffocate(LivingEntity entity) {
        return !entity.isEyeInFluid(FluidTags.WATER)
            && !entity.hasEffect(MobEffects.WATER_BREATHING)
            && !entity.hasEffect(MobEffects.CONDUIT_POWER);
    }

    private static boolean isInRain(LivingEntity entity) {
        BlockPos pos = entity.blockPosition();
        return entity.level().isRainingAt(pos)
            || entity.level().isRainingAt(BlockPos.containing(pos.getX(), entity.getBoundingBox().maxY, pos.getZ()));
    }
}
