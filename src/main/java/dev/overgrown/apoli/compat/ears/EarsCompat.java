package dev.overgrown.apoli.compat.ears;

import com.unascribed.ears.api.EarsFeatureType;
import com.unascribed.ears.api.registry.EarsInhibitorRegistry;
import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.client.render.AttachmentParts;
import dev.overgrown.apoli.client.render.SkinRenderCompat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class EarsCompat {

    private static final String[][] FEATURE_KEYS = buildFeatureKeys();
    private static boolean installed;

    private EarsCompat() {}

    public static void init() {
        if (installed) return;
        installed = true;
        EarsInhibitorRegistry.register(Apoli.MOD_ID, EarsCompat::shouldInhibit);
    }

    private static boolean shouldInhibit(EarsFeatureType type, Object peer) {
        if (!(peer instanceof LivingEntity entity)) return false;
        if (SkinRenderCompat.suppressed(entity, FEATURE_KEYS[type.ordinal()])) return true;
        if (SkinRenderCompat.rgba(entity)[3] <= 0.001F) return true;
        int bits = EarsAttachments.featureBits(type);
        return bits != 0 && (AttachmentParts.hiddenMask(entity) & bits) == bits;
    }

    private static String[][] buildFeatureKeys() {
        EarsFeatureType[] types = EarsFeatureType.values();
        String[][] keys = new String[types.length][];
        for (int i = 0; i < types.length; i++) {
            keys[i] = new String[]{"ears", "ears_" + types[i].name().toLowerCase(Locale.ROOT)};
        }
        return keys;
    }
}
