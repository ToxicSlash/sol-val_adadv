package vice.sol_valheim.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import vice.sol_valheim.DietConfig;
import vice.sol_valheim.DietSync;
import vice.sol_valheim.DietSystem;
import vice.sol_valheim.SOLValheim;
import vice.sol_valheim.accessors.DietDataAccessor;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

public final class DietFabricHooks {
    private static boolean initialized;

    private DietFabricHooks() {
    }

    public static void init() {
        if (initialized)
            return;
        initialized = true;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("diet").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    DietSystem.sendSummary(player, ((DietDataAccessor) player).sol_valheim$getDietData());
                    return 1;
                }))
        );

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (!success || !DietConfig.reload())
                return;

            // Send only after the external file has been re-read; this avoids
            // relying on ordering between Fabric's reload/sync callbacks.
            for (var player : server.getPlayerList().getPlayers()) {
                var data = ((DietDataAccessor) player).sol_valheim$getDietData();
                DietSystem.onConfigReload(player, data);
                DietSync.sendToPlayer(player);
            }
        });

        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
            if (!joined)
                return;

            DietSync.sendToPlayer(player);
            SOLValheim.syncFoodData(player, ((PlayerEntityMixinDataAccessor) player).sol_valheim$getFoodData());
        });
    }
}
