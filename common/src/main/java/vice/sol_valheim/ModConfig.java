package vice.sol_valheim;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;

#if PRE_CURRENT_MC_1_19_2
import net.minecraft.core.Registry;
#elif POST_CURRENT_MC_1_20_1
import net.minecraft.core.registries.BuiltInRegistries;
#endif

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Config(name = SOLValheim.MOD_ID)
@Config.Gui.Background("minecraft:textures/block/stone.png")
public class ModConfig extends PartitioningSerializer.GlobalData {
    private static final Map<String, Common.FoodConfig> RUNTIME_FOOD_CACHE = new ConcurrentHashMap<>();

    public static void clearRuntimeCaches() {
        RUNTIME_FOOD_CACHE.clear();
    }

    public static Common.FoodConfig getFoodConfig(ItemStack stack) {
        if (stack == null || stack.isEmpty() || SOLValheim.Config == null)
            return null;

        var item = stack.getItem();
        var isDrink = stack.getUseAnimation() == UseAnim.DRINK;
        if (item != Items.CAKE && !item.isEdible() && !isDrink)
            return null;

        String registry = item.arch$registryName().toString();
        var cached = RUNTIME_FOOD_CACHE.get(registry);
        if (cached != null)
            return cached;

        Common.FoodConfig existing = SOLValheim.Config.common.foodConfigs.get(registry);
        Common.FoodConfig result = existing != null ? new Common.FoodConfig(existing) : generateFoodConfig(stack, registry, isDrink);
        if (result == null)
            return null;

        applyFoodOverride(registry, result);
        RUNTIME_FOOD_CACHE.put(registry, result);
        return result;
    }

    private static Common.FoodConfig generateFoodConfig(ItemStack stack, String registry, boolean isDrink) {
        FoodProperties food;
        if (stack.is(Items.CAKE)) {
            food = new FoodProperties.Builder().nutrition(10).saturationMod(0.7f).build();
        } else if (isDrink) {
            if (registry.contains("potion"))
                food = new FoodProperties.Builder().nutrition(4).saturationMod(0.75f).build();
            else if (registry.contains("milk"))
                food = new FoodProperties.Builder().nutrition(6).saturationMod(1f).build();
            else
                food = new FoodProperties.Builder().nutrition(2).saturationMod(0.5f).build();
        } else {
            food = SOLValheim.getter.get(stack);
            if (food == null)
                return null;
        }

        var generated = new Common.FoodConfig();
        generated.nutrition = food.getNutrition();
        generated.healthRegenModifier = 1f;
        generated.saturationModifier = food.getSaturationModifier();

        if (registry.startsWith("farmers")) {
            generated.nutrition = (int) (generated.nutrition * 1.25f);
            generated.saturationModifier *= 1.10f;
            generated.healthRegenModifier = 1.25f;
        }

        if (registry.equals("minecraft:golden_apple") || registry.equals("minecraft:enchanted_golden_apple")) {
            generated.nutrition = 10;
            generated.healthRegenModifier = 1.5f;
        }

        if (registry.equals("minecraft:beetroot_soup")) {
            var effectConfig = new Common.MobEffectConfig();
            effectConfig.ID = movementSpeedEffectId();
            generated.extraEffects.add(effectConfig);
        }

        return generated;
    }

    private static String movementSpeedEffectId() {
        #if PRE_CURRENT_MC_1_19_2
        return Registry.MOB_EFFECT.getKey(MobEffects.MOVEMENT_SPEED).toString();
        #elif POST_CURRENT_MC_1_20_1
        return BuiltInRegistries.MOB_EFFECT.getKey(MobEffects.MOVEMENT_SPEED).toString();
        #endif
    }

    private static void applyFoodOverride(String registry, Common.FoodConfig config) {
        if (SOLValheim.Config.common.foodOverrides == null)
            return;

        for (var override : SOLValheim.Config.common.foodOverrides) {
            if (override == null || override.id == null || !override.id.equals(registry))
                continue;

            if (override.nutrition >= 0)
                config.nutrition = override.nutrition;
            if (override.saturationModifier >= 0f)
                config.saturationModifier = override.saturationModifier;
            if (override.healthRegenModifier >= 0f)
                config.healthRegenModifier = override.healthRegenModifier;
            if (override.healthMultiplier >= 0f)
                config.healthMultiplier = override.healthMultiplier;
            if (override.durationMultiplier >= 0f)
                config.durationMultiplier = override.durationMultiplier;
            return;
        }
    }

