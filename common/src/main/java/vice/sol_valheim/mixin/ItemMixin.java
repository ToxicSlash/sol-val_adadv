package vice.sol_valheim.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vice.sol_valheim.ConsumptionSystem;

@Mixin({Item.class})
public class ItemMixin
{
    @Inject(at= {@At("HEAD")}, method = {"use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;"}, cancellable = true)
    private void onCanConsume(Level level, Player player, InteractionHand usedHand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> info)
    {
        var item = (Item) (Object) this;
        if (!item.isEdible())
            return;

        ItemStack itemStack = player.getItemInHand(usedHand);
        if (ConsumptionSystem.canConsume(player, itemStack)) {
            player.startUsingItem(usedHand);
            info.setReturnValue(InteractionResultHolder.consume(itemStack));
        } else {
            info.setReturnValue(InteractionResultHolder.fail(itemStack));
        }
        info.cancel();
    }
}
