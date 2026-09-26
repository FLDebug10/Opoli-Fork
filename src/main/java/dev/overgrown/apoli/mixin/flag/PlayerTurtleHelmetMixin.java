package dev.overgrown.apoli.mixin.flag;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.overgrown.apoli.power.builtin.WaterBreathingPower;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class PlayerTurtleHelmetMixin {

    @ModifyExpressionValue(method = "turtleHelmetTick()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean apoli$invertTurtleHelmetForWaterBreathers(boolean eyeInWater) {
        return eyeInWater != WaterBreathingPower.suffocatesOutsideWater((Player) (Object) this);
    }
}
