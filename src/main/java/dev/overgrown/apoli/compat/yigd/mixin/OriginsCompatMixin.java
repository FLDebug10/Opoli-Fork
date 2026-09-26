package dev.overgrown.apoli.compat.yigd.mixin;

import com.b1n_ry.yigd.compat.OriginsCompat;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.fabricmc.fabric.api.event.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = OriginsCompat.class, remap = false)
public abstract class OriginsCompatMixin {

    @WrapWithCondition(method = "<init>", require = 1, at = @At(value = "INVOKE",
        target = "Lnet/fabricmc/fabric/api/event/Event;register(Ljava/lang/Object;)V"))
    private boolean apoli$skipUpstreamKeepInventoryHook(Event<?> event, Object listener) {
        return false;
    }
}
