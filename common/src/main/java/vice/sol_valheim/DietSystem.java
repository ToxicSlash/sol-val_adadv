package vice.sol_valheim;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

#if PRE_CURRENT_MC_1_19_2
import net.minecraft.core.Registry;
#elif POST_CURRENT_MC_1_20_1
import net.minecraft.core.registries.BuiltInRegistries;
#endif

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DietSystem {
    public static final String LEGACY_TIMER_OBJECTIVE = "cmobs.diet.Timer";

    private static final Map<String, Attribute> ATTRIBUTE_CACHE = new HashMap<>();
    private static final Set<String> MISSING_ATTRIBUTES = new HashSet<>();

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

        int added = Math.max(1, entry.points);
        int next = Math.min(DietConfig.maxPoints(), data.getPoints(category) + added);
        data.setPoints(category, next);
        data.setDecayTicks(category, nextDecayTicks(next));
        data.resetGlobalTimer();

        refreshCategory(player, category, next);
        syncLegacyScoreboards(player, data);

        if (DietConfig.showActionbar())
            showPlus(player, category, added, next);
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

        if (changed)
            syncLegacyScoreboards(player, data);

        // Effects are intentionally short and refreshed once per second. Keep
        // attribute work event-driven so optional-attribute lookup does not run
        // every second for every connected player.
        if (player.tickCount % 20 == 0) {
            refreshEffectDurations(player, data);
            syncLegacyTimer(player, data);
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
            refreshAttributes(player, category, 0);
        }
    }

    private static int nextDecayTicks(int points) {
        int seconds = points <= 1 ? DietConfig.baseSecondsAtOnePoint() : DietConfig.secondsPerPoint();
        return Math.max(1, seconds) * 20;
    }

    private static void refreshEffectDurations(ServerPlayer player, DietData data) {
        for (DietCategory category : DietCategory.values())
            refreshEffectState(player, category, data.getPoints(category));
    }

    private static void refreshCategory(ServerPlayer player, DietCategory category, int points) {
        refreshEffectState(player, category, points);
        refreshAttributes(player, category, points);
    }

    private static void refreshEffectState(ServerPlayer player, DietCategory category, int points) {
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

    private static void refreshAttributes(ServerPlayer player, DietCategory category, int points) {
        int primaryScale = points <= 0 ? 0 : (points >= DietConfig.bonusAtPoints() ? 2 : 1);
        boolean greater = points >= DietConfig.bonusAtPoints();
        var cfg = SOLValheim.Config.common.dietBlessings;

        switch (category) {
            case COMBAT -> {
                setAttribute(player, "minecraft:generic.attack_damage", "cb515903-6df7-4367-88f2-2b9e49bf78af", cfg.warriorsAttackDamage * primaryScale, AttributeModifier.Operation.ADDITION);
                setAttribute(player, "minecraft:generic.attack_speed", "9ee97a37-9f0c-46db-8c9d-ea64b7fa4f7a", cfg.warriorsAttackSpeed * primaryScale, AttributeModifier.Operation.ADDITION);
                setAttribute(player, "zenith_attributes:healing_received", "b5ac0264-ef05-4425-8767-25e8e9851721", greater ? cfg.greaterWarriorsHealingReceived : 0.0, AttributeModifier.Operation.ADDITION);
                setAttribute(player, "obscure_api:critical_hit", "8c2d890f-ffdb-40de-9a24-8840b4a58377", greater ? cfg.greaterWarriorsCriticalHit : 0.0, AttributeModifier.Operation.ADDITION);
            }
            case BUILDING -> {
                setAttribute(player, "reach-entity-attributes:reach", "727937d9-7779-44c9-ae1d-3c38614b0575", cfg.buildersReach * primaryScale, AttributeModifier.Operation.ADDITION);
                setAttribute(player, "doublejumpattribute:doublejumpattribute", "e0d58ef2-5ecd-4c44-a41c-11635047eec1", greater ? cfg.greaterBuildersDoubleJump : 0.0, AttributeModifier.Operation.ADDITION);
                setAttribute(player, "bettertrims:item_magnet", "de12a250-c5fb-4cf2-9425-2bb82b30974a", greater ? cfg.greaterBuildersItemMagnet : 0.0, AttributeModifier.Operation.ADDITION);
            }
            case MINING -> {
                setAttribute(player, "zenith_attributes:mining_speed", "de07647d-4faa-4a95-b5f1-b1362712fb76", cfg.minersMiningSpeed * primaryScale, AttributeModifier.Operation.ADDITION);
                setAttribute(player, "bettertrims:fortune", "a2d9987e-1dd3-43be-8168-d1fede7b224d", greater ? cfg.greaterMinersFortune : 0.0, AttributeModifier.Operation.ADDITION);
                setAttribute(player, "bettertrims:miners_rush", "1de98e54-2dd1-445c-8f72-33b278d9075b", greater ? cfg.greaterMinersRush : 0.0, AttributeModifier.Operation.ADDITION);
            }
            case ADVENTURING -> {
                setAttribute(player, "minecraft:generic.movement_speed", "20ab0528-ad69-4bd4-93b3-b7b3c0f6ea51", cfg.wayfarersMovementSpeed * primaryScale, AttributeModifier.Operation.MULTIPLY_BASE);
                setAttribute(player, "minecraft:generic.luck", "4263c81c-dc03-41f3-8839-d7ea21dd091a", cfg.wayfarersLuck * primaryScale, AttributeModifier.Operation.ADDITION);
                setAttribute(player, "zenith_attributes:dodge_chance", "c8e26a84-8132-42fb-b53f-421c43359b0e", greater ? cfg.greaterWayfarersDodgeChance : 0.0, AttributeModifier.Operation.ADDITION);
                setAttribute(player, "bettertrims:swim_speed", "e2e5dd84-1aba-4602-a30e-cf690dbef433", greater ? cfg.greaterWayfarersSwimSpeed : 0.0, AttributeModifier.Operation.ADDITION);
            }
        }
    }

    private static void setAttribute(ServerPlayer player, String attributeId, String uuidString, double amount, AttributeModifier.Operation operation) {
        Attribute attribute = resolveAttribute(attributeId);
        if (attribute == null)
            return;

        var instance = player.getAttribute(attribute);
        if (instance == null)
            return;

        UUID uuid = UUID.fromString(uuidString);
        var existing = instance.getModifier(uuid);
        if (Math.abs(amount) < 0.0000001) {
            if (existing != null)
                instance.removeModifier(uuid);
            return;
        }

        if (existing != null
                && Math.abs(existing.getAmount() - amount) < 0.0000001
                && existing.getOperation() == operation)
            return;

        if (existing != null)
            instance.removeModifier(uuid);

        instance.addTransientModifier(new AttributeModifier(
                uuid,
                "sol_valheim_diet_" + attributeId.replace(':', '_'),
                amount,
                operation
        ));
    }

    private static Attribute resolveAttribute(String attributeId) {
        Attribute cached = ATTRIBUTE_CACHE.get(attributeId);
        if (cached != null)
            return cached;
        if (MISSING_ATTRIBUTES.contains(attributeId))
            return null;

        ResourceLocation id = ResourceLocation.tryParse(attributeId);
        if (id == null) {
            MISSING_ATTRIBUTES.add(attributeId);
            return null;
        }

        #if PRE_CURRENT_MC_1_19_2
        var optional = Registry.ATTRIBUTE.getOptional(id);
        #elif POST_CURRENT_MC_1_20_1
        var optional = BuiltInRegistries.ATTRIBUTE.getOptional(id);
        #endif

        if (optional.isEmpty()) {
            MISSING_ATTRIBUTES.add(attributeId);
            return null;
        }

        Attribute attribute = optional.get();
        ATTRIBUTE_CACHE.put(attributeId, attribute);
        return attribute;
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

        syncLegacyTimer(player, data);
    }

    private static void syncLegacyTimer(ServerPlayer player, DietData data) {
        var objective = player.getScoreboard().getObjective(LEGACY_TIMER_OBJECTIVE);
        if (objective != null)
            player.getScoreboard().getOrCreatePlayerScore(player.getScoreboardName(), objective).setScore(data.getGlobalTimerTicks() / 20);
    }
}
