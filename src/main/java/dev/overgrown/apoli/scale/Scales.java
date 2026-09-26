package dev.overgrown.apoli.scale;

import dev.overgrown.apoli.compat.pehkui.PehkuiBridge;
import dev.overgrown.apoli.power.ApoliIds;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.builtin.ScalePower;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class Scales {
    private Scales() {}

    @Nullable
    private static Consumer<Entity> syncer;

    public static void setSyncer(Consumer<Entity> sender) {
        syncer = sender;
    }

    public static @Nullable ScaleState stateOf(Entity entity) {
        return ((ScaleHolder) entity).apoli$scales();
    }

    public static ScaleState stateOrCreate(Entity entity) {
        return ((ScaleHolder) entity).apoli$scalesOrCreate();
    }

    public static boolean anyPowerLoaded() {
        return ApoliPowers.anyOfType(ApoliIds.SCALE);
    }

    public static boolean untouched(Entity entity) {
        if (!anyPowerLoaded()) return stateOf(entity) == null;
        if (stateOf(entity) != null) return false;
        return !ScalePower.hasAny(entity);
    }

    public static float value(Entity entity, ScaleType type) {
        return value(entity, type, 1.0F);
    }

    public static float value(Entity entity, ScaleType type, float partial) {
        ScaleState state = stateOf(entity);
        if (state == null) {
            if (!anyPowerLoaded() || !ScalePower.hasAny(entity)) return 1.0F;
            state = stateOrCreate(entity);
        }
        return resolve(entity, state, state.isAnimating() ? partial : 1.0F)[type.index()];
    }

    public static float applied(Entity entity, ScaleType type) {
        return applied(entity, type, 1.0F);
    }

    public static float applied(Entity entity, ScaleType type, float partial) {
        if (!PehkuiBridge.ownsGeometry()) return value(entity, type, partial);
        return PehkuiBridge.handles(type) ? 1.0F : beyondPehkui(entity, type, partial);
    }

    public static float appliedIfScaled(Entity entity, ScaleType type, float partial) {
        if (untouched(entity)) return 1.0F;
        return applied(entity, type, partial);
    }

    private static float beyondPehkui(Entity entity, ScaleType type, float partial) {
        ScaleState state = stateOf(entity);
        if (state == null) {
            if (!anyPowerLoaded() || !ScalePower.hasAny(entity)) return 1.0F;
            state = stateOrCreate(entity);
        }
        resolve(entity, state, state.isAnimating() ? partial : 1.0F);
        return ownBeyondPehkui(state, type);
    }

    private static float ownBeyondPehkui(ScaleState state, ScaleType type) {
        float v = state.ownAt(type.index());
        ScaleType[] multipliers = type.multipliers();
        for (int m = 0; m < multipliers.length; m++) {
            if (!PehkuiBridge.handles(multipliers[m])) v *= ownBeyondPehkui(state, multipliers[m]);
        }
        ScaleType[] divisors = type.divisors();
        for (int d = 0; d < divisors.length; d++) {
            if (PehkuiBridge.handles(divisors[d])) continue;
            float f = ownBeyondPehkui(state, divisors[d]);
            if (f != 0.0F) v /= f;
        }
        return ScaleState.clamp(v);
    }

    public static float own(Entity entity, ScaleType type) {
        ScaleState state = stateOf(entity);
        if (state == null) {
            if (!anyPowerLoaded() || !ScalePower.hasAny(entity)) return ScaleState.DEFAULT;
            state = stateOrCreate(entity);
        }
        resolve(entity, state, 1.0F);
        return state.ownAt(type.index());
    }

    public static float base(Entity entity, ScaleType type) {
        ScaleState state = stateOf(entity);
        return state == null ? ScaleState.DEFAULT : state.base(type);
    }

    public static void set(Entity entity, ScaleType type, float target, int overTicks, ScaleEasing easing) {
        ScaleState state = stateOrCreate(entity);
        state.set(type, target, overTicks, easing);
        onChanged(entity, state);
    }

    public static void setAll(Entity entity, java.util.List<ScaleType> types, ScaleOperation operation,
                              float argument, int overTicks, ScaleEasing easing) {
        int n = types.size();
        if (n == 0) return;
        ScaleState state = stateOrCreate(entity);
        for (int i = 0; i < n; i++) {
            ScaleType type = types.get(i);
            state.set(type, operation.apply(state.target(type), argument), overTicks, easing);
        }
        onChanged(entity, state);
    }

    public static void reset(Entity entity) {
        ScaleState state = stateOf(entity);
        if (state == null) return;
        state.reset();
        onChanged(entity, state);
    }

    public static void transfer(Entity from, Entity to) {
        ScaleState source = stateOf(from);
        if (source == null || source.isDefault()) return;
        ScaleState target = stateOrCreate(to);
        target.copyFrom(source);
        onChanged(to, target);
    }

    public static void invalidate(Entity entity) {
        ScaleState state = stateOf(entity);
        if (state == null) return;
        state.invalidate();
        state.forgetDimensions();
        refreshDimensions(entity, state);
    }

    public static void onChanged(Entity entity, ScaleState state) {
        state.invalidate();
        refreshDimensions(entity, state);
        sync(entity);
    }

    public static void sync(Entity entity) {
        if (!entity.level().isClientSide() && syncer != null) syncer.accept(entity);
    }

    public static void syncIfScaled(Entity entity) {
        ScaleState state = stateOf(entity);
        if (state == null || state.isDefault()) return;
        sync(entity);
    }

    public static void tick(Entity entity, ScaleState state) {
        if (!state.needsTick()) return;
        state.tick();
        refreshDimensions(entity, state);
    }

    public static float[] ownAll(Entity entity, ScaleState state) {
        resolve(entity, state, 1.0F);
        return state.ownSlot();
    }

    public static void refreshDimensions(Entity entity, ScaleState state) {
        if (PehkuiBridge.ownsGeometry()) {
            PehkuiBridge.push(entity, state);
            return;
        }
        float width = value(entity, ScaleTypes.HITBOX_WIDTH);
        float height = value(entity, ScaleTypes.HITBOX_HEIGHT);
        if (!state.dimensionsChanged(width, height)) return;
        entity.refreshDimensions();
    }

    private static float[] resolve(Entity entity, ScaleState state, float partial) {
        long gameTime = entity.level().getGameTime();
        int generation = ApoliPowers.generation();
        float[] hit = state.cached(gameTime, generation, partial);
        if (hit != null) return hit;

        float[] out = state.cacheSlot();
        int n = ScaleTypes.count();
        for (int i = 0; i < n; i++) out[i] = state.baseAt(i, partial);
        boolean powered = anyPowerLoaded() && ScalePower.applyFactors(entity, out);
        System.arraycopy(out, 0, state.ownSlot(), 0, n);

        for (int i = 0; i < n; i++) {
            ScaleType type = ScaleTypes.byIndex(i);
            float v = out[i];
            ScaleType[] multipliers = type.multipliers();
            for (int m = 0; m < multipliers.length; m++) v *= out[multipliers[m].index()];
            ScaleType[] divisors = type.divisors();
            for (int d = 0; d < divisors.length; d++) {
                float f = out[divisors[d].index()];
                if (f != 0.0F) v /= f;
            }
            out[i] = ScaleState.clamp(v);
        }
        state.storeCache(gameTime, generation, partial, powered || state.isAnimating());
        return out;
    }
}
