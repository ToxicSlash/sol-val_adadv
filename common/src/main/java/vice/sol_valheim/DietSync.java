package vice.sol_valheim;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class DietSync {
    public static final ResourceLocation CHANNEL = new ResourceLocation(SOLValheim.MOD_ID, "diet_config");

    private DietSync() {
    }

    public static void initClient() {
        NetworkManager.registerReceiver(NetworkManager.s2c(), CHANNEL, (buf, context) -> {
            String json = buf.readUtf(32767);
            context.queue(() -> DietConfig.applySynced(json));
        });
    }

    public static void sendToPlayer(ServerPlayer player) {
        if (player == null || player.connection == null)
            return;

        String json = DietConfig.toJson();
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(json, 32767);
        NetworkManager.sendToPlayer(player, CHANNEL, buf);
    }
}
