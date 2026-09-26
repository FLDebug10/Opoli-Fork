package dev.overgrown.apoli.compat.pehkui;

import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.compat.ModCompat;
import dev.overgrown.apoli.scale.ScaleState;
import dev.overgrown.apoli.scale.ScaleType;
import dev.overgrown.apoli.scale.ScaleTypes;
import dev.overgrown.apoli.scale.Scales;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PehkuiBridge {
    private PehkuiBridge() {}

    public static final boolean PRESENT = ModCompat.classPresent("virtuoel.pehkui.api.ScaleTypes");

    private static boolean ready;
    private static boolean failed;
    private static Method getScaleData;
    private static Method setScale;
    private static Method getBaseScale;
    private static final Map<ScaleType, Object> MAPPED = new HashMap<>();
    private static boolean[] handled = new boolean[0];

    public static boolean ownsGeometry() {
        return PRESENT && wire();
    }

    private static boolean wire() {
        if (ready) return !failed;
        ready = true;
        try {
            Class<?> typesClass = Class.forName("virtuoel.pehkui.api.ScaleTypes");
            Class<?> typeClass = Class.forName("virtuoel.pehkui.api.ScaleType");
            Class<?> dataClass = Class.forName("virtuoel.pehkui.api.ScaleData");
            getScaleData = typeClass.getMethod("getScaleData", Entity.class);
            setScale = dataClass.getMethod("setScale", float.class);
            getBaseScale = dataClass.getMethod("getBaseScale");
            List<ScaleType> all = ScaleTypes.all();
            for (int i = 0, n = all.size(); i < n; i++) {
                ScaleType type = all.get(i);
                try {
                    Field field = typesClass.getField(type.id().getPath().toUpperCase(java.util.Locale.ROOT));
                    Object value = field.get(null);
                    if (typeClass.isInstance(value)) MAPPED.put(type, value);
                } catch (NoSuchFieldException ignored) {
                }
            }
            boolean[] owned = new boolean[all.size()];
            for (ScaleType type : MAPPED.keySet()) owned[type.index()] = true;
            handled = owned;
            failed = MAPPED.isEmpty();
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            Apoli.LOGGER.warn("[Apoli] Pehkui is installed but its scale API could not be bound; "
                + "Apoli will apply its own scaling instead ({})", e.toString());
        }
        return !failed;
    }

    public static boolean handles(ScaleType type) {
        boolean[] owned = handled;
        int index = type.index();
        return index < owned.length && owned[index];
    }

    public static void push(Entity entity, ScaleState state) {
        if (!ownsGeometry()) return;
        if (entity.level().isClientSide()) return;
        float[] own = Scales.ownAll(entity, state);
        if (!state.pushedScalesChanged(own)) return;
        for (Map.Entry<ScaleType, Object> entry : MAPPED.entrySet()) {
            float value = own[entry.getKey().index()];
            try {
                Object data = getScaleData.invoke(entry.getValue(), entity);
                if (data == null) continue;
                float current = (Float) getBaseScale.invoke(data);
                if (Math.abs(current - value) < 1.0E-5F) continue;
                setScale.invoke(data, value);
            } catch (ReflectiveOperationException | RuntimeException e) {
                failed = true;
                state.forgetDimensions();
                Apoli.LOGGER.warn("[Apoli] Pehkui scale bridge failed, falling back to Apoli scaling ({})",
                    e.toString());
                return;
            }
        }
    }
}
