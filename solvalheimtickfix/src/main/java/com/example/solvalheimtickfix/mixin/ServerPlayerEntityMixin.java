package com.example.solvalheimtickfix.mixin;

import com.example.solvalheimtickfix.compat.SolCompat;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Unique
    private long solvalheimtickfix$lastProcessedTick = Long.MIN_VALUE;

    @Inject(method = "tick", at = @At("TAIL"))
    private void solvalheimtickfix$forceFoodTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (player.getWorld().isClient()) {
            return;
        }

        long worldTime = player.getWorld().getTime();
        if (this.solvalheimtickfix$lastProcessedTick == worldTime) {
            return;
        }
        this.solvalheimtickfix$lastProcessedTick = worldTime;

        SolCompat.tickFood(player);
    }
}
