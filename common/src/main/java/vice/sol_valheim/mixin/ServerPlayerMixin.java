package vice.sol_valheim.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vice.sol_valheim.ConsumptionSystem;
import vice.sol_valheim.ModConfig;
import vice.sol_valheim.SOLValheim;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin
{
    @Unique
    private ItemStack sol_valheim$pendingDrink = ItemStack.EMPTY;

    @Inject(at = @At("HEAD"), method = {"completeUsingItem"}, cancellable = true)
    private void sol_valheim$validateTrackedDrink(CallbackInfo ci)
    {
        sol_valheim$pendingDrink = ItemStack.EMPTY;

        var player = (ServerPlayer) (Object) this;
        var useItem = player.getUseItem();
        if (useItem.isEmpty() || !player.isUsingItem() || useItem.getUseAnimation() != UseAnim.DRINK)
            return;

        // Ignore drink-animation items that SOL does not treat as food.
        if (ModConfig.getFoodConfig(useItem) == null)
            return;

        if (!ConsumptionSystem.canConsume(player, useItem)) {
            player.stopUsingItem();
            SOLValheim.syncFoodData(player, ((PlayerEntityMixinDataAccessor) player).sol_valheim$getFoodData());
            ci.cancel();
            return;
        }

        sol_valheim$pendingDrink = useItem.copy();
        sol_valheim$pendingDrink.setCount(1);
    }

    @Inject(at = @At("TAIL"), method = {"completeUsingItem"})
    private void sol_valheim$commitTrackedDrink(CallbackInfo ci)
    {
        if (sol_valheim$pendingDrink.isEmpty())
            return;

        var player = (ServerPlayer) (Object) this;
        var consumed = sol_valheim$pendingDrink;
        sol_valheim$pendingDrink = ItemStack.EMPTY;

        // Edible drinks may already have committed through Player.eat. The
        // shared service re-validates the stomach, making this safe and idempotent.
        ConsumptionSystem.commit(player, consumed);
    }
}
