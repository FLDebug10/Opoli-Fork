package dev.overgrown.apoli.compat.ears;

import com.unascribed.ears.api.EarsFeatureType;
import com.unascribed.ears.api.features.EarsFeatures;
import org.jetbrains.annotations.Nullable;

public interface EarsDelegateState {
    void apoli$begin(EarsFeatures features);

    void apoli$end();

    void apoli$feature(@Nullable EarsFeatureType feature);
}
