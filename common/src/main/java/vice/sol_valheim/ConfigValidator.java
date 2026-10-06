package vice.sol_valheim;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

#if PRE_CURRENT_MC_1_19_2
import net.minecraft.core.Registry;
#elif POST_CURRENT_MC_1_20_1
import net.minecraft.core.registries.BuiltInRegistries;
#endif

import java.util.HashSet;
import java.util.Set;

public final class ConfigValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger("sol_valheim/config");

    private ConfigValidator() {
    }

    public static void validateStartup() {
        if (SOLValheim.Config == null || SOLValheim.Config.common == null)
            return;

        var common = SOLValheim.Config.common;
        if (common.maxSlots < 2 || common.maxSlots > 5)
            warn("maxSlots is {} but runtime supports 2-5; it will be clamped", common.maxSlots);
        if (common.eatAgainPercentage < 0f || common.eatAgainPercentage > 1f)
            warn("eatAgainPercentage is {} but should be between 0 and 1; it will be clamped", common.eatAgainPercentage);
        if (common.reEatMinimumSeconds < 0)
            warn("reEatMinimumSeconds is negative; runtime will treat it as 0");
        if (common.stomachSafetySyncSeconds < 0)
            warn("stomachSafetySyncSeconds is negative; runtime will treat it as disabled");
        if (common.defaultTimer <= 0)
            warn("defaultTimer should be greater than 0");
        if (common.minimumFoodDurationSeconds <= 0)
            warn("minimumFoodDurationSeconds should be greater than 0");

        validateDiet(common.dietSystem);
        validateNamespaceRules(common.namespaceFoodRules);
        validateOverrides(common.foodOverrides);
        validateLegacyFoodConfigs(common.foodConfigs);
    }

    private static void validateDiet(ModConfig.Common.DietSystemConfig diet) {
        if (diet == null) {
            warn("dietSystem config is missing; defaults may be required");
            return;
        }
        if (diet.maxPoints < 1)
            warn("dietSystem.maxPoints must be at least 1");
        if (diet.bonusAtPoints < 1 || diet.bonusAtPoints > Math.max(1, diet.maxPoints))
            warn("dietSystem.bonusAtPoints ({}) should be between 1 and maxPoints ({})", diet.bonusAtPoints, diet.maxPoints);
        if (diet.secondsPerPoint < 1)
            warn("dietSystem.secondsPerPoint should be at least 1");
        if (diet.baseSecondsAtOnePoint < 1)
            warn("dietSystem.baseSecondsAtOnePoint should be at least 1");
        if (diet.postEatDecayDelaySeconds < 0)
            warn("dietSystem.postEatDecayDelaySeconds is negative; runtime will treat it as 0");
    }

    private static void validateNamespaceRules(java.util.List<ModConfig.Common.NamespaceFoodRule> rules) {
        if (rules == null)
            return;
        Set<String> seen = new HashSet<>();
        for (var rule : rules) {
            if (rule == null)
                continue;
            String namespace = rule.namespace == null ? "" : rule.namespace.trim();
            if (namespace.isEmpty()) {
                warn("Ignoring a namespaceFoodRules entry with a blank namespace");
                continue;
            }
            if (!seen.add(namespace))
                warn("Multiple namespaceFoodRules entries target '{}'; their multipliers will stack", namespace);
            if (rule.nutritionMultiplier < 0 || rule.saturationMultiplier < 0 || rule.healthRegenMultiplier < 0
                    || rule.healthMultiplier < 0 || rule.durationMultiplier < 0)
                warn("Namespace food rule '{}' contains a negative multiplier; runtime will clamp it", namespace);
        }
    }

    private static void validateOverrides(java.util.List<ModConfig.Common.FoodOverride> overrides) {
        if (overrides == null)
            return;
        Set<String> seen = new HashSet<>();
        for (var override : overrides) {
            if (override == null || override.id == null || override.id.isBlank()) {
                warn("Ignoring a foodOverrides entry with a blank item id");
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(override.id.trim());
            if (id == null) {
                warn("Invalid food override item id '{}'", override.id);
                continue;
            }
            if (!seen.add(id.toString()))
                warn("Duplicate food override for '{}'; only the first matching entry is used", id);
            if (!itemExists(id))
                warn("Food override references missing item '{}'", id);
            if (override.reEatPercentage > 1f)
                warn("Food override '{}' has reEatPercentage {} above 1; runtime will clamp it", id, override.reEatPercentage);
        }
    }

    private static void validateLegacyFoodConfigs(java.util.Map<String, ModConfig.Common.FoodConfig> configs) {
        if (configs == null)
            return;
        for (var entry : configs.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) {
                warn("Invalid legacy foodConfigs item id '{}'", entry.getKey());
                continue;
            }
            if (!itemExists(id))
                warn("Legacy food config references missing item '{}'", id);
            var food = entry.getValue();
            if (food == null || food.extraEffects == null)
                continue;
            for (var effect : food.extraEffects) {
                if (effect == null || effect.ID == null || effect.ID.isBlank())
                    continue;
                ResourceLocation effectId = ResourceLocation.tryParse(effect.ID);
                if (effectId == null || effect.getEffect() == null)
                    warn("Food '{}' references missing or invalid mob effect '{}'", id, effect.ID);
            }
        }
    }

    private static boolean itemExists(ResourceLocation id) {
        #if PRE_CURRENT_MC_1_19_2
        return Registry.ITEM.containsKey(id);
        #elif POST_CURRENT_MC_1_20_1
        return BuiltInRegistries.ITEM.containsKey(id);
        #endif
    }

    private static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }
}