    @ConfigEntry.Category("common")
    @ConfigEntry.Gui.TransitiveObject()
    public Common common = new Common();

    @ConfigEntry.Category("client")
    @ConfigEntry.Gui.TransitiveObject()
    public Client client = new Client();

    @Config(name = "common")
    public static final class Common implements ConfigData {
        @ConfigEntry.Gui.Tooltip() @Comment("Default time in seconds that food should last per saturation level")
        public int defaultTimer = 180;

        @ConfigEntry.Gui.Tooltip() @Comment("Minimum generated food duration in seconds")
        public int minimumFoodDurationSeconds = 300;

        @ConfigEntry.Gui.Tooltip() @Comment("Global multiplier for maximum-health gained from foods")
        public float foodHealthMultiplier = 1.0f;

        @ConfigEntry.Gui.Tooltip() @Comment("Global multiplier for food duration")
        public float foodDurationMultiplier = 1.0f;

        @ConfigEntry.Gui.Tooltip() @Comment("Speed at which regeneration should occur")
        public float regenSpeedModifier = 1f;

        @ConfigEntry.Gui.Tooltip() @Comment("Time in ticks that regeneration should wait after taking damage")
        public int regenDelay = 20 * 10;

        @ConfigEntry.Gui.Tooltip() @Comment("Time in seconds after spawning before sprinting is disabled")
        public int respawnGracePeriod = 60 * 5;

        @ConfigEntry.Gui.Tooltip() @Comment("Extra speed given when your hearts are full (0 to disable)")
        public float speedBoost = 0.20f;

        @ConfigEntry.Gui.Tooltip() @Comment("Maximum allowed hearts from food")
        public int maxFoodHealth = 100;

        @ConfigEntry.Gui.Tooltip() @Comment("Number of hearts to start with")
        public int startingHealth = 3;

        @ConfigEntry.Gui.Tooltip() @Comment("Number of food slots (range 2-5, default 3)")
        public int maxSlots = 3;

        @ConfigEntry.Gui.Tooltip() @Comment("Percentage remaining before the same food can be eaten again")
        public float eatAgainPercentage = 0.2F;

        @ConfigEntry.Gui.Tooltip() @Comment("Boost given to other foods when drinking")
        public float drinkSlotFoodEffectivenessBonus = 0.10F;

        @ConfigEntry.Gui.Tooltip() @Comment("Simulate food ticking down during night")
        public boolean passTicksDuringNight = true;

        @ConfigEntry.Gui.Tooltip() @Comment("Whether or not food tooltips should show extra effects")
        public boolean displayEffects = true;

        @ConfigEntry.Gui.TransitiveObject()
        @Comment("Diet point timing and presentation. These are startup settings; food IDs live in config/sol_valheim/diets.json.")
        public DietSystemConfig dietSystem = new DietSystemConfig();

        @ConfigEntry.Gui.TransitiveObject()
        @Comment("Diet blessing attribute balance. Changes are intended to apply after a restart.")
        public DietBlessingConfig dietBlessings = new DietBlessingConfig();

        @Comment("Per-food overrides applied after automatically generated values. Use -1 to keep the generated value.")
        public List<FoodOverride> foodOverrides = new ArrayList<>();

        @ConfigEntry.Gui.Tooltip(count = 5) @Comment("""
            Legacy full food overrides, kept for backwards compatibility.
            - nutrition: Affects Heart Gain & Health Regen
            - saturationModifier: Affects Food Duration
            - healthRegenModifier: Multiplies health regen speed
            - extraEffects: Extra effects provided by eating the food
        """)
        public Map<String, FoodConfig> foodConfigs = new HashMap<>();

