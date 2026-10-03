package vice.sol_valheim;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

public final class FoodSync {
    public static final ResourceLocation CHANNEL = new ResourceLocation(SOLValheim.MOD_ID, "food_sync");

    private FoodSync() {
    }

    public static void initServer() {
        SOLValheim.setFoodSyncSender(FoodSync::sendToPlayer);
    }

    public static void initClient() {
        NetworkManager.registerReceiver(NetworkManager.s2c(), CHANNEL, (buf, context) -> {
            CompoundTag tag = buf.readNbt();
            if (tag == null) {
                return;
            }

            ValheimFoodData data = ValheimFoodData.read(tag);
            context.queue(() -> {
                var player = context.getPlayer();
                if (player == null) {
                    return;
                }

                ((PlayerEntityMixinDataAccessor) player).sol_valheim$setFoodDataFromServer(data);

                // A use may already have started from client prediction. Stop
                // its animation as soon as authoritative stomach data rejects it.
                if (player.isUsingItem() && !data.canEat(player.getUseItem())) {
                    player.stopUsingItem();
                }
            });
        });
    }

    private static void sendToPlayer(ServerPlayer player, ValheimFoodData foodData) {
        if (player.connection == null) {
            return;
        }

        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeNbt(foodData.save(new CompoundTag()));
        NetworkManager.sendToPlayer(player, CHANNEL, buf);
    }
}
