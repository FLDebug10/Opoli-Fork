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

    public static final boolean ENTITY_MODEL_FEATURES = FabricLoader.getInstance().isModLoaded("entity_model_features");

    public static final boolean BETTER_COMBAT = FabricLoader.getInstance().isModLoaded("bettercombat");

    public static final boolean WALKERS = FabricLoader.getInstance().isModLoaded("walkers");

    public static final boolean PUFFISH_SKILLS = FabricLoader.getInstance().isModLoaded("puffish_skills");

    public static final boolean PUFFISH_SKILLS_ORIGINS = FabricLoader.getInstance().isModLoaded("puffish_skills_origins");

    public static final boolean LEDGER = classPresent("com.github.quiltservertools.ledger.database.ActionQueueService");

    public static final boolean INDEXOR = classPresent("ledger.core.LedgerManager");

    public static final boolean YIGD = classPresent("com.b1n_ry.yigd.compat.InvModCompat")
        && classPresent("com.b1n_ry.yigd.events.LoadModCompatEvent");

    public static final boolean GRAVESTONES = classPresent("net.pneumono.gravestones.api.GravestoneDataType")
        && classPresent("net.pneumono.gravestones.api.event.GravestoneContentsEvents");

    public static boolean classPresent(String binaryName) {
        return ModCompat.class.getClassLoader().getResource(binaryName.replace('.', '/') + ".class") != null;
    }

    public static boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public static String versionOf(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse(null);
    }
}
