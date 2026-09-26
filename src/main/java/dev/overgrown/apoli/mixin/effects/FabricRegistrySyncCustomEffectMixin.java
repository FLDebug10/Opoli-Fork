package dev.overgrown.apoli.mixin.effects;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.overgrown.apoli.effects.CustomEffectRegistry;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.Set;

@Mixin(RegistrySyncManager.class)
public class FabricRegistrySyncCustomEffectMixin {
    @ModifyReturnValue(method = "createAndPopulateRegistryMap", at = @At("RETURN"))
    @Nullable
    private static Map<ResourceLocation, Object2IntMap<ResourceLocation>> apoli$filterCustomEffects(@Nullable Map<ResourceLocation, Object2IntMap<ResourceLocation>> original) {
        if (original == null) return null;
        Set<ResourceLocation> custom = CustomEffectRegistry.ids();
        if (custom.isEmpty()) return original;
        Object2IntMap<ResourceLocation> effects = original.get(Registries.MOB_EFFECT.location());
        if (effects == null) return original;
        for (ResourceLocation id : custom) {
            effects.removeInt(id);
        }
        return original;
    }
}
