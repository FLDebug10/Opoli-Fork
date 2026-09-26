package dev.overgrown.apoli.action.builtin.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.data.Expression;
import net.minecraft.world.entity.LivingEntity;

public final class HealAction implements ActionType<EntityCtx, HealAction.Cfg> {
    public record Cfg(Expression amount) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Expression.FLOAT_OR_EXPR.fieldOf("amount").forGetter(Cfg::amount)
        ).apply(i, Cfg::new));
    }

    @Override
    public void run(Cfg cfg, EntityCtx ctx) {
        LivingEntity living = ctx.living();
        if (living == null || living.isDeadOrDying()) return;
        float amount = (float) cfg.amount.eval(living);
        if (amount > 0.0F) {
            living.heal(amount);
        } else if (amount < 0.0F) {
            float health = living.getHealth() + amount;
            if (health > 0.0F) {
                living.setHealth(health);
            } else {
                living.hurt(living.damageSources().generic(), Float.MAX_VALUE);
            }
        }
    }
}
