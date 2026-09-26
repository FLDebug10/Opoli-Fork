package dev.overgrown.apoli.mixin.effects;

import dev.overgrown.apoli.effects.CustomEffectRegistry;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegistrySnapshot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(RegistrySnapshot.class)
public class NeoforgeRegistrySyncCustomEffectMixin {
    @Shadow
    @Final
    private Int2ObjectSortedMap<ResourceLocation> ids;

    @Inject(method = "<init>(Lnet/minecraft/core/Registry;Z)V", at = @At("RETURN"))
    private void apoli$hideCustomEffects(Registry<?> registry, boolean full, CallbackInfo ci) {
        if (full || !registry.key().equals(Registries.MOB_EFFECT)) return;
        Set<ResourceLocation> custom = CustomEffectRegistry.ids();
        if (!custom.isEmpty()) ids.values().removeIf(custom::contains);
    }
}
