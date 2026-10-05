package vice.sol_valheim.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vice.sol_valheim.DietSystem;
import vice.sol_valheim.SOLValheim;
import vice.sol_valheim.accessors.DietDataAccessor;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

@Mixin(CakeBlock.class)
public class CakeBlockMixin
{
    @Inject(at = @At("HEAD"), method = "eat(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/InteractionResult;", cancellable = true)
    private static void sol_valheim$canEatCake(LevelAccessor level, BlockPos pos, BlockState state, Player player, CallbackInfoReturnable<InteractionResult> cir)
    {
        var foodData = ((PlayerEntityMixinDataAccessor) player).sol_valheim$getFoodData();
        if (!foodData.canEat(Items.CAKE.getDefaultInstance())) {
            cir.setReturnValue(InteractionResult.FAIL);
            cir.cancel();
        }
    }

    @Inject(at = @At("RETURN"), method = "eat(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/InteractionResult;")
    private static void sol_valheim$recordCake(LevelAccessor level, BlockPos pos, BlockState state, Player player, CallbackInfoReturnable<InteractionResult> cir)
    {
        if (level.isClientSide() || cir.getReturnValue() == null || !cir.getReturnValue().consumesAction())
            return;

        var cake = Items.CAKE.getDefaultInstance();
        var foodData = ((PlayerEntityMixinDataAccessor) player).sol_valheim$getFoodData();
        foodData.eatItem(cake);

        if (player instanceof ServerPlayer serverPlayer) {
            SOLValheim.syncFoodData(serverPlayer, foodData);
            DietSystem.onConsumed(serverPlayer, cake, ((DietDataAccessor) player).sol_valheim$getDietData());
        }
    }
}
