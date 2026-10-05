package vice.sol_valheim;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DietSync {
    public static final ResourceLocation CHANNEL = new ResourceLocation(SOLValheim.MOD_ID, "diet_config");
    private static final int MAX_CONFIG_BYTES = 1_048_576;
    private static final Logger LOGGER = LoggerFactory.getLogger("sol_valheim/diet_sync");

    private DietSync() {
    }

    public static void initClient() {
        NetworkManager.registerReceiver(NetworkManager.s2c(), CHANNEL, (buf, context) -> {
            String json = buf.readUtf(MAX_CONFIG_BYTES);
            context.queue(() -> DietConfig.applySynced(json));
        });
    }

    public static void sendToPlayer(ServerPlayer player) {
        if (player == null || player.connection == null)
            return;

        String json = DietConfig.toJson();
        if (json.length() > MAX_CONFIG_BYTES) {
            LOGGER.error("Diet config is too large to synchronize ({} characters); skipping sync for {}", json.length(), player.getScoreboardName());
            return;
        }

        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(json, MAX_CONFIG_BYTES);
        NetworkManager.sendToPlayer(player, CHANNEL, buf);
    }
}
