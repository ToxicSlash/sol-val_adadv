package vice.sol_valheim.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import vice.sol_valheim.DietConfig;
import vice.sol_valheim.DietSync;
import vice.sol_valheim.DietSystem;
import vice.sol_valheim.accessors.DietDataAccessor;

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

            for (var player : server.getPlayerList().getPlayers()) {
                var data = ((DietDataAccessor) player).sol_valheim$getDietData();
                DietSystem.onConfigReload(player, data);
                DietSync.sendToPlayer(player);
            }
        });

        // This also covers first join. END_DATA_PACK_RELOAD explicitly resends
        // after re-reading the external config so ordering cannot leave clients
        // with the old tooltip map.
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> DietSync.sendToPlayer(player));
    }
}
