package dev.overgrown.apoli.mixin.effects;

import dev.overgrown.apoli.effects.CustomEffectRegistry;
import dev.overgrown.apoli.effects.EffectConfig;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ReloadableServerResources.class)
public class ReloadableServerResourcesCustomEffectMixin {
    @Inject(method = "loadResources(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/RegistryAccess$Frozen;Lnet/minecraft/world/flag/FeatureFlagSet;Lnet/minecraft/commands/Commands$CommandSelection;ILjava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
    private static void apoli$stageCustomEffects(ResourceManager resourceManager, RegistryAccess.Frozen registries,
                                                 FeatureFlagSet features, Commands.CommandSelection selection, int functionLevel,
                                                 Executor backgroundExecutor, Executor gameExecutor,
                                                 CallbackInfoReturnable<CompletableFuture<ReloadableServerResources>> cir) {
        if (EffectConfig.get().enabled()) CustomEffectRegistry.stage(resourceManager);
    }
}
