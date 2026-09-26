package dev.overgrown.apoli.mixin.effects;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.overgrown.apoli.effects.CustomEffectRegistry;
import dev.overgrown.apoli.effects.EffectConfig;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftServer.class)
public class MinecraftServerReloadMixin {
    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void apoli$beforeReload(Collection<String> selectedIds, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        if (EffectConfig.get().enabled()) CustomEffectRegistry.beforeReload((MinecraftServer) (Object) this);
    }

    @ModifyReturnValue(method = "reloadResources", at = @At("RETURN"))
    private CompletableFuture<Void> apoli$afterReload(CompletableFuture<Void> future) {
        return future.whenComplete((v, ex) -> {
            if (ex != null && EffectConfig.get().enabled()) CustomEffectRegistry.finishReload(false);
        });
    }
}
