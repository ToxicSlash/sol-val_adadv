package vice.sol_valheim;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.effect.MobEffect;

public final class DietEffects {
    public static RegistrySupplier<? extends MobEffect> BUILDERS_BLESSING;
    public static RegistrySupplier<? extends MobEffect> GREATER_BUILDERS_BLESSING;
    public static RegistrySupplier<? extends MobEffect> MINERS_BLESSING;
    public static RegistrySupplier<? extends MobEffect> GREATER_MINERS_BLESSING;
    public static RegistrySupplier<? extends MobEffect> WARRIORS_BLESSING;
    public static RegistrySupplier<? extends MobEffect> GREATER_WARRIORS_BLESSING;
    public static RegistrySupplier<? extends MobEffect> WAYFARERS_BLESSING;
    public static RegistrySupplier<? extends MobEffect> GREATER_WAYFARERS_BLESSING;

    private static boolean registered;

    private DietEffects() {
    }

    public static void register() {
        if (registered)
            return;

        // These effects are visible/status markers. Their attribute modifiers
        // are applied by DietSystem from the startup config at runtime, which
        // also lets optional-mod attributes resolve safely after registration.
        BUILDERS_BLESSING = SOLValheim.MOB_EFFECTS.register("builders_blessing", DietBlessingEffect::new);
        GREATER_BUILDERS_BLESSING = SOLValheim.MOB_EFFECTS.register("greater_builders_blessing", DietBlessingEffect::new);
        MINERS_BLESSING = SOLValheim.MOB_EFFECTS.register("miners_blessing", DietBlessingEffect::new);
        GREATER_MINERS_BLESSING = SOLValheim.MOB_EFFECTS.register("greater_miners_blessing", DietBlessingEffect::new);
        WARRIORS_BLESSING = SOLValheim.MOB_EFFECTS.register("warriors_blessing", DietBlessingEffect::new);
        GREATER_WARRIORS_BLESSING = SOLValheim.MOB_EFFECTS.register("greater_warriors_blessing", DietBlessingEffect::new);
        WAYFARERS_BLESSING = SOLValheim.MOB_EFFECTS.register("wayfarers_blessing", DietBlessingEffect::new);
        GREATER_WAYFARERS_BLESSING = SOLValheim.MOB_EFFECTS.register("greater_wayfarers_blessing", DietBlessingEffect::new);

        SOLValheim.MOB_EFFECTS.register();
        registered = true;
    }

    public static MobEffect primary(DietCategory category) {
        return switch (category) {
            case COMBAT -> WARRIORS_BLESSING.get();
            case BUILDING -> BUILDERS_BLESSING.get();
            case MINING -> MINERS_BLESSING.get();
            case ADVENTURING -> WAYFARERS_BLESSING.get();
        };
    }

    public static MobEffect bonus(DietCategory category) {
        return switch (category) {
            case COMBAT -> GREATER_WARRIORS_BLESSING.get();
            case BUILDING -> GREATER_BUILDERS_BLESSING.get();
            case MINING -> GREATER_MINERS_BLESSING.get();
            case ADVENTURING -> GREATER_WAYFARERS_BLESSING.get();
        };
    }
}
