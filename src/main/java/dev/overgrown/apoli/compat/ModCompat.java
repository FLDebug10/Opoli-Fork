package dev.overgrown.apoli.compat;

import net.fabricmc.loader.api.FabricLoader;

public final class ModCompat {
    private ModCompat() {}

    public static final boolean ICARUS = FabricLoader.getInstance().isModLoaded("icarus");

    public static final boolean FIGURA = FabricLoader.getInstance().isModLoaded("figura");

    public static final boolean TRINKETS = FabricLoader.getInstance().isModLoaded("trinkets");

    public static final boolean ACCESSORIES = FabricLoader.getInstance().isModLoaded("accessories");

    public static final boolean CURIOS = FabricLoader.getInstance().isModLoaded("curios");

    public static boolean anyAccessory() {
        return TRINKETS || ACCESSORIES || CURIOS;
    }

    public static final boolean HARDCORE_REVIVAL = FabricLoader.getInstance().isModLoaded("hardcorerevival");

    public static final boolean VOICECHAT = FabricLoader.getInstance().isModLoaded("voicechat");

    public static final boolean NERB = FabricLoader.getInstance().isModLoaded("nerb");

    public static final boolean LAMBDYNLIGHTS = FabricLoader.getInstance().isModLoaded("lambdynlights");

    public static final boolean EARS = FabricLoader.getInstance().isModLoaded("ears");

    public static final boolean SKIN_LAYERS_3D = FabricLoader.getInstance().isModLoaded("skinlayers3d");

    public static final boolean BETTER_COMBAT = FabricLoader.getInstance().isModLoaded("bettercombat");

    public static final boolean PUFFISH_SKILLS = FabricLoader.getInstance().isModLoaded("puffish_skills");

    public static final boolean PUFFISH_SKILLS_ORIGINS = FabricLoader.getInstance().isModLoaded("puffish_skills_origins");

    public static final boolean RHINO = FabricLoader.getInstance().isModLoaded("rhino");

    public static final boolean ARCHITECTURY_API = FabricLoader.getInstance().isModLoaded("architectury");
}
