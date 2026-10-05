package vice.sol_valheim;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public final class DietEffects {
    public static RegistrySupplier<MobEffect> BUILDERS_BLESSING;
    public static RegistrySupplier<MobEffect> GREATER_BUILDERS_BLESSING;
    public static RegistrySupplier<MobEffect> MINERS_BLESSING;
    public static RegistrySupplier<MobEffect> GREATER_MINERS_BLESSING;
    public static RegistrySupplier<MobEffect> WARRIORS_BLESSING;
    public static RegistrySupplier<MobEffect> GREATER_WARRIORS_BLESSING;
    public static RegistrySupplier<MobEffect> WAYFARERS_BLESSING;
    public static RegistrySupplier<MobEffect> GREATER_WAYFARERS_BLESSING;

    private static boolean registered;

    private DietEffects() {
    }

    public static void register() {
        if (registered)
            return;

        BUILDERS_BLESSING = SOLValheim.MOB_EFFECTS.register("builders_blessing", () ->
                new DietBlessingEffect()
                        .addOptionalAttribute("reach-entity-attributes:reach", "727937d9-7779-44c9-ae1d-3c38614b0575", 1.0, AttributeModifier.Operation.ADDITION));

        GREATER_BUILDERS_BLESSING = SOLValheim.MOB_EFFECTS.register("greater_builders_blessing", () ->
                new DietBlessingEffect()
                        .addOptionalAttribute("doublejumpattribute:doublejumpattribute", "e0d58ef2-5ecd-4c44-a41c-11635047eec1", 1.0, AttributeModifier.Operation.ADDITION)
                        .addOptionalAttribute("bettertrims:item_magnet", "de12a250-c5fb-4cf2-9425-2bb82b30974a", 1.0, AttributeModifier.Operation.ADDITION));

        MINERS_BLESSING = SOLValheim.MOB_EFFECTS.register("miners_blessing", () ->
                new DietBlessingEffect()
                        .addOptionalAttribute("zenith_attributes:mining_speed", "de07647d-4faa-4a95-b5f1-b1362712fb76", 0.05, AttributeModifier.Operation.ADDITION));

        GREATER_MINERS_BLESSING = SOLValheim.MOB_EFFECTS.register("greater_miners_blessing", () ->
                new DietBlessingEffect()
                        .addOptionalAttribute("bettertrims:fortune", "a2d9987e-1dd3-43be-8168-d1fede7b224d", 2.0, AttributeModifier.Operation.ADDITION)
                        .addOptionalAttribute("bettertrims:miners_rush", "1de98e54-2dd1-445c-8f72-33b278d9075b", 1.0, AttributeModifier.Operation.ADDITION));

        WARRIORS_BLESSING = SOLValheim.MOB_EFFECTS.register("warriors_blessing", () ->
                new DietBlessingEffect()
                        .addOptionalAttribute("minecraft:generic.attack_damage", "cb515903-6df7-4367-88f2-2b9e49bf78af", 3.0, AttributeModifier.Operation.ADDITION)
                        .addOptionalAttribute("minecraft:generic.attack_speed", "9ee97a37-9f0c-46db-8c9d-ea64b7fa4f7a", 0.05, AttributeModifier.Operation.ADDITION));

        GREATER_WARRIORS_BLESSING = SOLValheim.MOB_EFFECTS.register("greater_warriors_blessing", () ->
                new DietBlessingEffect()
                        .addOptionalAttribute("zenith_attributes:healing_received", "b5ac0264-ef05-4425-8767-25e8e9851721", 0.5, AttributeModifier.Operation.ADDITION)
                        .addOptionalAttribute("obscure_api:critical_hit", "8c2d890f-ffdb-40de-9a24-8840b4a58377", 0.05, AttributeModifier.Operation.ADDITION));

        // The supplied KubeJS files reference a primary Wayfarer's Blessing,
        // but do not define any attributes for it. Keep it as a visible marker
        // effect rather than inventing an unsupported bonus.
        WAYFARERS_BLESSING = SOLValheim.MOB_EFFECTS.register("wayfarers_blessing", DietBlessingEffect::new);

        GREATER_WAYFARERS_BLESSING = SOLValheim.MOB_EFFECTS.register("greater_wayfarers_blessing", () ->
                new DietBlessingEffect()
                        .addOptionalAttribute("zenith_attributes:dodge_chance", "c8e26a84-8132-42fb-b53f-421c43359b0e", 0.1, AttributeModifier.Operation.ADDITION)
                        .addOptionalAttribute("bettertrims:swim_speed", "e2e5dd84-1aba-4602-a30e-cf690dbef433", 0.25, AttributeModifier.Operation.ADDITION));

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
