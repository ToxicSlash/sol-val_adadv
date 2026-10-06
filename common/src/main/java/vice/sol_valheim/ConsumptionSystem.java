package vice.sol_valheim;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import vice.sol_valheim.accessors.DietDataAccessor;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

public final class ConsumptionSystem {
    private ConsumptionSystem() {
    }

    public static boolean canConsume(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty())
            return false;
        if (stack.is(Items.ROTTEN_FLESH))
            return true;
        if (ModConfig.getFoodConfig(stack) == null)
            return true;

        var foodData = ((PlayerEntityMixinDataAccessor) player).sol_valheim$getFoodData();
        return foodData == null || foodData.canEat(stack);
    }

    public static boolean commit(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty())
            return false;

        ItemStack consumed = stack.copy();
        consumed.setCount(1);

        var foodData = ((PlayerEntityMixinDataAccessor) player).sol_valheim$getFoodData();
        var dietData = ((DietDataAccessor) player).sol_valheim$getDietData();

        if (consumed.is(Items.ROTTEN_FLESH)) {
            boolean hadFood = foodData != null && foodData.hasActiveFood();
            boolean hadDiet = dietData != null && dietData.hasAny();
            if (foodData != null)
                foodData.clear();
            DietSystem.purge(player, dietData, hadFood || hadDiet);
            if (foodData != null)
                SOLValheim.syncFoodData(player, foodData);
            return hadFood || hadDiet;
        }

        if (ModConfig.getFoodConfig(consumed) == null)
            return false;
        if (foodData == null || !foodData.canEat(consumed))
            return false;

        foodData.eatItem(consumed);
        DietSystem.onConsumed(player, consumed, dietData);
        SOLValheim.syncFoodData(player, foodData);
        return true;
    }

    public static boolean clearStomach(ServerPlayer player) {
        if (player == null)
            return false;
        var foodData = ((PlayerEntityMixinDataAccessor) player).sol_valheim$getFoodData();
        if (foodData == null || !foodData.hasActiveFood())
            return false;
        foodData.clear();
        SOLValheim.syncFoodData(player, foodData);
        return true;
    }

    public static boolean clearDiet(ServerPlayer player) {
        if (player == null)
            return false;
        var data = ((DietDataAccessor) player).sol_valheim$getDietData();
        if (data == null || !data.hasAny())
            return false;
        DietSystem.purge(player, data, false);
        return true;
    }
}
