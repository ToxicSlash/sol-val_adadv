package vice.sol_valheim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DietConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("sol_valheim/diets");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Platform.getConfigFolder().resolve(SOLValheim.MOD_ID).resolve("diets.json");

    private static volatile Data data = new Data();
    private static volatile Map<String, FoodEntry> foods = Collections.emptyMap();

    private DietConfig() {
    }

    public static synchronized boolean reload() {
        try {
            ensureConfigExists();
            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            Data parsed = GSON.fromJson(json, Data.class);
            install(parsed);
            LOGGER.info("Loaded {} diet food entries from {}", foods.size(), CONFIG_PATH);
            return true;
        } catch (Exception ex) {
            LOGGER.error("Failed to reload diet config from {}. Keeping the previous diet config.", CONFIG_PATH, ex);
            return false;
        }
    }

    public static synchronized boolean applySynced(String json) {
        try {
            install(GSON.fromJson(json, Data.class));
            return true;
        } catch (Exception ex) {
            LOGGER.error("Failed to apply synchronized diet config", ex);
            return false;
        }
    }

    public static String toJson() {
        return GSON.toJson(data);
    }

    public static FoodEntry get(String itemId) {
        return itemId == null ? null : foods.get(itemId);
    }

    public static int maxPoints() {
        return data.maxPoints;
    }

    public static int baseSecondsAtOnePoint() {
        return data.baseSecondsAtOnePoint;
    }

    public static int secondsPerPoint() {
        return data.secondsPerPoint;
    }

    public static int bonusAtPoints() {
        return data.bonusAtPoints;
    }

    public static boolean showParticles() {
        return data.showParticles;
    }

    public static boolean showActionbar() {
        return data.showActionbar;
    }

    public static Path path() {
        return CONFIG_PATH;
    }

    private static void ensureConfigExists() throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        if (Files.exists(CONFIG_PATH))
            return;

        try (InputStream in = DietConfig.class.getResourceAsStream("/sol_valheim/default_diets.json")) {
            if (in == null)
                throw new IOException("Bundled default_diets.json is missing");
            Files.copy(in, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void install(Data parsed) {
        if (parsed == null)
            throw new IllegalArgumentException("Diet config root cannot be null");

        parsed.maxPoints = Math.max(1, parsed.maxPoints);
        parsed.baseSecondsAtOnePoint = Math.max(1, parsed.baseSecondsAtOnePoint);
        parsed.secondsPerPoint = Math.max(1, parsed.secondsPerPoint);
        parsed.bonusAtPoints = Math.max(1, Math.min(parsed.maxPoints, parsed.bonusAtPoints));
        if (parsed.foods == null)
            parsed.foods = new ArrayList<>();

        Map<String, FoodEntry> next = new LinkedHashMap<>();
        for (FoodEntry entry : parsed.foods) {
            if (entry == null || entry.id == null || entry.id.isBlank())
                continue;

            DietCategory category = DietCategory.fromId(entry.diet);
            if (category == null) {
                LOGGER.warn("Ignoring diet entry {} because '{}' is not a valid diet", entry.id, entry.diet);
                continue;
            }

            entry.diet = category.id;
            entry.points = Math.max(1, entry.points);
            next.put(entry.id, entry);
        }

        data = parsed;
        foods = Collections.unmodifiableMap(next);
    }

    public static final class Data {
        public int maxPoints = 20;
        public int baseSecondsAtOnePoint = 60;
        public int secondsPerPoint = 30;
        public int bonusAtPoints = 8;
        public boolean showParticles = true;
        public boolean showActionbar = true;
        public List<FoodEntry> foods = new ArrayList<>();
    }

    public static final class FoodEntry {
        public String id;
        public String diet;
        public int points = 1;

        public DietCategory category() {
            return DietCategory.fromId(diet);
        }
    }
}
