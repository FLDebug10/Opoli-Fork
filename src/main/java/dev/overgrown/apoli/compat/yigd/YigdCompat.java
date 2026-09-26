package dev.overgrown.apoli.compat.yigd;

import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.data.DeathContext;
import com.b1n_ry.yigd.events.YigdEvents;
import com.b1n_ry.yigd.util.DropRule;
import dev.overgrown.apoli.power.builtin.KeepInventoryPower;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

public final class YigdCompat {
    private static final String VANILLA = "vanilla";

    private YigdCompat() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(YigdEvents.LoadModCompatEvent.class, event -> event.addModCompat(new ApoliYigdCompat()));
        NeoForge.EVENT_BUS.addListener(YigdEvents.AdjustDropRuleEvent.class,
            event -> keepPowerItems(event.getInventoryComponent(), event.getDeathContext()));
    }

    private static void keepPowerItems(InventoryComponent inventory, DeathContext context) {
        ServerPlayer player = context.player();
        if (!KeepInventoryPower.isHeldBy(player)) return;
        inventory.handleGraveItems(VANILLA::equals, (stack, slot, graveItem) -> {
            if (KeepInventoryPower.keepsInventorySlot(player, slot, stack)) graveItem.dropRule = DropRule.KEEP;
        });
    }
}
