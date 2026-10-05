package vice.sol_valheim;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

public final class DietSystem {
    public static final String LEGACY_TIMER_OBJECTIVE = "cmobs.diet.Timer";

    private DietSystem() {
    }

    public static void onConsumed(ServerPlayer player, ItemStack stack, DietData data) {
        if (player == null || stack == null || stack.isEmpty() || data == null)
            return;

        String itemId = stack.getItem().arch$registryName().toString();
        long gameTime = player.serverLevel().getGameTime();
        if (!data.markConsumption(gameTime, itemId))
            return;

        if ("minecraft:rotten_flesh".equals(itemId)) {
            purge(player, data, true);
            return;
        }

        DietConfig.FoodEntry entry = DietConfig.get(itemId);
        if (entry == null)
            return;

        DietCategory category = entry.category();
        if (category == null)
            return;

        int next = Math.min(DietConfig.maxPoints(), data.getPoints(category) + Math.max(1, entry.points));
        data.setPoints(category, next);
        data.setDecayTicks(category, nextDecayTicks(next));
        data.resetGlobalTimer();

        refreshCategory(player, category, next);
        syncLegacyScoreboards(player, data);

        if (DietConfig.showActionbar())
            showPlus(player, category, Math.max(1, entry.points), next);
        if (DietConfig.showParticles())
            burstParticles(player, category);
    }

    public static void tick(ServerPlayer player, DietData data) {
        if (player == null || data == null)
            return;

        data.incrementGlobalTimer();
        boolean changed = false;

        for (DietCategory category : DietCategory.values()) {
            int points = data.getPoints(category);
            if (points <= 0)
                continue;

            int remaining = data.getDecayTicks(category);
            if (remaining <= 0)
                remaining = nextDecayTicks(points);

            remaining--;
            if (remaining <= 0) {
                points--;
                data.setPoints(category, points);
                data.setDecayTicks(category, points > 0 ? nextDecayTicks(points) : 0);
                refreshCategory(player, category, points);
                changed = true;
            } else {
                data.setDecayTicks(category, remaining);
            }
        }

        if (changed || player.tickCount % 20 == 0) {
            refreshAllEffects(player, data);
            syncLegacyScoreboards(player, data);
        }
    }

    public static void onConfigReload(ServerPlayer player, DietData data) {
        if (player == null || data == null)
            return;

        for (DietCategory category : DietCategory.values()) {
            int points = Math.min(data.getPoints(category), DietConfig.maxPoints());
            data.setPoints(category, points);
            data.setDecayTicks(category, points > 0 ? nextDecayTicks(points) : 0);
        }
        refreshAllEffects(player, data);
        syncLegacyScoreboards(player, data);
    }

    public static void purge(ServerPlayer player, DietData data, boolean feedback) {
        if (data == null)
            return;

        data.clear();
        if (player != null) {
            clearEffects(player);
            syncLegacyScoreboards(player, data);
            if (feedback)
                player.displayClientMessage(Component.literal("Diet Purged").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        }
    }

    public static void clearOnDeath(ServerPlayer player, DietData data) {
        if (data != null && data.hasAny())
            purge(player, data, false);
    }

    public static void sendSummary(ServerPlayer player, DietData data) {
        if (player == null || data == null)
            return;

        int max = DietConfig.maxPoints();
        for (DietCategory category : DietCategory.values()) {
            Component line = Component.literal(category.singularName + ": ")
                    .withStyle(category.color)
                    .append(Component.literal(data.getPoints(category) + "/" + max).withStyle(ChatFormatting.WHITE));
            player.sendSystemMessage(line);
        }
    }

    public static void refreshAllEffects(ServerPlayer player, DietData data) {
        for (DietCategory category : DietCategory.values())
            refreshCategory(player, category, data.getPoints(category));
    }

    public static void clearEffects(ServerPlayer player) {
        for (DietCategory category : DietCategory.values()) {
            player.removeEffect(DietEffects.primary(category));
            player.removeEffect(DietEffects.bonus(category));
        }
    }

    private static int nextDecayTicks(int points) {
        int seconds = points <= 1 ? DietConfig.baseSecondsAtOnePoint() : DietConfig.secondsPerPoint();
        return Math.max(1, seconds) * 20;
    }

    private static void refreshCategory(ServerPlayer player, DietCategory category, int points) {
        MobEffect primary = DietEffects.primary(category);
        MobEffect bonus = DietEffects.bonus(category);

        if (points <= 0) {
            player.removeEffect(primary);
            player.removeEffect(bonus);
            return;
        }

        int primaryAmplifier = points >= DietConfig.bonusAtPoints() ? 1 : 0;
        MobEffectInstance current = player.getEffect(primary);
        if (current == null || current.getAmplifier() != primaryAmplifier || current.getDuration() < 30) {
            if (current != null && current.getAmplifier() != primaryAmplifier)
                player.removeEffect(primary);
            player.addEffect(new MobEffectInstance(primary, 80, primaryAmplifier, false, DietConfig.showParticles(), true));
        }

        if (points >= DietConfig.bonusAtPoints()) {
            MobEffectInstance currentBonus = player.getEffect(bonus);
            if (currentBonus == null || currentBonus.getDuration() < 30)
                player.addEffect(new MobEffectInstance(bonus, 80, 0, false, DietConfig.showParticles(), true));
        } else {
            player.removeEffect(bonus);
        }
    }

    private static void showPlus(ServerPlayer player, DietCategory category, int added, int total) {
        Component text = Component.literal("⟡ +" + added + " ")
                .append(Component.literal(category.singularName + " Diet ").withStyle(category.color))
                .append(Component.literal("(" + total + "/" + DietConfig.maxPoints() + ")").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        player.displayClientMessage(text, true);
    }

    private static void burstParticles(ServerPlayer player, DietCategory category) {
        ParticleOptions particle = switch (category) {
            case COMBAT -> ParticleTypes.CRIT;
            case BUILDING -> ParticleTypes.CLOUD;
            case MINING -> ParticleTypes.HAPPY_VILLAGER;
            case ADVENTURING -> ParticleTypes.ENCHANTED_HIT;
        };

        player.serverLevel().sendParticles(
                particle,
                player.getX(), player.getY() + 1.0, player.getZ(),
                20,
                0.5, 0.5, 0.5,
                0.1
        );
    }

    private static void syncLegacyScoreboards(ServerPlayer player, DietData data) {
        var scoreboard = player.getScoreboard();
        String holder = player.getScoreboardName();

        for (DietCategory category : DietCategory.values()) {
            var objective = scoreboard.getObjective(category.legacyScoreboard);
            if (objective != null)
                scoreboard.getOrCreatePlayerScore(holder, objective).setScore(data.getPoints(category));
        }

        var timerObjective = scoreboard.getObjective(LEGACY_TIMER_OBJECTIVE);
        if (timerObjective != null)
            scoreboard.getOrCreatePlayerScore(holder, timerObjective).setScore(data.getGlobalTimerTicks() / 20);
    }
}
