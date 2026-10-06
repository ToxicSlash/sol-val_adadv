package vice.sol_valheim;

import net.minecraft.resources.ResourceLocation;

public final class FoodBalanceRules {
    private FoodBalanceRules() {
    }

    public static void applyNamespaceRules(String itemId, ModConfig.Common.FoodConfig config) {
        if (itemId == null || config == null || SOLValheim.Config == null || SOLValheim.Config.common.namespaceFoodRules == null)
            return;

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null)
            return;

        String namespace = id.getNamespace();
        for (var rule : SOLValheim.Config.common.namespaceFoodRules) {
            if (rule == null || rule.namespace == null || !namespace.equals(rule.namespace.trim()))
                continue;

            config.nutrition = Math.max(1, Math.round(config.nutrition * safeMultiplier(rule.nutritionMultiplier)));
            config.saturationModifier = Math.max(0.01f, config.saturationModifier * safeMultiplier(rule.saturationMultiplier));
            config.healthRegenModifier = Math.max(0f, config.healthRegenModifier * safeMultiplier(rule.healthRegenMultiplier));
            config.healthMultiplier = Math.max(0f, config.healthMultiplier * safeMultiplier(rule.healthMultiplier));
            config.durationMultiplier = Math.max(0.01f, config.durationMultiplier * safeMultiplier(rule.durationMultiplier));
        }
    }

    private static float safeMultiplier(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value))
            return 1f;
        return Math.max(0f, value);
    }
}