        public static final class DietSystemConfig implements ConfigData {
            public int maxPoints = 20;
            public int baseSecondsAtOnePoint = 60;
            public int secondsPerPoint = 30;
            public int bonusAtPoints = 8;
            public boolean showParticles = true;
            public boolean showActionbar = true;
        }

        public static final class DietBlessingConfig implements ConfigData {
            public double buildersReach = 1.0;
            public double greaterBuildersDoubleJump = 1.0;
            public double greaterBuildersItemMagnet = 1.0;

            public double minersMiningSpeed = 0.05;
            public double greaterMinersFortune = 2.0;
            public double greaterMinersRush = 1.0;

            public double warriorsAttackDamage = 3.0;
            public double warriorsAttackSpeed = 0.05;
            public double greaterWarriorsHealingReceived = 0.5;
            public double greaterWarriorsCriticalHit = 0.05;

            @Comment("Wayfarer primary movement-speed bonus as a fraction. 0.05 = +5% base movement speed.")
            public double wayfarersMovementSpeed = 0.05;
            public double wayfarersLuck = 1.0;
            public double greaterWayfarersDodgeChance = 0.10;
            public double greaterWayfarersSwimSpeed = 0.25;
        }

        public static final class FoodOverride implements ConfigData {
            public String id = "minecraft:apple";
            public int nutrition = -1;
            public float saturationModifier = -1f;
            public float healthRegenModifier = -1f;
            public float healthMultiplier = -1f;
            public float durationMultiplier = -1f;
        }

        public static final class FoodConfig implements ConfigData {
            public int nutrition;
            public float saturationModifier = 1f;
            public float healthRegenModifier = 1f;
            public float healthMultiplier = 1f;
            public float durationMultiplier = 1f;
            public List<MobEffectConfig> extraEffects = new ArrayList<>();

            public FoodConfig() {
            }

            public FoodConfig(FoodConfig other) {
                this.nutrition = other.nutrition;
                this.saturationModifier = other.saturationModifier;
                this.healthRegenModifier = other.healthRegenModifier;
                this.healthMultiplier = other.healthMultiplier;
                this.durationMultiplier = other.durationMultiplier;
                this.extraEffects = new ArrayList<>(other.extraEffects);
            }

            public int getTime() {
                var common = SOLValheim.Config.common;
                double multiplier = Math.max(0.01, common.foodDurationMultiplier) * Math.max(0.01, durationMultiplier);
                int time = (int) Math.round(common.defaultTimer * 20.0 * saturationModifier * nutrition * multiplier);
                int minimum = Math.max(1, common.minimumFoodDurationSeconds) * 20;
                return Math.max(time, minimum);
            }

            public int getHearts() {
                var common = SOLValheim.Config.common;
                double value = nutrition * Math.max(0.0, common.foodHealthMultiplier) * Math.max(0.0, healthMultiplier);
                return Math.max((int) Math.round(value), 2);
            }

            public float getHealthRegen() {
                return Mth.clamp(nutrition * 0.10f * healthRegenModifier, 0.25f, 2f);
            }
        }

        public static final class MobEffectConfig implements ConfigData {
            @ConfigEntry.Gui.Tooltip() @Comment("Mob Effect ID")
            public String ID;

            @ConfigEntry.Gui.Tooltip() @Comment("Effect duration percentage (1f is the entire food duration)")
            public float duration = 1f;

            @ConfigEntry.Gui.Tooltip() @Comment("Effect Level")
            public int amplifier = 1;

            public MobEffect getEffect() {
                if (ID == null || ID.isBlank())
                    return null;
                ResourceLocation id = ResourceLocation.tryParse(ID);
                if (id == null)
                    return null;

                #if PRE_CURRENT_MC_1_19_2
                return Registry.MOB_EFFECT.get(id);
                #elif POST_CURRENT_MC_1_20_1
                return BuiltInRegistries.MOB_EFFECT.get(id);
                #endif
            }
        }
    }

    @Config(name = "client")
    public static final class Client implements ConfigData {
        @ConfigEntry.Gui.Tooltip
        @Comment("Enlarge the currently eaten food icons")
        public boolean useLargeIcons = true;
    }
}
