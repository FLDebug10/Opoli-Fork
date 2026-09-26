package dev.overgrown.apoli.compat.yigd;

import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.data.DeathContext;
import com.b1n_ry.yigd.events.AdjustDropRuleEvent;
import com.b1n_ry.yigd.events.LoadModCompatEvent;
import com.b1n_ry.yigd.util.DropRule;
import dev.overgrown.apoli.power.builtin.KeepInventoryPower;
import net.minecraft.server.level.ServerPlayer;

public final class YigdCompat {
    private static final String VANILLA = "vanilla";
    private static final String UPSTREAM_ORIGINS_COMPAT = "com.b1n_ry.yigd.compat.OriginsCompat";

    private YigdCompat() {}

    public static void init() {
        LoadModCompatEvent.EVENT.register(mods -> {
            mods.removeIf(mod -> UPSTREAM_ORIGINS_COMPAT.equals(mod.getClass().getName()));
            mods.add(new ApoliYigdCompat());
        });
        AdjustDropRuleEvent.EVENT.register(YigdCompat::keepPowerItems);
    }

    private static void keepPowerItems(InventoryComponent inventory, DeathContext context) {
        ServerPlayer player = context.player();
        if (!KeepInventoryPower.isHeldBy(player)) return;
        inventory.handleGraveItems(VANILLA::equals, (stack, slot, graveItem) -> {
            if (KeepInventoryPower.keepsInventorySlot(player, slot, stack)) graveItem.dropRule = DropRule.KEEP;
        });
    }
}
