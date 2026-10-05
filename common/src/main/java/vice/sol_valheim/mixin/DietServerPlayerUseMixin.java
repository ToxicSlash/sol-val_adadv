package vice.sol_valheim.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vice.sol_valheim.DietSystem;
import vice.sol_valheim.accessors.DietDataAccessor;

@Mixin(ServerPlayer.class)
public class DietServerPlayerUseMixin {
    @Inject(at = @At("HEAD"), method = "completeUsingItem")
    private void sol_valheim$onDietDrinkConsumed(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        var stack = player.getUseItem();
        if (stack.isEmpty() || !player.isUsingItem() || stack.getUseAnimation() != UseAnim.DRINK)
            return;

        var copy = stack.copy();
        copy.setCount(1);
        DietSystem.onConsumed(player, copy, ((DietDataAccessor) player).sol_valheim$getDietData());
    }
}
