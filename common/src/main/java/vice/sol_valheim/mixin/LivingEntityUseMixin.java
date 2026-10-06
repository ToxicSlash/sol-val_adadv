package vice.sol_valheim.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vice.sol_valheim.ConsumptionSystem;
import vice.sol_valheim.ModConfig;

@Mixin(LivingEntity.class)
public class LivingEntityUseMixin
{
    @Inject(at = @At("HEAD"), method = "startUsingItem(Lnet/minecraft/world/InteractionHand;)V", cancellable = true)
    private void sol_valheim$blockUnavailableFoodUse(InteractionHand hand, CallbackInfo info)
    {
        if (!((Object) this instanceof Player player))
            return;

        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || ModConfig.getFoodConfig(stack) == null)
            return;

        // Final animation gate for ordinary foods and modded drink items.
        if (!ConsumptionSystem.canConsume(player, stack))
            info.cancel();
    }
}
