package dev.overgrown.apoli.condition.builtin.bientity;

import com.mojang.serialization.MapCodec;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.shared.EmptyCfg;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

public final class ProjectileOwnerCondition implements ConditionType<BiEntityCtx, EmptyCfg> {
    @Override
    public MapCodec<EmptyCfg> codec() {
        return MapCodec.unit(EmptyCfg.INSTANCE);
    }

    @Override
    public boolean test(EmptyCfg cfg, BiEntityCtx ctx) {
        Entity actor = ctx.actor();
        if (actor == null || !(ctx.target() instanceof Projectile projectile)) return false;
        Entity owner = projectile.getOwner();
        return owner != null && owner.equals(actor);
    }
}
