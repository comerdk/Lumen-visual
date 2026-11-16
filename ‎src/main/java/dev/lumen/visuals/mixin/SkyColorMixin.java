package dev.lumen.visuals.mixin;

import dev.lumen.visuals.Gfx;
import dev.lumen.visuals.LumenConfig;
import dev.lumen.visuals.WorldEffects;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Recolors the actual sky dome color. It is not a HUD overlay, so it cannot paint over blocks. */
@Mixin(ClientWorld.class)
public abstract class SkyColorMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void lumenvisuals$skyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        LumenConfig cfg = LumenConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!cfg.world || !WorldEffects.plasmaActive(cfg) || mc.world == null || mc.world.getRegistryKey() != World.OVERWORLD) return;
        int original = cir.getReturnValue();
        int plasma = WorldEffects.plasmaSkyRgb(cfg);
        float strength = cfg.plasmaIntensity / 100.0f;
        cir.setReturnValue((original & 0xFF000000) | Gfx.mixRgb(original, plasma, strength));
    }
}
