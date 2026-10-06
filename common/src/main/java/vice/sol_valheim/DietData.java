package vice.sol_valheim;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;

public final class DietData {
    public static final int DATA_VERSION = 2;

    private final EnumMap<DietCategory, Integer> points = new EnumMap<>(DietCategory.class);
    private final EnumMap<DietCategory, Integer> decayTicks = new EnumMap<>(DietCategory.class);
    private int globalTimerTicks;
    private long lastHandledTick = Long.MIN_VALUE;
    private String lastHandledItem = "";

    public int getPoints(DietCategory category) {
        return Math.max(0, points.getOrDefault(category, 0));
    }

    public void setPoints(DietCategory category, int value) {
        points.put(category, Math.max(0, Math.min(DietConfig.maxPoints(), value)));
    }

    public int getDecayTicks(DietCategory category) {
        return Math.max(0, decayTicks.getOrDefault(category, 0));
    }

    public void setDecayTicks(DietCategory category, int value) {
        decayTicks.put(category, Math.max(0, value));
    }

    public int getGlobalTimerTicks() {
        return Math.max(0, globalTimerTicks);
    }

    public void resetGlobalTimer() {
        globalTimerTicks = 0;
    }

    public void incrementGlobalTimer() {
        if (hasAny())
            globalTimerTicks++;
        else
            globalTimerTicks = 0;
    }

    public boolean hasAny() {
        for (DietCategory category : DietCategory.values()) {
            if (getPoints(category) > 0)
                return true;
        }
        return false;
    }

    public void clear() {
        points.clear();
        decayTicks.clear();
        globalTimerTicks = 0;
    }

    public boolean markConsumption(long gameTime, String itemId) {
        if (gameTime == lastHandledTick && itemId != null && itemId.equals(lastHandledItem))
            return false;

        lastHandledTick = gameTime;
        lastHandledItem = itemId == null ? "" : itemId;
        return true;
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putInt("data_version", DATA_VERSION);
        for (DietCategory category : DietCategory.values()) {
            tag.putInt(category.id + "_points", getPoints(category));
            tag.putInt(category.id + "_decay", getDecayTicks(category));
        }
        tag.putInt("global_timer", getGlobalTimerTicks());
        return tag;
    }

    public static DietData read(CompoundTag tag) {
        DietData data = new DietData();
        // Versions 0/1 use the same point/timer keys and are migrated simply by
        // reading them here and writing DATA_VERSION on the next player save.
        int version = tag.getInt("data_version");
        for (DietCategory category : DietCategory.values()) {
            data.setPoints(category, tag.getInt(category.id + "_points"));
            data.setDecayTicks(category, tag.getInt(category.id + "_decay"));
        }
        data.globalTimerTicks = Math.max(0, tag.getInt("global_timer"));
        return data;
    }
}
