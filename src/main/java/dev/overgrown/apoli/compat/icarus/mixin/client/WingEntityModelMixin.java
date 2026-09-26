package dev.overgrown.apoli.compat.icarus.mixin.client;

import dev.cammiescorner.icarus.client.models.WingEntityModel;
import dev.overgrown.apoli.client.render.ModelPartEdits;
import dev.overgrown.apoli.compat.icarus.IcarusWingParts;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WingEntityModel.class)
public abstract class WingEntityModelMixin {

    private static final String SETUP_ANIM = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V";

    @Unique
    private float[] apoli$rightOriginal;
    @Unique
    private float[] apoli$leftOriginal;
    @Unique
    private boolean apoli$edited;

    @Inject(method = SETUP_ANIM, at = @At("HEAD"), require = 0)
    private void apoli$restoreWings(LivingEntity entity, float limbAngle, float limbDistance, float age,
                                    float headYaw, float headPitch, CallbackInfo ci) {
        if (!apoli$edited) return;
        WingEntityModel<?> self = (WingEntityModel<?>) (Object) this;
        ModelPartEdits.restore(self.rightWing, apoli$rightOriginal);
        ModelPartEdits.restore(self.leftWing, apoli$leftOriginal);
        apoli$edited = false;
    }

    @Inject(method = SETUP_ANIM, at = @At("TAIL"), require = 0)
    private void apoli$editWings(LivingEntity entity, float limbAngle, float limbDistance, float age,
                                 float headYaw, float headPitch, CallbackInfo ci) {
        WingEntityModel<?> self = (WingEntityModel<?>) (Object) this;
        if (apoli$rightOriginal == null) {
            apoli$rightOriginal = ModelPartEdits.snapshot(self.rightWing);
            apoli$leftOriginal = ModelPartEdits.snapshot(self.leftWing);
        }
        if (IcarusWingParts.edit(entity, self.rightWing, self.leftWing, apoli$rightOriginal, apoli$leftOriginal)) {
            apoli$edited = true;
        }
    }
}
