package vice.sol_valheim;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

#if PRE_CURRENT_MC_1_19_2
import net.minecraft.core.Registry;
#elif POST_CURRENT_MC_1_20_1
import net.minecraft.core.registries.BuiltInRegistries;
#endif

public final class DietBlessingEffect extends MobEffect {
    public DietBlessingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFC049);
    }

    public DietBlessingEffect addOptionalAttribute(String attributeId, String uuid, double amount, AttributeModifier.Operation operation) {
        ResourceLocation id = new ResourceLocation(attributeId);

        #if PRE_CURRENT_MC_1_19_2
        Registry.ATTRIBUTE.getOptional(id).ifPresent(attribute -> addAttributeModifier(attribute, uuid, amount, operation));
        #elif POST_CURRENT_MC_1_20_1
        BuiltInRegistries.ATTRIBUTE.getOptional(id).ifPresent(attribute -> addAttributeModifier(attribute, uuid, amount, operation));
        #endif

        return this;
    }
}
