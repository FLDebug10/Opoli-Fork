package dev.overgrown.apoli.compat;

import net.neoforged.fml.ModList;

public final class ModCompat {
    private ModCompat() {}

    public static final boolean VOICECHAT = ModList.get().isLoaded("voicechat");

    public static final boolean NERB = ModList.get().isLoaded("nerb");

    public static final boolean LAMBDYNLIGHTS = ModList.get().isLoaded("lambdynlights");

    public static final boolean EARS = ModList.get().isLoaded("ears");

    public static final boolean SKIN_LAYERS_3D = ModList.get().isLoaded("skinlayers3d");

    public static final boolean ENTITY_MODEL_FEATURES = ModList.get().isLoaded("entity_model_features");

    public static final boolean ICARUS = ModList.get().isLoaded("icarus");

    public static final boolean FIGURA = ModList.get().isLoaded("figura");

    public static final boolean TRINKETS = ModList.get().isLoaded("trinkets");

    public static final boolean ACCESSORIES = ModList.get().isLoaded("accessories");

    public static final boolean CURIOS = ModList.get().isLoaded("curios");

    public static boolean anyAccessory() {
        return TRINKETS || ACCESSORIES || CURIOS;
    }

    public static final boolean HARDCORE_REVIVAL = ModList.get().isLoaded("hardcorerevival");

    public static final boolean BETTER_COMBAT = ModList.get().isLoaded("bettercombat");

    public static final boolean WALKERS = ModList.get().isLoaded("walkers");

    public static final boolean KUBEJS = ModList.get().isLoaded("kubejs");

    public static final boolean PUFFISH_SKILLS = ModList.get().isLoaded("puffish_skills");

    public static final boolean LEDGER = classPresent("com.github.quiltservertools.ledger.database.ActionQueueService");

    public static final boolean INDEXOR = classPresent("ledger.core.LedgerManager");

    public static final boolean YIGD = classPresent("com.b1n_ry.yigd.compat.InvModCompat")
        && classPresent("com.b1n_ry.yigd.events.YigdEvents");

    public static boolean classPresent(String binaryName) {
        return ModCompat.class.getClassLoader().getResource(binaryName.replace('.', '/') + ".class") != null;
    }

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    public static String versionOf(String modId) {
        return ModList.get().getModContainerById(modId)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse(null);
    }
}
