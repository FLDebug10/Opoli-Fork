package dev.overgrown.apoli.action.builtin.item;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.condition.context.ItemCtx;
import dev.overgrown.apoli.data.Expression;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class CooldownItemAction implements ActionType<ItemCtx, CooldownItemAction.Cfg> {
    public record Cfg(Expression ticks) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Expression.INT_OR_EXPR.fieldOf("ticks").forGetter(Cfg::ticks)
        ).apply(i, Cfg::new));
    }

    @Override
    public void run(Cfg cfg, ItemCtx ctx) {
        if (!(ctx.holder() instanceof Player player)) return;
        ItemStack stack = ctx.stack();
        if (stack == null || stack.isEmpty()) return;
        int ticks = cfg.ticks.evalInt(player);
        if (ticks <= 0) return;
        player.getCooldowns().addCooldown(stack.getItem(), ticks);
    }
}
