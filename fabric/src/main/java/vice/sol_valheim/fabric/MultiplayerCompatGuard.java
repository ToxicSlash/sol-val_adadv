package vice.sol_valheim.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vice.sol_valheim.SOLValheim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

public final class MultiplayerCompatGuard {
    private static final Logger LOGGER = LoggerFactory.getLogger(MultiplayerCompatGuard.class);
    private static final ResourceLocation HANDSHAKE_CHANNEL = new ResourceLocation(SOLValheim.MOD_ID, "login_compat");
    private static final int PROTOCOL_VERSION = 2;
    private static final int MAX_MOD_COUNT = 4096;
    private static final int MAX_MOD_ID_LEN = 128;
    private static final int MAX_MOD_VERSION_LEN = 192;
    private static final int MAX_DISCONNECT_LEN = 220;
    private static final boolean ENFORCE_MISMATCH_KICK = false;
    private static volatile Map<String, String> cachedUniversalMods;

    private static final Set<String> SIDE_ONLY_ALLOWED_IDS = Set.of(
            "alternate-current",
            "elytranerf",
            "factions",
            "hearths",
            "lazy_ai_pixelindiedev",
            "multicount",
            "nobroken",
            "pebbles-crates",
            "placeholder-api",
            "servercore",
            "spark",
            "starlight",
            "threadtweak",
            "yawp"
    );
    private static final List<String> SIDE_ONLY_ALLOWED_PREFIXES = List.of(
            "adventure-platform-",
            "net_kyori_",
            "space_arim_"
    );

    private MultiplayerCompatGuard() {
    }

