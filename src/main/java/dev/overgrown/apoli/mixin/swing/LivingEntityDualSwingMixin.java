package dev.overgrown.apoli.mixin.swing;

import dev.overgrown.apoli.access.DualSwingHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDualSwingMixin implements DualSwingHolder {

    private static final String SWING = "swing(Lnet/minecraft/world/InteractionHand;Z)V";

    @Shadow public boolean swinging;
    @Shadow public InteractionHand swingingArm;
    @Shadow public int swingTime;

    @Unique private boolean apoli$bothArms;
    @Unique private boolean apoli$wasSwinging;
    @Unique private int apoli$previousSwingTime;
    @Unique private InteractionHand apoli$previousArm;

    @Override
    public boolean apoli$isSwingingBothArms() {
        return this.apoli$bothArms;
    }

    @Override
    public void apoli$setSwingingBothArms(boolean value) {
        this.apoli$bothArms = value;
    }

    @Inject(method = SWING, at = @At("HEAD"))
    private void apoli$captureSwing(InteractionHand hand, boolean broadcastToSelf, CallbackInfo ci) {
        this.apoli$wasSwinging = this.swinging;
        this.apoli$previousSwingTime = this.swingTime;
        this.apoli$previousArm = this.swingingArm;
    }

    @Inject(method = SWING, at = @At("RETURN"))
    private void apoli$pairSwing(InteractionHand hand, boolean broadcastToSelf, CallbackInfo ci) {
        InteractionHand previous = this.apoli$previousArm;
        this.apoli$previousArm = null;
        if (this.swingTime != -1 || this.swingingArm != hand) return;
        if (!this.apoli$wasSwinging || this.apoli$previousSwingTime >= 0 || previous == null) {
            this.apoli$bothArms = false;
        } else if (previous != hand) {
            this.apoli$bothArms = true;
        }
    }

    @Inject(method = "updateSwingTime", at = @At("TAIL"))
    private void apoli$clearBothArms(CallbackInfo ci) {
        if (!this.swinging) this.apoli$bothArms = false;
    }
}
