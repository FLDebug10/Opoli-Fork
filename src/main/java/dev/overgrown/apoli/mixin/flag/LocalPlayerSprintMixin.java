package dev.overgrown.apoli.mixin.flag;

import dev.overgrown.apoli.power.ApoliIds;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.builtin.SprintingPower;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
@OnlyIn(Dist.CLIENT)
public abstract class LocalPlayerSprintMixin {

    @Inject(method = "hasEnoughFoodToStartSprinting()Z", at = @At("RETURN"), cancellable = true)
    private void apoli$gateSprinting(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (PowerLookup.hasActive(self, ApoliIds.PREVENT_SPRINTING)) {
            cir.setReturnValue(false);
            return;
        }
        if (!cir.getReturnValueZ()) return;
        if (!self.getAbilities().mayfly) return;
        if (self.isPassenger()) return;
        if (self.getFoodData().getFoodLevel() > 6.0F) return;
        if (self.isCreative() || self.isSpectator()) return;
        cir.setReturnValue(false);
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void apoli$applySprintPowers(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (self.isSprinting()) {
            if (PowerLookup.hasActive(self, ApoliIds.PREVENT_SPRINTING)) self.setSprinting(false);
        } else if (self.input.up && SprintingPower.isSprinting(self)) {
            self.setSprinting(true);
        }
    }
}
