package dev.lumen.visuals.mixin;

import dev.lumen.visuals.LumenConfig;
import dev.lumen.visuals.WorldEffects;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Fog;
import net.minecraft.entity.effect.StatusEffects;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Свой туман и цвет неба/фона для «World visuals» (цвет учитывает насыщенность и небо-плазму).
 * require = 0: если в версии игры метод называется иначе, мод просто не меняет туман, а не падает.
 */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {

    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true, require = 0)
    private static void lumenvisuals$fog(CallbackInfoReturnable<Fog> cir) {
        LumenConfig c = LumenConfig.get();
        if (!c.world || !(c.worldFog || WorldEffects.plasmaActive(c))) return;
        Fog f = cir.getReturnValue();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (f == null || mc.gameRenderer == null || mc.player == null) return;
        if (mc.gameRenderer.getCamera().getSubmersionType() != CameraSubmersionType.NONE) return;
        if (mc.player.hasStatusEffect(StatusEffects.BLINDNESS) || mc.player.hasStatusEffect(StatusEffects.DARKNESS)) return;

        int rgb = WorldEffects.fogRgb(c);
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;

        float start = f.start();
        float end = f.end();
        if (c.worldFog && start > 0f) { // туман земли: двигаем дальность; у неба start == 0, там меняем только цвет
            start = end * c.fogStart / 100f;
            end = end * c.fogEnd / 100f;
        }
        cir.setReturnValue(new Fog(start, end, f.shape(), r, g, b, f.alpha()));
    }

    @Inject(method = "getFogColor", at = @At("RETURN"), require = 0)
    private static void lumenvisuals$fogColor(CallbackInfoReturnable<Vector4f> cir) {
        LumenConfig c = LumenConfig.get();
        if (!c.world || !(c.worldFog || WorldEffects.plasmaActive(c))) return;
        Vector4f v = cir.getReturnValue();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (v == null || mc.gameRenderer == null) return;
        if (mc.gameRenderer.getCamera().getSubmersionType() != CameraSubmersionType.NONE) return;
        int rgb = WorldEffects.fogRgb(c);
        v.set(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, v.w);
    }
}