    public static void initServer() {
        ServerLoginNetworking.registerGlobalReceiver(HANDSHAKE_CHANNEL, (server, handler, understood, buf, synchronizer, responseSender) -> {
            if (!understood) {
                server.execute(() -> handler.disconnect(Component.literal("SOL mismatch: client does not support SOL compatibility handshake.")));
                return;
            }

            final int clientProtocol;
            final Map<String, String> clientMods;
            try {
                clientProtocol = buf.readVarInt();
                clientMods = readModMap(buf);
            } catch (RuntimeException ex) {
                LOGGER.warn("[SOL compat] Invalid compatibility payload received during login", ex);
                server.execute(() -> handler.disconnect(Component.literal("SOL mismatch: invalid compatibility handshake payload.")));
                return;
            }

            Map<String, String> serverMods = collectUniversalMods();
            server.execute(() -> {
                if (clientProtocol != PROTOCOL_VERSION) {
                    handler.disconnect(Component.literal("SOL mismatch: incompatible SOL protocol version between client and server."));
                    return;
                }

                DiffResult diff = diffMods(serverMods, clientMods);
                if (!diff.isMatch()) {
                    LOGGER.warn("[SOL compat] Multiplayer mismatch detected during login.");
                    if (!diff.missingOnClient.isEmpty())
                        LOGGER.warn("[SOL compat] Missing on client: {}", String.join(", ", diff.missingOnClient));
                    if (!diff.missingOnServer.isEmpty())
                        LOGGER.warn("[SOL compat] Missing on server: {}", String.join(", ", diff.missingOnServer));
                    if (!diff.versionMismatch.isEmpty())
                        LOGGER.warn("[SOL compat] Version mismatch: {}", String.join(", ", diff.versionMismatch));

                    if (ENFORCE_MISMATCH_KICK)
                        handler.disconnect(Component.literal(summarizeDiffForDisconnect(diff)));
                }
            });
        });

        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            var buf = PacketByteBufs.create();
            buf.writeVarInt(PROTOCOL_VERSION);
            sender.sendPacket(HANDSHAKE_CHANNEL, buf);
        });
    }

    public static void initClient() {
        ClientLoginNetworking.registerGlobalReceiver(HANDSHAKE_CHANNEL, (client, handler, buf, listenerAdder) -> {
            buf.readVarInt();

            var response = PacketByteBufs.create();
            response.writeVarInt(PROTOCOL_VERSION);
            writeModMap(response, collectUniversalMods());
            return CompletableFuture.completedFuture(response);
        });
    }

    private static Map<String, String> collectUniversalMods() {
        Map<String, String> cached = cachedUniversalMods;
        if (cached != null)
            return cached;

        synchronized (MultiplayerCompatGuard.class) {
            if (cachedUniversalMods == null) {
                TreeMap<String, String> collected = FabricLoader.getInstance()
                        .getAllMods()
                        .stream()
                        .filter(container -> container.getMetadata().getEnvironment() == ModEnvironment.UNIVERSAL)
                        .sorted(Comparator.comparing(c -> c.getMetadata().getId()))
                        .collect(
                                TreeMap::new,
                                (map, container) -> map.put(container.getMetadata().getId(), container.getMetadata().getVersion().getFriendlyString()),
                                TreeMap::putAll
                        );
                cachedUniversalMods = Collections.unmodifiableMap(collected);
            }
            return cachedUniversalMods;
        }
    }

    private static void writeModMap(net.minecraft.network.FriendlyByteBuf buf, Map<String, String> mods) {
        if (mods.size() > MAX_MOD_COUNT)
            throw new IllegalStateException("Too many mods for SOL compatibility handshake: " + mods.size());

        buf.writeVarInt(mods.size());
        for (var entry : mods.entrySet()) {
            buf.writeUtf(entry.getKey(), MAX_MOD_ID_LEN);
            buf.writeUtf(entry.getValue(), MAX_MOD_VERSION_LEN);
        }
    }

    private static Map<String, String> readModMap(net.minecraft.network.FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_MOD_COUNT)
            throw new IllegalArgumentException("Invalid mod count in SOL compatibility payload: " + count);

        Map<String, String> mods = new LinkedHashMap<>(Math.min(count, MAX_MOD_COUNT));
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(MAX_MOD_ID_LEN);
            String version = buf.readUtf(MAX_MOD_VERSION_LEN);
            mods.put(id, version);
        }
        return mods;
    }

    private static DiffResult diffMods(Map<String, String> serverMods, Map<String, String> clientMods) {
        List<String> missingOnClient = new ArrayList<>();
        List<String> missingOnServer = new ArrayList<>();
        List<String> versionMismatch = new ArrayList<>();

        for (var entry : serverMods.entrySet()) {
            String id = entry.getKey();
            String serverVersion = entry.getValue();
            String clientVersion = clientMods.get(id);
            if (clientVersion == null) {
                if (!isAllowedSideOnly(id))
                    missingOnClient.add(id + "@" + serverVersion);
            } else if (!serverVersion.equals(clientVersion)) {
                versionMismatch.add(id + " server=" + serverVersion + " client=" + clientVersion);
            }
        }

        for (var entry : clientMods.entrySet()) {
            if (!serverMods.containsKey(entry.getKey()) && !isAllowedSideOnly(entry.getKey()))
                missingOnServer.add(entry.getKey() + "@" + entry.getValue());
        }

        return new DiffResult(missingOnClient, missingOnServer, versionMismatch);
    }

    private static String summarizeDiffForDisconnect(DiffResult diff) {
        StringBuilder sb = new StringBuilder("SOL mismatch: ");
        appendCategory(sb, "missing on client", diff.missingOnClient);
        appendCategory(sb, "missing on server", diff.missingOnServer);
        appendCategory(sb, "version mismatch", diff.versionMismatch);
        sb.append(" Check server log for full list.");

        String message = sb.toString();
        if (message.length() <= MAX_DISCONNECT_LEN)
            return message;

        return "SOL mismatch: mod list differs between client and server. Check server log for exact missing/version-mismatch mods.";
    }

    private static boolean isAllowedSideOnly(String modId) {
        if (SIDE_ONLY_ALLOWED_IDS.contains(modId))
            return true;
        for (String prefix : SIDE_ONLY_ALLOWED_PREFIXES) {
            if (modId.startsWith(prefix))
                return true;
        }
        return false;
    }

    private static void appendCategory(StringBuilder sb, String name, List<String> values) {
        if (values.isEmpty())
            return;
        if (sb.length() > "SOL mismatch: ".length())
            sb.append("; ");
        int show = Math.min(3, values.size());
        sb.append(name).append(": ").append(String.join(", ", values.subList(0, show)));
        if (values.size() > show)
            sb.append(", ... +").append(values.size() - show);
    }

    private record DiffResult(List<String> missingOnClient, List<String> missingOnServer, List<String> versionMismatch) {
        private boolean isMatch() {
            return missingOnClient.isEmpty() && missingOnServer.isEmpty() && versionMismatch.isEmpty();
        }
    }
}
