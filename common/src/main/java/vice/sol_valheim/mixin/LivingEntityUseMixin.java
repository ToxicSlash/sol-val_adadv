package vice.sol_valheim.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vice.sol_valheim.ModConfig;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

@Mixin(LivingEntity.class)
public class LivingEntityUseMixin
{
    @Inject(at = @At("HEAD"), method = "startUsingItem(Lnet/minecraft/world/InteractionHand;)V", cancellable = true)
    private void sol_valheim$blockUnavailableFoodUse(InteractionHand hand, CallbackInfo info)
    {
        if (!((Object) this instanceof Player player))
            return;

        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || stack.is(Items.ROTTEN_FLESH))
            return;

        // Covers normal edible items and drink-animation items. Some modded
        // drinks bypass Item.use, so startUsingItem is the final animation gate.
        if (ModConfig.getFoodConfig(stack) == null)
            return;

        var foodData = ((PlayerEntityMixinDataAccessor) player).sol_valheim$getFoodData();
        if (foodData != null && !foodData.canEat(stack))
            info.cancel();
    }
}
