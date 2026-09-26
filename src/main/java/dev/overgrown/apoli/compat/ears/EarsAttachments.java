package dev.overgrown.apoli.compat.ears;

import com.unascribed.ears.api.EarsFeatureType;
import com.unascribed.ears.api.features.EarsFeatures;
import dev.overgrown.apoli.data.BodyAttachments;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class EarsAttachments {
    private EarsAttachments() {}

    public static int featureBits(EarsFeatureType feature) {
        return switch (feature) {
            case EARS -> BodyAttachments.EARS;
            case WINGS -> BodyAttachments.WINGS;
            default -> single(feature);
        };
    }

    public static int bit(@Nullable EarsFeatureType feature, @Nullable EarsFeatures features, int quad) {
        if (feature == null) return 0;
        return switch (feature) {
            case EARS -> features == null ? BodyAttachments.EAR_PAIR : ear(features.earMode, quad);
            case WINGS -> features == null ? BodyAttachments.WING_PAIR : wing(features.wingMode, quad);
            default -> single(feature);
        };
    }

    private static int single(EarsFeatureType feature) {
        return switch (feature) {
            case HORN -> BodyAttachments.HORNS;
            case SNOUT -> BodyAttachments.SNOUT;
            case TAIL -> BodyAttachments.TAIL;
            case CLAW_RIGHT_ARM -> BodyAttachments.RIGHT_ARM_CLAW;
            case CLAW_LEFT_ARM -> BodyAttachments.LEFT_ARM_CLAW;
            case CLAW_RIGHT_LEG -> BodyAttachments.RIGHT_LEG_CLAW;
            case CLAW_LEFT_LEG -> BodyAttachments.LEFT_LEG_CLAW;
            case CHEST -> BodyAttachments.CHEST;
            case CAPE -> BodyAttachments.CAPE;
            default -> 0;
        };
    }

    private static int ear(@Nullable EarsFeatures.EarMode mode, int quad) {
        if (mode == null) return BodyAttachments.EAR_PAIR;
        return switch (mode) {
            case SIDES, BEHIND, FLOPPY, OUT -> quad < 2 ? BodyAttachments.RIGHT_EAR : BodyAttachments.LEFT_EAR;
            case AROUND -> quad < 2 ? BodyAttachments.EAR_PAIR
                : quad < 4 ? BodyAttachments.RIGHT_EAR : BodyAttachments.LEFT_EAR;
            default -> BodyAttachments.EAR_PAIR;
        };
    }

    private static int wing(@Nullable EarsFeatures.WingMode mode, int quad) {
        if (mode == null) return BodyAttachments.WING_PAIR;
        return switch (mode) {
            case SYMMETRIC_DUAL, ASYMMETRIC_DUAL -> quad < 2 ? BodyAttachments.RIGHT_WING : BodyAttachments.LEFT_WING;
            case ASYMMETRIC_R -> BodyAttachments.RIGHT_WING;
            case ASYMMETRIC_L -> BodyAttachments.LEFT_WING;
            default -> BodyAttachments.WING_PAIR;
        };
    }
}
