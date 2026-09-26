package dev.overgrown.apoli.action.builtin.bientity;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.codec.LoggedOptionalField;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.data.Hand;
import dev.overgrown.apoli.data.ItemStackData;
import dev.overgrown.apoli.mixin.damage.LivingEntityAttackStrengthAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.Optional;

public final class PunchBiEntityAction implements ActionType<BiEntityCtx, PunchBiEntityAction.Cfg> {
    public record Cfg(Optional<ItemStackData> stack, Optional<ResourceLocation> damageType, Hand hand,
                      boolean swingHand, boolean resetCooldown, boolean ignoreCooldown) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            LoggedOptionalField.strict("stack", ItemStackData.CODEC).forGetter(Cfg::stack),
            LoggedOptionalField.strict("damage_type", IdCodecs.ID).forGetter(Cfg::damageType),
            Hand.CODEC.optionalFieldOf("hand", Hand.MAIN_HAND).forGetter(Cfg::hand),
            Codec.BOOL.optionalFieldOf("swing_hand", true).forGetter(Cfg::swingHand),
            Codec.BOOL.optionalFieldOf("reset_cooldown", true).forGetter(Cfg::resetCooldown),
            Codec.BOOL.optionalFieldOf("ignore_cooldown", false).forGetter(Cfg::ignoreCooldown)
        ).apply(i, Cfg::new));
    }

    @Override
    public void run(Cfg cfg, BiEntityCtx ctx) {
        Entity target = ctx.target();
        LivingEntity actor = ctx.livingActor();
        if (target == null || actor == null) return;
        if (!(ctx.level() instanceof ServerLevel level)) return;

        ItemStack substitute = cfg.stack.map(ItemStackData::stack).map(ItemStack::copy).orElse(null);
        if (substitute == null && cfg.hand == Hand.OFF_HAND) substitute = actor.getItemInHand(InteractionHand.OFF_HAND);
        ItemStack original = null;
        if (substitute != null) {
            original = actor.getItemInHand(InteractionHand.MAIN_HAND);
            hold(actor, original, substitute);
        }
        LivingEntityAttackStrengthAccessor cooldown = null;
        int restoreTicker = 0;
        if (actor instanceof Player player && (cfg.ignoreCooldown || !cfg.resetCooldown)) {
            cooldown = (LivingEntityAttackStrengthAccessor) actor;
            restoreTicker = cooldown.apoli$getAttackStrengthTicker();
            if (cfg.ignoreCooldown) {
                cooldown.apoli$setAttackStrengthTicker((int) Math.ceil(player.getCurrentItemAttackStrengthDelay()) + 1);
            }
        }
        try {
            if (cfg.damageType.isPresent()) {
                typed(cfg.damageType.get(), actor, target, level);
            } else if (actor instanceof Player player) {
                player.attack(target);
            } else if (actor instanceof Mob mob) {
                mob.doHurtTarget(target);
            } else {
                hurt(actor, target, level, actor.damageSources().mobAttack(actor));
            }
            if (cfg.swingHand) actor.swing(cfg.hand.vanilla(), true);
        } finally {
            if (cooldown != null && !cfg.resetCooldown) cooldown.apoli$setAttackStrengthTicker(restoreTicker);
            if (substitute != null) hold(actor, substitute, original);
        }
    }

    private static void hold(LivingEntity actor, ItemStack previous, ItemStack next) {
        Multimap<Holder<Attribute>, AttributeModifier> removed = HashMultimap.create();
        previous.forEachModifier(EquipmentSlot.MAINHAND, removed::put);
        if (!removed.isEmpty()) actor.getAttributes().removeAttributeModifiers(removed);
        actor.setItemInHand(InteractionHand.MAIN_HAND, next);
        Multimap<Holder<Attribute>, AttributeModifier> added = HashMultimap.create();
        next.forEachModifier(EquipmentSlot.MAINHAND, added::put);
        if (!added.isEmpty()) actor.getAttributes().addTransientAttributeModifiers(added);
    }

    private static void typed(ResourceLocation damageType, LivingEntity actor, Entity target, ServerLevel level) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, damageType);
        Optional<Holder.Reference<DamageType>> holder = level.registryAccess()
            .registryOrThrow(Registries.DAMAGE_TYPE).getHolder(key);
        if (holder.isEmpty()) return;
        hurt(actor, target, level, new DamageSource(holder.get(), actor));
    }

    private static void hurt(LivingEntity actor, Entity target, ServerLevel level, DamageSource source) {
        float base = (float) actor.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float charge = actor instanceof Player player ? player.getAttackStrengthScale(0.5F) : 1.0F;
        float enchanted = EnchantmentHelper.modifyDamage(level, actor.getWeaponItem(), target, source, base) - base;
        float amount = base * (0.2F + charge * charge * 0.8F) + enchanted * charge;
        if (actor instanceof Player player) player.resetAttackStrengthTicker();
        if (amount > 0.0F) target.hurt(source, amount);
    }
}
