package com.example.solvalheimtickfix.compat;

import com.example.solvalheimtickfix.SolValheimTickFix;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class SolCompat {
    private static final String SOL_MOD_ID = "sol_valheim";

    private static volatile boolean initialized;
    private static volatile boolean enabled;
    private static volatile boolean warnedLookupFailure;

    private static Class<?> playerAccessorClass;
    private static Method getFoodDataMethod;
    private static Method foodTickMethod;
    private static Field itemEntriesField;
    private static Field drinkSlotField;
    private static Field ticksLeftField;

    private static final Map<ServerPlayerEntity, TickState> PLAYER_STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final boolean ENABLE_EXPLICIT_SYNC = false;

    private SolCompat() {
    }

    public static void bootstrap() {
        boolean installed = FabricLoader.getInstance().isModLoaded(SOL_MOD_ID);
        enabled = installed;
        initialized = false;

        if (installed) {
            SolValheimTickFix.LOGGER.info("[{}] SOL detected; compat will initialize on first player tick.", SolValheimTickFix.MOD_ID);
        } else {
            SolValheimTickFix.LOGGER.info("[{}] SOL not installed; compat disabled.", SolValheimTickFix.MOD_ID);
        }
    }

    public static void tickFood(ServerPlayerEntity player) {
        if (!enabled) {
            return;
        }

        if (!ensureInitialized()) {
            return;
        }

        try {
            Object manager = getFoodDataMethod.invoke(player);
            if (manager == null) {
                return;
            }

            if (!ensureManagerHandles(manager.getClass(), player.getClass())) {
                return;
            }

            long worldTick = player.getWorld().getTime();
            int signature = snapshotSignature(manager);
            boolean hasActiveFoods = hasActiveFoods(manager);

            TickState state = PLAYER_STATES.computeIfAbsent(player, ignored -> new TickState());
            boolean shouldManualTick = hasActiveFoods
                    && state.lastSeenTick == worldTick - 1
                    && state.hadActiveFoods
                    && state.lastSignature == signature;

            if (shouldManualTick) {
                foodTickMethod.invoke(manager);
                // Explicitly syncing SOL tracked data can conflict with some mixed client/server mod stacks.
                // Keep this off by default unless you have confirmed matching entity-data layouts on both sides.
                if (ENABLE_EXPLICIT_SYNC) {
                    Method syncDataMethod = findMethodInHierarchy(player.getClass(), "sol_valheim$trackData");
                    if (syncDataMethod != null) {
                        syncDataMethod.setAccessible(true);
                        syncDataMethod.invoke(player);
                    }
                }
                signature = snapshotSignature(manager);
                hasActiveFoods = hasActiveFoods(manager);
                SolValheimTickFix.LOGGER.debug("[{}] Applied manual SOL food tick for {}", SolValheimTickFix.MOD_ID, player.getEntityName());
            }

            state.lastSeenTick = worldTick;
            state.lastSignature = signature;
            state.hadActiveFoods = hasActiveFoods;
        } catch (Throwable t) {
            disableCompat("Failed while running SOL compat tick", t);
        }
    }

    private static synchronized boolean ensureInitialized() {
        if (!enabled) {
            return false;
        }
        if (initialized) {
            return true;
        }

        try {
            // TODO: If SOL updates package names, replace this class name after checking the decompiled SOL jar.
            playerAccessorClass = Class.forName("vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor");
            // TODO: If SOL renames accessor methods, replace this method name from the decompiled SOL jar.
            getFoodDataMethod = playerAccessorClass.getMethod("sol_valheim$getFoodData");

            initialized = true;
            SolValheimTickFix.LOGGER.info("[{}] SOL compat reflection handles initialized.", SolValheimTickFix.MOD_ID);
            return true;
        } catch (Throwable t) {
            disableCompat("Could not initialize SOL compat reflection handles", t);
            return false;
        }
    }

    private static synchronized boolean ensureManagerHandles(Class<?> managerClass, Class<?> playerClass) {
        if (foodTickMethod != null && itemEntriesField != null && ticksLeftField != null) {
            return true;
        }

        try {
            // TODO: If SOL renames ValheimFoodData#tick, update this from the decompiled SOL jar.
            foodTickMethod = managerClass.getMethod("tick");
            foodTickMethod.setAccessible(true);

            // TODO: If SOL renames these fields, update names from the decompiled SOL jar.
            itemEntriesField = managerClass.getField("ItemEntries");
            drinkSlotField = managerClass.getField("DrinkSlot");

            // TODO: If SOL renames EatenFoodItem#ticksLeft, update this from the decompiled SOL jar.
            Class<?> eatenFoodItemClass = Class.forName("vice.sol_valheim.ValheimFoodData$EatenFoodItem");
            ticksLeftField = eatenFoodItemClass.getField("ticksLeft");

            SolValheimTickFix.LOGGER.debug("[{}] SOL manager reflection handles resolved.", SolValheimTickFix.MOD_ID);
            return true;
        } catch (Throwable t) {
            disableCompat("Could not resolve SOL manager reflection handles", t);
            return false;
        }
    }

    private static Method findMethodInHierarchy(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) {
                    return method;
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static boolean hasActiveFoods(Object manager) throws IllegalAccessException {
        List<Object> items = (List<Object>) itemEntriesField.get(manager);
        if (items != null && !items.isEmpty()) {
            return true;
        }
        return drinkSlotField.get(manager) != null;
    }

    @SuppressWarnings("unchecked")
    private static int snapshotSignature(Object manager) throws IllegalAccessException {
        int hash = 17;
        List<Object> items = (List<Object>) itemEntriesField.get(manager);
        if (items != null) {
            hash = 31 * hash + items.size();
            for (Object item : items) {
                hash = 31 * hash + ticksLeftField.getInt(item);
            }
        }

        Object drink = drinkSlotField.get(manager);
        if (drink != null) {
            hash = 31 * hash + 1;
            hash = 31 * hash + ticksLeftField.getInt(drink);
        }
        return hash;
    }

    private static synchronized void disableCompat(String reason, Throwable error) {
        enabled = false;
        if (warnedLookupFailure) {
            return;
        }
        warnedLookupFailure = true;
        SolValheimTickFix.LOGGER.warn("[{}] {}. SOL compat is now disabled.", SolValheimTickFix.MOD_ID, reason, error);
    }

    private static final class TickState {
        private long lastSeenTick = Long.MIN_VALUE;
        private int lastSignature;
        private boolean hadActiveFoods;
    }
}
