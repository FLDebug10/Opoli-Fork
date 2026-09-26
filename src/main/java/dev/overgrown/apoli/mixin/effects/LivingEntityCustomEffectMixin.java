package dev.overgrown.apoli.mixin.effects;

import dev.overgrown.apoli.effects.CustomEffectRegistry;
import dev.overgrown.apoli.effects.CustomMobEffect;
import dev.overgrown.apoli.power.PowerContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityCustomEffectMixin {
    @Inject(method = "onEffectRemoved", at = @At(value = "HEAD"))
    private void apoli$onRemoveEffect(MobEffectInstance mobEffectInstance, CallbackInfo ci) {
        if (!(mobEffectInstance.getEffect() instanceof CustomMobEffect effect)) return;
        PowerContainer holder = PowerContainer.of((LivingEntity) (Object) this);
        if (holder != null) holder.removeAllFromSource(effect.id);
    }

    @Inject(method = "onEffectAdded", at = @At(value = "HEAD"))
    private void apoli$onAddedEffect(MobEffectInstance mobEffectInstance, @Nullable Entity entity, CallbackInfo ci) {
        if (mobEffectInstance.getEffect() instanceof CustomMobEffect effect) {
            effect.grantPowers((LivingEntity) (Object) this);
        }
    }

    @Inject(method = "canBeAffected", at = @At(value = "HEAD"), cancellable = true)
    private void apoli$preventEffectsDuringReload(MobEffectInstance mobEffectInstance, CallbackInfoReturnable<Boolean> cir) {
        if (CustomEffectRegistry.blocksCustomEffects()
                && mobEffectInstance.getEffect() instanceof CustomMobEffect
                && !((LivingEntity) (Object) this).level().isClientSide()) {
            cir.setReturnValue(false);
        }
    }
}
