package dev.overgrown.apoli.compat.yigd.mixin;

import com.b1n_ry.yigd.compat.CompatComponent;
import com.b1n_ry.yigd.components.InventoryComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = InventoryComponent.class, remap = false)
public interface InventoryComponentAccessor {
    @Accessor("modInventoryItems")
    Map<String, CompatComponent<?>> apoli$modInventories();
}
