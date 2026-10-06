package vice.sol_valheim;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;

#if PRE_CURRENT_MC_1_19_2

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.level.Level;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiComponent;

#elif POST_CURRENT_MC_1_20_1

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.CommonColors;

#endif

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

public class FoodHUD implements ClientGuiEvent.RenderHud
{
    static Minecraft client;

    public FoodHUD() {
        ClientGuiEvent.RENDER_HUD.register(this);
        client = Minecraft.getInstance();
    }

    @Override
    public void renderHud(#if PRE_CURRENT_MC_1_19_2 PoseStack #elif POST_CURRENT_MC_1_20_1 GuiGraphics #endif graphics, float tickDelta) {
        if (client.player == null || SOLValheim.Config == null || SOLValheim.Config.client == null)
            return;

        var pose = #if PRE_CURRENT_MC_1_19_2 graphics #elif POST_CURRENT_MC_1_20_1 graphics.pose() #endif;
        pose.pushPose();
        try {
            var solPlayer = (PlayerEntityMixinDataAccessor) client.player;
            var foodData = solPlayer.sol_valheim$getFoodData();
            if (foodData == null)
                return;

            var hudConfig = SOLValheim.Config.client;
            boolean useLargeIcons = hudConfig.useLargeIcons;
            float hudScale = Math.max(0.25f, Math.min(3.0f, hudConfig.foodHudScale));
            int spacing = Math.max(0, Math.min(32, hudConfig.foodHudSpacing));

            int anchorX = client.getWindow().getGuiScaledWidth() / 2 + 91 + hudConfig.foodHudXOffset;
            int anchorY = client.getWindow().getGuiScaledHeight() - 39 + hudConfig.foodHudYOffset;

            pose.translate(anchorX, anchorY, 0f);
            pose.scale(hudScale, hudScale, 1f);

            ValheimFoodData.EatenFoodItem replacementTarget = findReplacementTarget(foodData);
            int offset = 1;
            int size = useLargeIcons ? 14 : 9;
            int localHeight = useLargeIcons ? -6 : 0;

            for (var food : foodData.ItemEntries) {
                if (ModConfig.getFoodConfig(food.item) == null)
                    continue;

                renderFoodSlot(graphics, food, size, offset, localHeight, useLargeIcons,
                        hudConfig.foodHudRightAligned, spacing,
                        hudConfig.foodHudShowReEatIndicator && food.canEatEarly(),
                        hudConfig.foodHudHighlightReplacement && food == replacementTarget);
                offset++;
            }

            if (foodData.DrinkSlot != null)
                renderFoodSlot(graphics, foodData.DrinkSlot, size, offset, localHeight, useLargeIcons,
                        hudConfig.foodHudRightAligned, spacing,
                        hudConfig.foodHudShowReEatIndicator && foodData.DrinkSlot.canEatEarly(),
                        hudConfig.foodHudHighlightReplacement && foodData.DrinkSlot == replacementTarget);
        } finally {
            pose.popPose();
        }
    }

    private static ValheimFoodData.EatenFoodItem findReplacementTarget(ValheimFoodData foodData) {
        if (client.player == null)
            return null;

        ItemStack held = client.player.getMainHandItem();
        if (ModConfig.getFoodConfig(held) == null)
            held = client.player.getOffhandItem();
        if (held.isEmpty() || held.is(Items.ROTTEN_FLESH) || ModConfig.getFoodConfig(held) == null)
            return null;

        if (held.getUseAnimation() == UseAnim.DRINK) {
            if (foodData.DrinkSlot != null
                    && !ItemStack.isSameItemSameTags(foodData.DrinkSlot.item, held)
                    && foodData.DrinkSlot.canEatEarly())
                return foodData.DrinkSlot;
            return null;
        }

        if (foodData.getEatenFood(held) != null || foodData.ItemEntries.size() < foodData.MaxItemSlots)
            return null;
        return foodData.findReplaceableEntry();
    }

