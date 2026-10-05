package vice.sol_valheim.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vice.sol_valheim.SOLValheim;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public class ServerLevelMixin
{
    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"), method = "tick")
    private void sol_valheim$advanceFoodDuringSleep(BooleanSupplier hasTimeLeft, CallbackInfo ci)
    {
        if (!SOLValheim.Config.common.passTicksDuringNight)
            return;

        var level = (ServerLevel) (Object) this;
        long dayTime = level.getLevelData().getDayTime();
        long nextDay = dayTime + 24000L;
        long newTime = nextDay - nextDay % 24000L;
        long passedTicks = Math.max(0, newTime - dayTime);
        if (passedTicks == 0)
            return;

        for (var player : level.players()) {
            var foodData = ((PlayerEntityMixinDataAccessor) player).sol_valheim$getFoodData();
            if (!foodData.hasActiveFood())
                continue;

            // Actually expire food during the skipped night instead of clamping
            // every entry to one minute remaining.
            foodData.advanceTicks(passedTicks);
            SOLValheim.syncFoodData(player, foodData);
        }
    }
}
