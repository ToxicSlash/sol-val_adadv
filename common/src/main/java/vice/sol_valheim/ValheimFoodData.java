package vice.sol_valheim;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;

#if PRE_CURRENT_MC_1_19_2
import net.minecraft.core.Registry;
#elif POST_CURRENT_MC_1_20_1
import net.minecraft.core.registries.BuiltInRegistries;
#endif

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ValheimFoodData
{
    public static final EntityDataSerializer<ValheimFoodData> FOOD_DATA_SERIALIZER = new EntityDataSerializer<>(){
        @Override
        public void write(FriendlyByteBuf buffer, ValheimFoodData value)
        {
            buffer.writeNbt(value.save(new CompoundTag()));
        }

        @Override
        public ValheimFoodData read(FriendlyByteBuf buffer) {
            CompoundTag tag = buffer.readNbt();
            return tag == null ? new ValheimFoodData() : ValheimFoodData.read(tag);
        }

        @Override
        public ValheimFoodData copy(ValheimFoodData value)
        {
            var ret = new ValheimFoodData();
            ret.MaxItemSlots = value.MaxItemSlots;
            for (var entry : value.ItemEntries)
                ret.ItemEntries.add(new EatenFoodItem(entry));
            if (value.DrinkSlot != null)
                ret.DrinkSlot = new EatenFoodItem(value.DrinkSlot);
            return ret;
        }
    };

    public List<EatenFoodItem> ItemEntries = new ArrayList<>();
    public EatenFoodItem DrinkSlot;
    public int MaxItemSlots = sol_valheim$configuredMaxSlots();

    public void eatItem(ItemStack food)
    {
        sol_valheim$sanitizeMaxSlots();

        if (food == null || food.isEmpty() || food.is(Items.ROTTEN_FLESH))
            return;

        var config = ModConfig.getFoodConfig(food);
        if (config == null)
            return;

        ItemStack stored = food.copy();
        stored.setCount(1);
        var isDrink = food.getUseAnimation() == UseAnim.DRINK;
        if (isDrink) {
            if (DrinkSlot != null && !DrinkSlot.canEatEarly())
                return;

            if (DrinkSlot == null)
                DrinkSlot = new EatenFoodItem(stored, config.getTime());
            else {
                DrinkSlot.ticksLeft = config.getTime();
                DrinkSlot.item = stored;
            }
            return;
        }

        var existing = getEatenFood(food);
        if (existing != null)
        {
            if (!existing.canEatEarly())
                return;

            existing.ticksLeft = config.getTime();
            existing.item = stored;
            sol_valheim$sortEntries();
            return;
        }

        // A full stomach cannot be used to cycle in a different food. The
        // player must wait for an occupied slot to expire; only the same food
        // may be refreshed once that food reaches its re-eat threshold.
        if (ItemEntries.size() >= MaxItemSlots)
            return;

        ItemEntries.add(new EatenFoodItem(stored, config.getTime()));
        sol_valheim$sortEntries();
    }

    public boolean canEat(ItemStack food)
    {
        sol_valheim$sanitizeMaxSlots();

        if (food == null || food.isEmpty())
            return false;

        if (food.is(Items.ROTTEN_FLESH))
            return true;

        if (ModConfig.getFoodConfig(food) == null)
            return false;

        if (food.getUseAnimation() == UseAnim.DRINK)
            return DrinkSlot == null || DrinkSlot.canEatEarly();

        var existing = getEatenFood(food);
        if (existing != null)
            return existing.canEatEarly();

        return ItemEntries.size() < MaxItemSlots;
    }

    public EatenFoodItem getEatenFood(ItemStack food) {
        for (var entry : ItemEntries) {
            if (ItemStack.isSameItemSameTags(entry.item, food))
                return entry;
        }
        return null;
    }

    public boolean hasActiveFood() {
        return !ItemEntries.isEmpty() || DrinkSlot != null;
    }

    public void clear()
    {
        ItemEntries.clear();
        DrinkSlot = null;
    }

    public void tick()
    {
        advanceTicks(1);
    }

    public void advanceTicks(long ticks)
    {
        sol_valheim$sanitizeMaxSlots();
        if (ticks <= 0)
            return;

        var iterator = ItemEntries.iterator();
        while (iterator.hasNext()) {
            var item = iterator.next();
            if (!sol_valheim$isValidFoodEntry(item)) {
                iterator.remove();
                continue;
            }

            long left = (long) item.ticksLeft - ticks;
            if (left <= 0)
                iterator.remove();
            else
                item.ticksLeft = (int) Math.min(Integer.MAX_VALUE, left);
        }

        if (DrinkSlot != null) {
            if (!sol_valheim$isValidFoodEntry(DrinkSlot)) {
                DrinkSlot = null;
            } else {
                long left = (long) DrinkSlot.ticksLeft - ticks;
                if (left <= 0)
                    DrinkSlot = null;
                else
                    DrinkSlot.ticksLeft = (int) Math.min(Integer.MAX_VALUE, left);
            }
        }
    }

    public float getTotalFoodNutrition()
    {
        float foodNutrition = 0f;
        for (var item : ItemEntries)
        {
            var food = ModConfig.getFoodConfig(item.item);
            if (food != null)
                foodNutrition += food.getHearts();
        }

        float total = foodNutrition;
        if (DrinkSlot != null)
        {
            total = foodNutrition * (1.0f + SOLValheim.Config.common.drinkSlotFoodEffectivenessBonus);
            var drink = ModConfig.getFoodConfig(DrinkSlot.item);
            if (drink != null)
                total += drink.getHearts();
        }

        return total;
    }

    public float getRegenSpeed()
    {
        float foodRegen = 0f;
        for (var item : ItemEntries)
        {
            var food = ModConfig.getFoodConfig(item.item);
            if (food != null)
                foodRegen += food.getHealthRegen();
        }

        float regen = 0.25f + foodRegen;
        if (DrinkSlot != null)
        {
            regen = 0.25f + foodRegen * (1.0f + SOLValheim.Config.common.drinkSlotFoodEffectivenessBonus);
            var drink = ModConfig.getFoodConfig(DrinkSlot.item);
            if (drink != null)
                regen += drink.getHealthRegen();
        }

        return regen;
    }

    public CompoundTag save(CompoundTag tag) {
        int count = 0;
        tag.putInt("max_slots", MaxItemSlots);
        for (var item : ItemEntries)
        {
            var registryName = item.item.getItem().arch$registryName();
            if (registryName == null)
                continue;

            tag.putString("id" + count, registryName.toString());
            CompoundTag stackData = item.item.getTag();
            if (stackData != null)
                tag.put("data" + count, stackData);
            tag.putInt("ticks" + count, item.ticksLeft);
            count++;
        }
        tag.putInt("count", count);

        if (DrinkSlot != null)
        {
            var registryName = DrinkSlot.item.getItem().arch$registryName();
            if (registryName != null) {
                tag.putString("drink", registryName.toString());
                CompoundTag stackData = DrinkSlot.item.getTag();
                if (stackData != null)
                    tag.put("drinkData", stackData);
                tag.putInt("drinkticks", DrinkSlot.ticksLeft);
            }
        }

        return tag;
    }

    public static ValheimFoodData read(CompoundTag tag) {
        var instance = new ValheimFoodData();
        if (tag == null)
            return instance;

        instance.MaxItemSlots = sol_valheim$configuredMaxSlots();
        int size = Math.max(0, Math.min(16, tag.getInt("count")));
        for (int count = 0; count < size; count++)
        {
            var stack = sol_valheim$readStack(tag.getString("id" + count), tag, "data" + count);
            if (stack == null)
                continue;

            int ticks = tag.getInt("ticks" + count);
            var eaten = new EatenFoodItem(stack, ticks);
            if (ticks > 0 && sol_valheim$isValidFoodEntry(eaten))
                instance.ItemEntries.add(eaten);
        }
        instance.sol_valheim$sortEntries();

        String drink = tag.getString("drink");
        int drinkTicks = tag.getInt("drinkticks");
        if (!drink.isBlank() && drinkTicks > 0)
        {
            String drinkDataKey = tag.contains("drinkData")
                    ? "drinkData"
                    : (tag.contains("drinkData" + size) ? "drinkData" + size : null);
            ItemStack stack = sol_valheim$readStack(drink, tag, drinkDataKey);
            if (stack != null) {
                var eaten = new EatenFoodItem(stack, drinkTicks);
                if (sol_valheim$isValidFoodEntry(eaten))
                    instance.DrinkSlot = eaten;
            }
        }

        return instance;
    }

    private static ItemStack sol_valheim$readStack(String idString, CompoundTag tag, String dataKey) {
        ResourceLocation id = ResourceLocation.tryParse(idString);
        if (id == null)
            return null;

        #if PRE_CURRENT_MC_1_19_2
        var item = Registry.ITEM.get(id);
        #elif POST_CURRENT_MC_1_20_1
        var item = BuiltInRegistries.ITEM.get(id);
        #endif

        if (item == null || item == Items.AIR)
            return null;

        var stack = new ItemStack(item, 1);
        if (dataKey != null && tag.contains(dataKey))
            stack.setTag(tag.getCompound(dataKey));
        return stack;
    }

    private void sol_valheim$sortEntries() {
        ItemEntries.sort(Comparator.comparingInt(a -> a.ticksLeft));
    }

    private void sol_valheim$sanitizeMaxSlots() {
        MaxItemSlots = sol_valheim$configuredMaxSlots();
    }

    private static int sol_valheim$configuredMaxSlots() {
        if (SOLValheim.Config == null || SOLValheim.Config.common == null)
            return 3;
        return Math.max(2, Math.min(5, SOLValheim.Config.common.maxSlots));
    }

    private static boolean sol_valheim$isValidFoodEntry(EatenFoodItem entry) {
        return entry != null
                && entry.item != null
                && !entry.item.isEmpty()
                && !entry.item.is(Items.AIR)
                && ModConfig.getFoodConfig(entry.item) != null;
    }

    public static class EatenFoodItem {
        public ItemStack item;
        public int ticksLeft;

        public boolean canEatEarly() {
            if (ticksLeft <= 1200)
                return true;

            var config = ModConfig.getFoodConfig(item);
            if (config == null)
                return false;

            float threshold = Math.max(0f, Math.min(1f, SOLValheim.Config.common.eatAgainPercentage));
            return ((float) ticksLeft / config.getTime()) <= threshold;
        }

        public EatenFoodItem(ItemStack item, int ticksLeft)
        {
            this.item = item.copy();
            this.item.setCount(1);
            this.ticksLeft = ticksLeft;
        }

        public EatenFoodItem(EatenFoodItem eaten)
        {
            this(eaten.item, eaten.ticksLeft);
        }
    }
}
