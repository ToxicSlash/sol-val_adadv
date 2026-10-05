package vice.sol_valheim.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vice.sol_valheim.ModConfig;
import vice.sol_valheim.SOLValheim;
import vice.sol_valheim.ValheimFoodData;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

@Mixin({Player.class})
public abstract class PlayerEntityMixin extends LivingEntity implements PlayerEntityMixinDataAccessor
{
    @Unique
    private ValheimFoodData sol_valheim$food_data = new ValheimFoodData();

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    @Unique
    public ValheimFoodData sol_valheim$getFoodData() {
        return sol_valheim$food_data;
    }

    @Override
    @Unique
    public void sol_valheim$setFoodDataFromServer(ValheimFoodData data) {
        if (data == null) {
            sol_valheim$food_data = new ValheimFoodData();
            return;
        }

        var loaded = ValheimFoodData.read(data.save(new CompoundTag()));
        sol_valheim$food_data.MaxItemSlots = loaded.MaxItemSlots;
        sol_valheim$food_data.DrinkSlot = loaded.DrinkSlot == null ? null : new ValheimFoodData.EatenFoodItem(loaded.DrinkSlot);
        sol_valheim$food_data.ItemEntries.clear();
        for (var entry : loaded.ItemEntries)
            sol_valheim$food_data.ItemEntries.add(new ValheimFoodData.EatenFoodItem(entry));
    }

    @Inject(at = {@At("HEAD")}, method = {"causeFoodExhaustion(F)V"}, cancellable = true)
    private void onAddExhaustion(float exhaustion, CallbackInfo info) {
        info.cancel();
    }

    @Inject(at = {@At("HEAD")}, method = {"eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"}, cancellable = true)
    private void onEatFood(Level world, ItemStack stack, CallbackInfoReturnable<ItemStack> info) {
        if (stack.getItem() == Items.ROTTEN_FLESH) {
            if (sol_valheim$food_data.hasActiveFood()) {
                sol_valheim$food_data.clear();
                sol_valheim$trackData();
            }
            return;
        }

        // Validate again at completion. The stomach may have changed while the
        // use animation was running, especially with client/server latency.
        if (!sol_valheim$food_data.canEat(stack)) {
            sol_valheim$trackData();
            info.setReturnValue(stack);
            info.cancel();
            return;
        }

        sol_valheim$food_data.eatItem(stack);
        sol_valheim$trackData();

        #if PRE_CURRENT_MC_1_19_2
        if (!this.level.isClientSide)
        #elif POST_CURRENT_MC_1_20_1
        if (!this.level().isClientSide)
        #endif
            sol_valheim$refreshFoodStats((Player) (Object) this);
    }

    @Inject(at = {@At("HEAD")}, method = {"tick"})
    private void onTick(CallbackInfo info) {
        #if PRE_CURRENT_MC_1_19_2
        var level = this.level;
        #elif POST_CURRENT_MC_1_20_1
        var level = this.level();
        #endif

        // Client-side prediction keeps HUD countdowns smooth. The server sends
        // a correction once per second and immediately after state changes.
        if (level.isClientSide) {
            if (sol_valheim$food_data.hasActiveFood())
                sol_valheim$food_data.tick();
            return;
        }

        if (isDeadOrDying()) {
            if (sol_valheim$food_data.hasActiveFood()) {
                sol_valheim$food_data.clear();
                sol_valheim$trackData();
            }
            return;
        }

        Player player = (Player) (Object) this;
        boolean hasFood = sol_valheim$food_data.hasActiveFood();
        if (hasFood)
            sol_valheim$food_data.tick();

        if (player.tickCount % 20 == 0) {
            if (hasFood || sol_valheim$food_data.hasActiveFood()) {
                sol_valheim$trackData();
                sol_valheim$refreshFoodEffects(player);
            }
            sol_valheim$refreshFoodStats(player);
        }

        int regenInterval = Math.max(1, Math.round(5f * Math.max(0.1f, SOLValheim.Config.common.regenSpeedModifier)));
        long timeSinceHurt = level.getGameTime() - ((LivingEntityDamageAccessor) this).getLastDamageStamp();
        if (timeSinceHurt > Math.max(0, SOLValheim.Config.common.regenDelay) && player.tickCount % regenInterval == 0)
            player.heal(sol_valheim$food_data.getRegenSpeed() / 20f);
    }

