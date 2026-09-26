package dev.overgrown.apoli.mixin.effects;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.Lifecycle;
import dev.overgrown.apoli.effects.CustomMobEffect;
import dev.overgrown.apoli.effects.RuntimeMobEffectRegistry;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Mixin(MappedRegistry.class)
public abstract class MobEffectRegistryCustomEffectMixin<T> implements RuntimeMobEffectRegistry {
    @Final
    @Shadow
    private ResourceKey<? extends Registry<T>> key;
    @Final
    @Shadow
    private ObjectList<Holder.Reference<T>> byId;
    @Final
    @Shadow
    private Object2IntMap<T> toId;
    @Final
    @Shadow
    private Map<ResourceLocation, Holder.Reference<T>> byLocation;
    @Final
    @Shadow
    private Map<ResourceKey<T>, Holder.Reference<T>> byKey;
    @Final
    @Shadow
    private Map<T, Holder.Reference<T>> byValue;
    @Final
    @Shadow
    private Map<T, Lifecycle> lifecycles;
    @Shadow
    private int nextId;
    @Shadow
    @Nullable
    private List<Holder.Reference<T>> holdersInOrder;

    @Unique
    private boolean apoli$allowWrite;

    @Unique
    private Map<ResourceKey<T>, Holder.Reference<T>> apoli$retired;

    @WrapMethod(method = "validateWrite()V")
    private void apoli$allowRuntimeWrite(Operation<Void> original) {
        if (this.apoli$allowWrite) return;
        original.call();
    }

    @WrapMethod(method = "validateWrite(Lnet/minecraft/resources/ResourceKey;)V")
    private void apoli$allowRuntimeWriteKey(ResourceKey<T> resourceKey, Operation<Void> original) {
        if (this.apoli$allowWrite) return;
        original.call(resourceKey);
    }

    @Override
    public void apoli$unregisterCustom() {
        if (!this.key.equals(Registries.MOB_EFFECT)) return;

        Iterator<Map.Entry<ResourceKey<T>, Holder.Reference<T>>> keys = this.byKey.entrySet().iterator();
        while (keys.hasNext()) {
            Map.Entry<ResourceKey<T>, Holder.Reference<T>> entry = keys.next();
            if (!apoli$isCustom(entry.getValue())) continue;
            keys.remove();
            if (this.apoli$retired == null) this.apoli$retired = new HashMap<>();
            this.apoli$retired.put(entry.getKey(), entry.getValue());
        }

        Iterator<Holder.Reference<T>> locations = this.byLocation.values().iterator();
        while (locations.hasNext()) {
            if (apoli$isCustom(locations.next())) locations.remove();
        }

        Iterator<T> values = this.byValue.keySet().iterator();
        while (values.hasNext()) {
            if (values.next() instanceof CustomMobEffect) values.remove();
        }

        Iterator<T> numbered = this.toId.keySet().iterator();
        while (numbered.hasNext()) {
            if (numbered.next() instanceof CustomMobEffect) numbered.remove();
        }

        Iterator<T> lifecycleKeys = this.lifecycles.keySet().iterator();
        while (lifecycleKeys.hasNext()) {
            if (lifecycleKeys.next() instanceof CustomMobEffect) lifecycleKeys.remove();
        }

        this.holdersInOrder = null;

        int first = -1;
        for (int i = 0; i < this.byId.size(); i++) {
            if (apoli$isCustom(this.byId.get(i))) {
                first = i;
                break;
            }
        }
        if (first < 0) return;

        int write = first;
        for (int read = first; read < this.byId.size(); read++) {
            Holder.Reference<T> reference = this.byId.get(read);
            if (reference == null || apoli$isCustom(reference)) continue;
            this.byId.set(write, reference);
            this.toId.put(reference.value(), write);
            write++;
        }
        this.byId.size(write);
        this.nextId = write;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void apoli$register(CustomMobEffect effect) {
        if (!this.key.equals(Registries.MOB_EFFECT)) return;

        ResourceKey<T> resourceKey = ResourceKey.create(this.key, effect.id);
        if (this.apoli$retired != null) {
            Holder.Reference<T> previous = this.apoli$retired.remove(resourceKey);
            if (previous != null) this.byKey.put(resourceKey, previous);
        }

        this.apoli$allowWrite = true;
        try {
            Registry.register((Registry<T>) (Object) this, resourceKey, (T) effect);
        } finally {
            this.apoli$allowWrite = false;
        }
    }

    @Unique
    private static boolean apoli$isCustom(Holder.Reference<?> reference) {
        return reference != null && reference.isBound() && reference.value() instanceof CustomMobEffect;
    }
}
