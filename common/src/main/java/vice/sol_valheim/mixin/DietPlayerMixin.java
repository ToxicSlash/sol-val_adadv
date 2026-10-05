package vice.sol_valheim.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vice.sol_valheim.DietData;
import vice.sol_valheim.DietSystem;
import vice.sol_valheim.accessors.DietDataAccessor;

@Mixin(Player.class)
public class DietPlayerMixin implements DietDataAccessor {
    @Unique
    private DietData sol_valheim$dietData = new DietData();

    @Unique
    private ItemStack sol_valheim$pendingDietFood = ItemStack.EMPTY;

    @Override
    @Unique
    public DietData sol_valheim$getDietData() {
        return sol_valheim$dietData;
    }

    @Inject(at = @At("HEAD"), method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;")
    private void sol_valheim$captureDietFood(Level level, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        sol_valheim$pendingDietFood = stack.copy();
        sol_valheim$pendingDietFood.setCount(1);
    }

    @Inject(at = @At("TAIL"), method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;")
    private void sol_valheim$onDietFoodEaten(Level level, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        Player player = (Player) (Object) this;
        if (player instanceof ServerPlayer serverPlayer && !sol_valheim$pendingDietFood.isEmpty())
            DietSystem.onConsumed(serverPlayer, sol_valheim$pendingDietFood, sol_valheim$dietData);
        sol_valheim$pendingDietFood = ItemStack.EMPTY;
    }

    @Inject(at = @At("TAIL"), method = "tick")
    private void sol_valheim$tickDiet(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        if (serverPlayer.isDeadOrDying()) {
            DietSystem.clearOnDeath(serverPlayer, sol_valheim$dietData);
            return;
        }

        DietSystem.tick(serverPlayer, sol_valheim$dietData);
    }

    @Inject(at = @At("TAIL"), method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V")
    private void sol_valheim$saveDietData(CompoundTag tag, CallbackInfo ci) {
        tag.put("sol_diet_data", sol_valheim$dietData.save(new CompoundTag()));
    }

    @Inject(at = @At("TAIL"), method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V")
    private void sol_valheim$loadDietData(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("sol_diet_data"))
            sol_valheim$dietData = DietData.read(tag.getCompound("sol_diet_data"));
        else
            sol_valheim$dietData = new DietData();
    }
}