    @Unique
    private void sol_valheim$refreshFoodStats(Player player) {
        float maxhp = Math.min(
                Math.max(1, SOLValheim.Config.common.maxFoodHealth) * 2f,
                Math.max(1, SOLValheim.Config.common.startingHealth) * 2f + sol_valheim$food_data.getTotalFoodNutrition()
        );
        maxhp = Math.max(2f, Math.round(maxhp / 2f) * 2f);

        player.getFoodData().setSaturation(0);

        var maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && Math.abs(maxHealth.getBaseValue() - maxhp) > 0.001)
            maxHealth.setBaseValue(maxhp);

        var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null)
            return;

        var modifier = SOLValheim.getSpeedBuffModifier();
        var current = movement.getModifier(modifier.getId());
        boolean shouldHave = SOLValheim.Config.common.speedBoost > 0.01f && maxhp >= 20;
        if (shouldHave && current == null)
            movement.addTransientModifier(modifier);
        else if (!shouldHave && current != null)
            movement.removeModifier(modifier.getId());
    }

    @Unique
    private void sol_valheim$refreshFoodEffects(Player player) {
        for (var eaten : sol_valheim$food_data.ItemEntries) {
            var config = ModConfig.getFoodConfig(eaten.item);
            if (config != null)
                sol_valheim$applyFoodEffectsToPlayer(player, eaten, config);
        }

        if (sol_valheim$food_data.DrinkSlot != null) {
            var config = ModConfig.getFoodConfig(sol_valheim$food_data.DrinkSlot.item);
            if (config != null)
                sol_valheim$applyFoodEffectsToPlayer(player, sol_valheim$food_data.DrinkSlot, config);
        }
    }

    @Unique
    private void sol_valheim$applyFoodEffectsToPlayer(Player player,
                                                      ValheimFoodData.EatenFoodItem eatenData,
                                                      ModConfig.Common.FoodConfig config)
    {
        int totalTime = config.getTime();
        int ticksLeft = eatenData.ticksLeft;

        for (var effectCfg : config.extraEffects) {
            var mobEffect = effectCfg.getEffect();
            if (mobEffect == null)
                continue;

            float fractionActive = Math.max(0f, Math.min(1f, effectCfg.duration));
            int threshold = (int) (totalTime * fractionActive);
            if (ticksLeft < totalTime - threshold)
                continue;

            int amplifier = Math.max(effectCfg.amplifier - 1, 0);
            MobEffectInstance current = player.getEffect(mobEffect);
            if (current == null || current.getAmplifier() != amplifier || current.getDuration() < 30) {
                player.addEffect(new MobEffectInstance(
                        mobEffect,
                        80,
                        amplifier,
                        false,
                        false
                ));
            }
        }
    }

    @Inject(at = {@At("HEAD")}, method = {"canEat(Z)Z"}, cancellable = true)
    private void onCanConsume(boolean ignorehunger, CallbackInfoReturnable<Boolean> info) {
        info.setReturnValue(true);
        info.cancel();
    }

    @Inject(at = {@At("HEAD")}, method = {"hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"}, cancellable = true)
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> info) {
        #if PRE_CURRENT_MC_1_19_2
        if (source == DamageSource.STARVE) {
        #elif POST_CURRENT_MC_1_20_1
        if (source == this.damageSources().starve()) {
        #endif
            info.setReturnValue(Boolean.FALSE);
            info.cancel();
        }
    }

    @Inject(at = {@At("TAIL")}, method = {"addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V"})
    private void onWriteCustomData(CompoundTag nbt, CallbackInfo info) {
        nbt.put("sol_food_data", sol_valheim$food_data.save(new CompoundTag()));
    }

    @Inject(at = {@At("TAIL")}, method = {"readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V"})
    private void onReadCustomData(CompoundTag nbt, CallbackInfo info) {
        if (nbt.contains("sol_food_data"))
            sol_valheim$food_data = ValheimFoodData.read(nbt.getCompound("sol_food_data"));
        else
            sol_valheim$food_data = new ValheimFoodData();

        sol_valheim$trackData();
    }

    @Unique
    private void sol_valheim$trackData() {
        #if PRE_CURRENT_MC_1_19_2
        var level = this.level;
        #elif POST_CURRENT_MC_1_20_1
        var level = this.level();
        #endif
        if (!level.isClientSide && (Object) this instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
            SOLValheim.syncFoodData(serverPlayer, sol_valheim$food_data);
    }
}
