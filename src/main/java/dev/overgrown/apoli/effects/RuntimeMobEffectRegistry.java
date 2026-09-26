package dev.overgrown.apoli.effects;

public interface RuntimeMobEffectRegistry {
    void apoli$unregisterCustom();
    void apoli$register(CustomMobEffect effect);
}