    private static void renderFoodSlot(#if PRE_CURRENT_MC_1_19_2 PoseStack #elif POST_CURRENT_MC_1_20_1 GuiGraphics #endif graphics,
                                       ValheimFoodData.EatenFoodItem food,
                                       int size,
                                       int offset,
                                       int height,
                                       boolean useLargeIcons,
                                       boolean rightAligned,
                                       int spacing,
                                       boolean readyToRefresh,
                                       boolean replacementTarget)
    {
        var foodConfig = ModConfig.getFoodConfig(food.item);
        if (foodConfig == null)
            return;

        var isDrink = food.item.getUseAnimation() == UseAnim.DRINK;
        int bgColor = isDrink ? FastColor.ARGB32.color(96, 52, 104, 163) : FastColor.ARGB32.color(96, 0, 0, 0);
        int yellow = FastColor.ARGB32.color(255, 255, 191, 0);

        int step = size + spacing;
        int startWidth = rightAligned
                ? -(step * offset) + spacing
                : step * (offset - 1);

        float ticksLeftPercent = Float.min(1.0F, (float) food.ticksLeft / foodConfig.getTime());
        int barHeight = Integer.max(1, (int)((size + 2f) * ticksLeftPercent));
        int barColor = readyToRefresh
                ? FastColor.ARGB32.color(150, 255, 170, 0)
                : FastColor.ARGB32.color(96, 0, 0, 0);

        int wholeSeconds = Math.max(0, food.ticksLeft / 20);
        var scale = useLargeIcons ? 0.75f : 0.5f;
        boolean isSeconds = wholeSeconds < 60;
        String timeText;
        if (isSeconds) {
            timeText = wholeSeconds + "s";
        } else {
            int wholeMinutes = Math.max(1, wholeSeconds / 60);
            timeText = wholeMinutes + "m";
        }

        var pose = #if PRE_CURRENT_MC_1_19_2 graphics #elif POST_CURRENT_MC_1_20_1 graphics.pose(); #endif;

        if (replacementTarget)
            drawBorder(graphics, startWidth - 1, height - 1, size + 2, FastColor.ARGB32.color(255, 255, 145, 0));
        else if (readyToRefresh)
            drawBorder(graphics, startWidth - 1, height - 1, size + 2, FastColor.ARGB32.color(255, 70, 220, 90));

        fill(graphics, startWidth, height, startWidth + size, height + size, bgColor);
        fill(graphics, startWidth, Integer.max(height, height - barHeight + size), startWidth + size, height + size, barColor);

        pose.pushPose();
        pose.scale(scale, scale, scale);
        pose.translate(startWidth * (useLargeIcons ? 0.3333f : 1f), height * (useLargeIcons ? 0.3333f : 1f), 0f);

        if (food.item.is(Items.CAKE) && Platform.isModLoaded("farmersdelight"))
        {
            var cakeSlice = SOLValheim.ITEMS.getRegistrar().get(new ResourceLocation("farmersdelight:cake_slice"));
            renderGUIItem(graphics, cakeSlice != null ? new ItemStack(cakeSlice, 1) : food.item, startWidth + 1, height + 1);
        }
        else
        {
            renderGUIItem(graphics, food.item, startWidth + 1, height + 1);
        }

        pose.pushPose();
        pose.translate(0.0f, 0.0f, 200.0f);

        int timeColor = readyToRefresh
                ? FastColor.ARGB32.color(255, 255, 205, 64)
                : (isSeconds ? FastColor.ARGB32.color(255, 237, 57, 57) : FastColor.ARGB32.color(255, 255, 255, 255));
        drawFont(graphics, timeText, startWidth + (timeText.length() > 1 ? 6 : 12), height + 10, timeColor);
        if (!foodConfig.extraEffects.isEmpty())
            drawFont(graphics, "+" + foodConfig.extraEffects.size(), startWidth + 6, height, yellow);

        pose.popPose();
        pose.popPose();
    }

    private static void drawBorder(#if PRE_CURRENT_MC_1_19_2 PoseStack #elif POST_CURRENT_MC_1_20_1 GuiGraphics #endif graphics,
                                   int x, int y, int size, int color) {
        fill(graphics, x, y, x + size, y + 1, color);
        fill(graphics, x, y + size - 1, x + size, y + size, color);
        fill(graphics, x, y, x + 1, y + size, color);
        fill(graphics, x + size - 1, y, x + size, y + size, color);
    }

    private static void fill(#if PRE_CURRENT_MC_1_19_2 PoseStack #elif POST_CURRENT_MC_1_20_1 GuiGraphics #endif graphics, int width, int height, int x, int y, int color)
    {
        #if PRE_CURRENT_MC_1_19_2
        GuiComponent.fill(graphics, width, height, x, y, color);
        #elif POST_CURRENT_MC_1_20_1
        graphics.fill(width, height, x, y, color);
        #endif
    }

    private static void renderGUIItem(#if PRE_CURRENT_MC_1_19_2 PoseStack #elif POST_CURRENT_MC_1_20_1 GuiGraphics #endif graphics, ItemStack stack, int x, int y)
    {
        #if PRE_CURRENT_MC_1_19_2

        var itemRenderer = client.getItemRenderer();
        var bakedModel = itemRenderer.getModel(stack, null, null, 0);

        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.translate((double)x, (double)y, (double)(100.0F + itemRenderer.blitOffset));
        poseStack.translate(8.0, 8.0, 0.0);
        poseStack.scale(1.0F, -1.0F, 1.0F);
        poseStack.scale(16.0F, 16.0F, 16.0F);

        var useLargeIcons = true;
        var scale = useLargeIcons ? 0.75f : 0.5f;
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.15, 0.15, 0f);

        RenderSystem.applyModelViewMatrix();
        PoseStack poseStack2 = new PoseStack();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean bl = !bakedModel.usesBlockLight();
        if (bl) {
            Lighting.setupForFlatItems();
        }

        itemRenderer.render(stack, ItemTransforms.TransformType.GUI, false, poseStack2, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, bakedModel);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (bl) {
            Lighting.setupFor3DItems();
        }

        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();

        #elif POST_CURRENT_MC_1_20_1
        graphics.renderItem(stack, x, y);
        #endif
    }

    private static void drawFont(#if PRE_CURRENT_MC_1_19_2 PoseStack #elif POST_CURRENT_MC_1_20_1 GuiGraphics #endif graphics, String str, int x, int y, int color)
    {
        #if PRE_CURRENT_MC_1_19_2
        client.font.draw(graphics, str, x, y, color);
        #elif POST_CURRENT_MC_1_20_1
        graphics.drawString(client.font, str, x, y, color);
        #endif
    }
}
