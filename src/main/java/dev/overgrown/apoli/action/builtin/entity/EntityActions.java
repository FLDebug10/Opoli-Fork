package dev.overgrown.apoli.action.builtin.entity;

import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.action.ActionTypes;
import dev.overgrown.apoli.alias.AliasingOptions;
import dev.overgrown.apoli.compat.ModCompat;
import dev.overgrown.apoli.compat.accessory.action.ModifyAccessoryAction;
import dev.overgrown.apoli.compat.hardcorerevival.action.KnockOutAction;
import dev.overgrown.apoli.compat.hardcorerevival.action.ReviveAction;

public final class EntityActions {
    private EntityActions() {}

    public static void register() {
        ActionTypes.ENTITY.register(Apoli.id("run_function"), new RunFunctionAction());
        ActionTypes.ENTITY.register(Apoli.id("add_velocity"), new AddVelocityAction());
        ActionTypes.ENTITY.register(Apoli.id("nothing"), new NothingAction());
        ActionTypes.ENTITY.register(
            Apoli.id("team"),
            new TeamAction(),
            AliasingOptions.builder()
                .addTypeAlias(Apoli.id("modify_team"), java.util.Map.of("operation", "modify"))
                .addTypeAlias(Apoli.id("join_team"), java.util.Map.of("operation", "join"))
                .addTypeAlias(Apoli.id("leave_team"), java.util.Map.of("operation", "leave"))
                .build()
        );
        ActionTypes.ENTITY.register(Apoli.id("apply_effect"), new ApplyEffectAction());
        ActionTypes.ENTITY.register(Apoli.id("conjure_equipment"), new ConjureEquipmentAction());
        ActionTypes.ENTITY.register(Apoli.id("release_grab"), new ReleaseGrabAction());
        ActionTypes.ENTITY.register(Apoli.id("execute_command"), new ExecuteCommandAction());
        ActionTypes.ENTITY.register(Apoli.id("exhaust"), new ExhaustAction());
        ActionTypes.ENTITY.register(Apoli.id("heal"), new HealAction());
        ActionTypes.ENTITY.register(Apoli.id("set_on_fire"), new SetOnFireAction());
        ActionTypes.ENTITY.register(Apoli.id("explode"), new ExplodeEntityAction());
        ActionTypes.ENTITY.register(
            Apoli.id("action_on_entity_set"),
            new ActionOnEntitySetAction(),
            AliasingOptions.builder().addTypeAlias(Apoli.id("action_on_set")).build()
        );
        ActionTypes.ENTITY.register(
            Apoli.id("modify_resource"),
            new ModifyResourceAction(),
            AliasingOptions.builder()
                .addTypeAlias(Apoli.id("change_resource"))
                .addTypeAlias("origins:change_resource")
                .build()
        );
        ActionTypes.ENTITY.register(Apoli.id("trigger_cooldown"), new TriggerCooldownAction());

        ActionTypes.ENTITY.register(Apoli.id("dismount"), new DismountAction());
        ActionTypes.ENTITY.register(Apoli.id("extinguish"), new ExtinguishAction());
        ActionTypes.ENTITY.register(Apoli.id("crafting_table"), new CraftingTableAction());
        ActionTypes.ENTITY.register(Apoli.id("ender_chest"), new EnderChestAction());

        ActionTypes.ENTITY.register(Apoli.id("add_xp"), new AddXpAction());
        ActionTypes.ENTITY.register(Apoli.id("clear_effect"), new ClearEffectAction());
        ActionTypes.ENTITY.register(Apoli.id("feed"), new FeedAction());
        ActionTypes.ENTITY.register(Apoli.id("gain_air"), new GainAirAction());
        ActionTypes.ENTITY.register(Apoli.id("set_fall_distance"), new SetFallDistanceAction());
        ActionTypes.ENTITY.register(Apoli.id("emit_game_event"), new EmitGameEventAction());
        ActionTypes.ENTITY.register(Apoli.id("play_sound"), new PlaySoundAction());
        ActionTypes.ENTITY.register(Apoli.id("swing_hand"), new SwingHandAction());
        ActionTypes.ENTITY.register(Apoli.id("force_key_pressed"), new ForceKeyPressedAction(),
            AliasingOptions.builder()
                .addTypeAlias(Apoli.id("press_key"))
                .addTypeAlias(Apoli.id("force_key"))
                .build());

        ActionTypes.ENTITY.register(Apoli.id("grant_power"), new GrantPowerAction());
        ActionTypes.ENTITY.register(Apoli.id("grant_all_powers"), new GrantAllPowersAction());
        ActionTypes.ENTITY.register(Apoli.id("revoke_power"), new RevokePowerAction());
        ActionTypes.ENTITY.register(Apoli.id("revoke_all_powers"), new RevokeAllPowersAction());
        ActionTypes.ENTITY.register(Apoli.id("remove_power"), new RemovePowerAction());
        ActionTypes.ENTITY.register(Apoli.id("suppress_power"), new SuppressPowerAction(),
            AliasingOptions.builder().renameField("powers", "power").renameField("sources", "source").build());
        ActionTypes.ENTITY.register(Apoli.id("unsuppress_power"), new UnsuppressPowerAction(),
            AliasingOptions.builder().renameField("powers", "power").renameField("sources", "source").build());
        ActionTypes.ENTITY.register(Apoli.id("modify_stat"), new ModifyStatAction(),
            AliasingOptions.builder()
                .addTypeAlias(Apoli.id("change_stat"))
                .addTypeAlias(Apoli.id("set_stat"))
                .build());
        ActionTypes.ENTITY.register(Apoli.id("text"), new TextAction(),
            AliasingOptions.builder()
                .addTypeAlias(Apoli.id("text_bar"))
                .addTypeAlias(Apoli.id("show_text"))
                .build());
        ActionTypes.ENTITY.register(Apoli.id("modify_death_ticks"), new ModifyDeathTicksAction());
        ActionTypes.ENTITY.register(Apoli.id("toggle"), new ToggleEntityAction());

        ActionTypes.ENTITY.register(Apoli.id("modify_entity_data"), new ModifyEntityDataAction(),
            AliasingOptions.builder()
                .addTypeAlias(Apoli.id("set_entity_data"))
                .addTypeAlias(Apoli.id("change_entity_data"))
                .addTypeAlias(Apoli.id("set_no_gravity"), java.util.Map.of("data", "no_gravity"))
                .addTypeAlias(Apoli.id("set_gravity"), java.util.Map.of("data", "no_gravity"))
                .build());

        ActionTypes.ENTITY.register(Apoli.id("advancement"), new AdvancementAction(false));
        ActionTypes.ENTITY.register(Apoli.id("grant_advancement"), new AdvancementAction(false));
        ActionTypes.ENTITY.register(Apoli.id("revoke_advancement"), new AdvancementAction(true));

        ActionTypes.ENTITY.register(Apoli.id("relative_action"), new RelativeAction(RelativeAction.Target.PASSENGER));
        ActionTypes.ENTITY.register(Apoli.id("passenger_action"), new RelativeAction(RelativeAction.Target.PASSENGER));
        ActionTypes.ENTITY.register(Apoli.id("riding_action"), new RelativeAction(RelativeAction.Target.VEHICLE));

        ActionTypes.ENTITY.register(Apoli.id("inventory_action"), new InventoryAction(),
            AliasingOptions.builder()
                .addTypeAlias(Apoli.id("modify_inventory"))
                .addTypeAlias(Apoli.id("replace_inventory"), java.util.Map.of("operation", "replace"))
                .addTypeAlias(Apoli.id("drop_inventory"), java.util.Map.of("operation", "drop"))
                .build());

        ActionTypes.ENTITY.register(Apoli.id("change_slot"), new ChangeSlotAction(),
            AliasingOptions.builder()
                .addTypeAlias(Apoli.id("swap_slot"), java.util.Map.of("operation", "swap"))
                .addTypeAlias(Apoli.id("move_slot"), java.util.Map.of("operation", "move"))
                .build());
        ActionTypes.ENTITY.register(Apoli.id("change_selected_slot"), new ChangeSelectedSlotAction(),
            AliasingOptions.builder().addTypeAlias(Apoli.id("set_selected_slot")).build());

        ActionTypes.ENTITY.register(Apoli.id("give"), new GiveAction());
        ActionTypes.ENTITY.register(Apoli.id("equipped_item_action"), new EquippedItemAction());
        ActionTypes.ENTITY.register(Apoli.id("damage"), new DamageAction());
        ActionTypes.ENTITY.register(Apoli.id("spawn_entity"), new SpawnEntityAction());
        ActionTypes.ENTITY.register(Apoli.id("spawn_effect_cloud"), new SpawnEffectCloudAction());
        ActionTypes.ENTITY.register(Apoli.id("spawn_particles"), new SpawnParticlesAction());

        ActionTypes.ENTITY.register(Apoli.id("summon_clone"), new SummonCloneAction());
        ActionTypes.ENTITY.register(Apoli.id("summon_minion"), new SummonMinionAction());
        ActionTypes.ENTITY.register(Apoli.id("set_summon_max_life"), new SetSummonMaxLifeAction(),
            AliasingOptions.builder().addTypeAlias(Apoli.id("set_summon_max_life_ticks")).build());

        ActionTypes.ENTITY.register(Apoli.id("disguise_as"), new DisguiseAsAction(),
            AliasingOptions.builder().addTypeAlias(Apoli.id("disguise_as_player")).build());
        ActionTypes.ENTITY.register(Apoli.id("remove_disguise"), new RemoveDisguiseAction());

        ActionTypes.ENTITY.register(Apoli.id("add_skill_points"), new AddSkillPointsAction());
        ActionTypes.ENTITY.register(Apoli.id("grant_skill_tree"), new GrantSkillTreeAction());
        ActionTypes.ENTITY.register(Apoli.id("revoke_skill_tree"), new RevokeSkillTreeAction());

        ActionTypes.ENTITY.register(Apoli.id("block_action_at"), new BlockActionAtAction());
        ActionTypes.ENTITY.register(Apoli.id("area_of_effect"), new AreaOfEffectAction());
        ActionTypes.ENTITY.register(Apoli.id("selector_action"), new SelectorAction());
        ActionTypes.ENTITY.register(Apoli.id("random_teleport"), new RandomTeleportAction());

        ActionTypes.ENTITY.register(Apoli.id("raycast"), new RaycastAction());
        ActionTypes.ENTITY.register(Apoli.id("fire_projectile"), new FireProjectileEntityAction());

        ActionTypes.ENTITY.register(Apoli.id("attach_rope"), new AttachRopeAction());
        ActionTypes.ENTITY.register(Apoli.id("detach_rope"), new DetachRopeAction());
        ActionTypes.ENTITY.register(Apoli.id("rope_pull"), new RopePullAction());
        ActionTypes.ENTITY.register(Apoli.id("reset_skills"), new ResetSkillsAction());

        ActionTypes.ENTITY.register(Apoli.id("radial_menu"), new RadialMenuAction(),
            AliasingOptions.builder().addTypeAlias("sync:radial_menu").build());

        if (ModCompat.anyAccessory()) {
            ActionTypes.ENTITY.register(Apoli.id("modify_accessory"), new ModifyAccessoryAction(),
                AliasingOptions.builder().addTypeAlias(Apoli.id("modify_trinket")).build());
        }

        if (ModCompat.HARDCORE_REVIVAL) {
            ActionTypes.ENTITY.register(Apoli.id("knock_out"), new KnockOutAction());
            ActionTypes.ENTITY.register(Apoli.id("revive"), new ReviveAction());
        }

        if (ModCompat.RHINO) {
            ActionTypes.ENTITY.register(Apoli.id("script"), new JavaScriptEntityAction());
        }
    }
}
