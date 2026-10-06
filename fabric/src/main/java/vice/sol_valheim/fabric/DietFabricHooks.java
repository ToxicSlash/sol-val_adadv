package vice.sol_valheim.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import vice.sol_valheim.ConfigValidator;
import vice.sol_valheim.ConsumptionSystem;
import vice.sol_valheim.DietConfig;
import vice.sol_valheim.DietSync;
import vice.sol_valheim.DietSystem;
import vice.sol_valheim.ModConfig;
import vice.sol_valheim.SOLValheim;
import vice.sol_valheim.ValheimFoodData;
import vice.sol_valheim.accessors.DietDataAccessor;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

public final class DietFabricHooks {
    private static boolean initialized;

    private DietFabricHooks() {
    }

    public static void init() {
        if (initialized)
            return;
        initialized = true;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("diet").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                DietSystem.sendSummary(player, ((DietDataAccessor) player).sol_valheim$getDietData());
                return 1;
            }));

            dispatcher.register(Commands.literal("solvalheim")
                    .then(Commands.literal("diet").executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        DietSystem.sendSummary(player, ((DietDataAccessor) player).sol_valheim$getDietData());
                        return 1;
                    }))
                    .then(Commands.literal("stomach").executes(context -> showStomach(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("foodinfo").executes(context -> showFoodInfo(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("clearstomach")
                            .requires(source -> source.hasPermission(2))
                            .executes(context -> clearStomach(context.getSource().getPlayerOrException(), context.getSource().getPlayerOrException()))
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> clearStomach(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player")))))
                    .then(Commands.literal("cleardiet")
                            .requires(source -> source.hasPermission(2))
                            .executes(context -> clearDiet(context.getSource().getPlayerOrException(), context.getSource().getPlayerOrException()))
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> clearDiet(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player")))))
                    .then(Commands.literal("reload")
                            .requires(source -> source.hasPermission(2))
                            .executes(context -> reloadDiet(context.getSource().getPlayerOrException()))));
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ConfigValidator.validateStartup();
            DietConfig.validateResolvedItems();
        });

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (!success || !DietConfig.reload())
                return;

            ModConfig.clearRuntimeCaches();
            ConfigValidator.validateStartup();
            DietConfig.validateResolvedItems();
            for (var player : server.getPlayerList().getPlayers()) {
                var data = ((DietDataAccessor) player).sol_valheim$getDietData();
                DietSystem.onConfigReload(player, data);
                DietSync.sendToPlayer(player);
                SOLValheim.syncFoodData(player, ((PlayerEntityMixinDataAccessor) player).sol_valheim$getFoodData());
            }
        });

        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
            if (!joined)
                return;

            DietSync.sendToPlayer(player);
            SOLValheim.syncFoodData(player, ((PlayerEntityMixinDataAccessor) player).sol_valheim$getFoodData());
        });
    }

    private static int showStomach(ServerPlayer player) {
        ValheimFoodData data = ((PlayerEntityMixinDataAccessor) player).sol_valheim$getFoodData();
        player.sendSystemMessage(Component.literal("SOL Stomach " + data.ItemEntries.size() + "/" + data.MaxItemSlots).withStyle(ChatFormatting.GOLD));
        int index = 1;
        for (var entry : data.ItemEntries) {
            var id = entry.item.getItem().arch$registryName();
            String state = entry.canEatEarly() ? "ready" : "locked";
            player.sendSystemMessage(Component.literal(index + ". " + id + " - " + formatTicks(entry.ticksLeft) + " [" + state + "]")
                    .withStyle(entry.canEatEarly() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
            index++;
        }
        if (data.DrinkSlot != null) {
            var id = data.DrinkSlot.item.getItem().arch$registryName();
            player.sendSystemMessage(Component.literal("Drink: " + id + " - " + formatTicks(data.DrinkSlot.ticksLeft)
                    + " [" + (data.DrinkSlot.canEatEarly() ? "ready" : "locked") + "]").withStyle(ChatFormatting.AQUA));
        }
        return 1;
    }

    private static int showFoodInfo(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (ModConfig.getFoodConfig(stack) == null)
            stack = player.getOffhandItem();
        var config = ModConfig.getFoodConfig(stack);
        if (stack.isEmpty() || config == null) {
            player.sendSystemMessage(Component.literal("Hold a SOL-compatible food or drink first.").withStyle(ChatFormatting.RED));
            return 0;
        }

        var id = stack.getItem().arch$registryName();
        String itemId = id == null ? "unknown" : id.toString();
        player.sendSystemMessage(Component.literal("SOL Food Info: " + itemId).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Health: +" + formatHearts(config.getHearts()) + " hearts"));
        player.sendSystemMessage(Component.literal("Duration: " + formatTicks(config.getTime())));
        player.sendSystemMessage(Component.literal("Re-eat/replace at: " + formatTicks(config.getReEatThresholdTicks())
                + " remaining (" + Math.round(config.getReEatPercentage() * 100f) + "%, min " + (config.getReEatMinimumTicks() / 20) + "s)"));
        player.sendSystemMessage(Component.literal("Regen: " + String.format("%.2f", config.getHealthRegen())));
        player.sendSystemMessage(Component.literal("Balance source: " + balanceSource(itemId)).withStyle(ChatFormatting.GRAY));

        var diet = DietConfig.get(itemId);
        if (diet != null && diet.category() != null)
            player.sendSystemMessage(Component.literal("Diet: +" + diet.points + " " + diet.category().tooltipName).withStyle(diet.category().color));
        else
            player.sendSystemMessage(Component.literal("Diet: none").withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    private static String balanceSource(String itemId) {
        if (SOLValheim.Config.common.foodConfigs.containsKey(itemId))
            return "legacy explicit food config";
        if (SOLValheim.Config.common.foodOverrides != null) {
            for (var override : SOLValheim.Config.common.foodOverrides) {
                if (override != null && itemId.equals(override.id))
                    return "generated values + exact food override";
            }
        }
        int colon = itemId.indexOf(':');
        String namespace = colon > 0 ? itemId.substring(0, colon) : itemId;
        if (SOLValheim.Config.common.namespaceFoodRules != null) {
            for (var rule : SOLValheim.Config.common.namespaceFoodRules) {
                if (rule != null && rule.namespace != null && namespace.equals(rule.namespace.trim()))
                    return "generated values + namespace rule " + namespace + ":*";
            }
        }
        return "automatically generated values";
    }

    private static int clearStomach(ServerPlayer sender, ServerPlayer target) {
        boolean changed = ConsumptionSystem.clearStomach(target);
        sender.sendSystemMessage(Component.literal((changed ? "Cleared " : "No active stomach food for ") + target.getScoreboardName())
                .withStyle(changed ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        return changed ? 1 : 0;
    }

    private static int clearDiet(ServerPlayer sender, ServerPlayer target) {
        boolean changed = ConsumptionSystem.clearDiet(target);
        sender.sendSystemMessage(Component.literal((changed ? "Cleared diet for " : "No active diet for ") + target.getScoreboardName())
                .withStyle(changed ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        return changed ? 1 : 0;
    }

    private static int reloadDiet(ServerPlayer sender) {
        if (!DietConfig.reload()) {
            sender.sendSystemMessage(Component.literal("Failed to reload " + DietConfig.path()).withStyle(ChatFormatting.RED));
            return 0;
        }

        ModConfig.clearRuntimeCaches();
        ConfigValidator.validateStartup();
        DietConfig.validateResolvedItems();
        var server = sender.getServer();
        for (var player : server.getPlayerList().getPlayers()) {
            DietSystem.onConfigReload(player, ((DietDataAccessor) player).sol_valheim$getDietData());
            DietSync.sendToPlayer(player);
            SOLValheim.syncFoodData(player, ((PlayerEntityMixinDataAccessor) player).sol_valheim$getFoodData());
        }
        sender.sendSystemMessage(Component.literal("Reloaded diet mappings and refreshed SOL food caches. Startup balance config still requires its normal config reload/restart.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static String formatTicks(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }

    private static String formatHearts(int healthPoints) {
        return healthPoints % 2 == 0 ? Integer.toString(healthPoints / 2) : String.format("%.1f", healthPoints / 2f);
    }
}
