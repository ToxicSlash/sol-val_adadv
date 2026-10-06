package vice.sol_valheim;

import dev.architectury.registry.registries.DeferredRegister;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

#if PRE_CURRENT_MC_1_19_2
import dev.architectury.registry.registries.Registries;
import net.minecraft.core.Registry;
#elif POST_CURRENT_MC_1_20_1
import net.minecraft.core.registries.Registries;
#endif

import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import vice.sol_valheim.api.FoodPropertiesGetter;

import java.util.List;
import java.util.function.BiConsumer;

public class SOLValheim
{
    #if PRE_CURRENT_MC_1_19_2
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create("sol_valheim", Registry.ITEM_REGISTRY);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create("sol_valheim", Registry.MOB_EFFECT_REGISTRY);
    #elif POST_CURRENT_MC_1_20_1
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create("sol_valheim", Registries.ITEM);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create("sol_valheim", Registries.MOB_EFFECT);
    #endif

    public static ModConfig Config;
    public static final String MOD_ID = "sol_valheim";

    private static AttributeModifier speedBuff;

    public static AttributeModifier getSpeedBuffModifier() {
        if (speedBuff == null)
            speedBuff = new AttributeModifier("sol_valheim_speed_buff", Config.common.speedBoost, AttributeModifier.Operation.MULTIPLY_BASE);
        return speedBuff;
    }

    public static FoodPropertiesGetter getter;
    private static BiConsumer<ServerPlayer, ValheimFoodData> foodSyncSender = (player, data) -> {};

    public static void init(FoodPropertiesGetter getter) {
        DietEffects.register();

        AutoConfig.register(ModConfig.class, PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        Config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        ModConfig.clearRuntimeCaches();
        speedBuff = null;

        SOLValheim.getter = getter;
        DietConfig.reload();
        ConfigValidator.validateStartup();
        FoodSync.initServer();
    }

    public static void setFoodSyncSender(BiConsumer<ServerPlayer, ValheimFoodData> sender) {
        foodSyncSender = sender == null ? (player, data) -> {} : sender;
    }

    public static void syncFoodData(ServerPlayer player, ValheimFoodData data) {
        foodSyncSender.accept(player, data);
    }

    public static void addTooltip(ItemStack item, TooltipFlag flag, List<Component> list){
        var food = item.getItem();
        if (food == Items.ROTTEN_FLESH) {
            list.add(Component.literal("☠ Empties Your Stomach & Diet!").withStyle(ChatFormatting.GREEN));
            return;
        }

        var registryName = food.arch$registryName();
        var dietEntry = registryName == null ? null : DietConfig.get(registryName.toString());
        var config = ModConfig.getFoodConfig(item);
        if (config == null) {
            addDietTooltip(dietEntry, list);
            return;
        }

        int healthPoints = config.getHearts();
        var hearts = healthPoints % 2 == 0 ? Integer.toString(healthPoints / 2) : String.format("%.1f", healthPoints / 2f);
        list.add(Component.literal("❤ " + hearts + " Heart" + (healthPoints / 2f > 1 ? "s" : "")).withStyle(ChatFormatting.RED));
        list.add(Component.literal("☀ " + String.format("%.1f", config.getHealthRegen()) + " Regen").withStyle(ChatFormatting.DARK_RED));

        float minutes = (float) config.getTime() / (20 * 60);
        list.add(Component.literal("⌚ " + String.format("%.0f", minutes) + " Minute" + (minutes > 1 ? "s" : "")).withStyle(ChatFormatting.GOLD));

        if (!config.extraEffects.isEmpty() && Config.common.displayEffects) {
            list.add(Component.literal(""));
            for (var effect : config.extraEffects) {
                var eff = effect.getEffect();
                if (eff == null)
                    continue;

                float effectDurationSeconds = config.getTime() * Math.max(0f, Math.min(1f, effect.duration)) / 20f;
                int minutesPart = (int) (effectDurationSeconds / 60);
                int secondsPart = (int) (effectDurationSeconds % 60);
                String text = (eff.isBeneficial() ? "★ " : "❌ ")
                        + eff.getDisplayName().getString()
                        + (effect.amplifier > 1 ? " " + effect.amplifier : "")
                        + String.format(" (%02d:%02d)", minutesPart, secondsPart);
                list.add(Component.literal(text).withStyle(eff.isBeneficial() ? ChatFormatting.GREEN : ChatFormatting.DARK_RED));
            }
        }

        addDietTooltip(dietEntry, list);

        if (item.getUseAnimation() == UseAnim.DRINK)
            list.add(Component.literal("❄ Refreshing!").withStyle(ChatFormatting.AQUA));
    }

    private static void addDietTooltip(DietConfig.FoodEntry dietEntry, List<Component> list) {
        if (dietEntry == null)
            return;

        var category = dietEntry.category();
        if (category != null)
            list.add(Component.literal("⟡ +" + dietEntry.points + " " + category.tooltipName).withStyle(ChatFormatting.GRAY));
    }
}
