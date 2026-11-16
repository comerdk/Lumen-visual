package dev.lumen.visuals.mixin;

import dev.lumen.visuals.LumenConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fullbright: подменяем значение гаммы, пока модуль включён. Ничего не рисуем и не тикаем лишний раз. */
@Mixin(SimpleOption.class)
public abstract class SimpleOptionMixin<T> {

    @SuppressWarnings("unchecked")
    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private void lumenvisuals$fullbright(CallbackInfoReturnable<T> cir) {
        LumenConfig cfg = LumenConfig.get();
        if (!cfg.fullbright) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        if ((Object) this == mc.options.getGamma()) {
            cir.setReturnValue((T) (Object) Double.valueOf(cfg.fullbrightGamma));
        }
    }
}
