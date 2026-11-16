package dev.lumen.visuals.mixin;

import dev.lumen.visuals.LumenConfig;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** View model: позиция/размер/поворот предмета в руке и свои стили анимации взмаха. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), require = 0)
    private void lumenvisuals$viewModel(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                        float swingProgress, ItemStack item, float equipProgress,
                                        MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                        CallbackInfo ci) {
        LumenConfig c = LumenConfig.get();
        if (!c.vm) return;
        float side = hand == Hand.MAIN_HAND ? 1f : -1f;

        matrices.translate(c.vmX / 100f * side, c.vmY / 100f, c.vmZ / 100f);
        if (c.vmScale != 100) {
            float s = c.vmScale / 100f;
            matrices.scale(s, s, s);
        }
        if (c.vmRotX != 0) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(c.vmRotX));
        if (c.vmRotY != 0) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(c.vmRotY * side));
        if (c.vmRotZ != 0) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(c.vmRotZ * side));

        String style = c.vmSwing;
        if (hand != Hand.MAIN_HAND || "vanilla".equals(style) || "smooth".equals(style)
                || "slow".equals(style) || "snap".equals(style) || "none".equals(style)) return;

        float sp = Math.min(1f, player.getHandSwingProgress(tickDelta) * c.vmSwingSpeed / 100f);
        if (sp <= 0f) return;
        float s = MathHelper.sin(sp * (float) Math.PI);
        if ("tilt".equals(style)) {
            // Предмет плавно наклоняется к игроку на пике удара и возвращается в исходное положение.
            float eased = s * s * (3f - 2f * s);
            matrices.translate(0.02f * side * eased, -0.08f * eased, -0.16f * eased);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-34f * eased));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-12f * side * eased));
            return;
        }
        switch (style) {
            case "stab" -> {
                matrices.translate(0f, 0f, -s * 0.55f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(s * 14f));
            }
            case "slash" -> {
                matrices.translate(s * 0.30f, -s * 0.18f, -s * 0.30f);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-s * 60f));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(s * 28f));
            }
            case "spin" -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sp * 360f));
            case "bounce" -> {
                matrices.translate(0f, s * 0.26f, -s * 0.12f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-s * 22f));
            }
            default -> {
            }
        }
    }

    /** Форма кривой ванильного взмаха (swingProgress — третий float-аргумент). */
    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
    private float lumenvisuals$swing(float swing) {
        LumenConfig c = LumenConfig.get();
        if (!c.vm) return swing;
        float sp = Math.min(1f, swing * c.vmSwingSpeed / 100f);
        return switch (c.vmSwing) {
            case "vanilla" -> sp;
            case "smooth" -> sp * sp * (3f - 2f * sp);
            case "slow" -> (float) Math.pow(sp, 1.7);
            case "snap" -> (float) Math.sqrt(sp);
            default -> 0f; // none / stab / slash / spin / bounce: ванильный взмах убираем
        };
    }

    /** Убрать анимацию подъёма предмета (equipProgress — четвёртый float-аргумент). */
    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 3, require = 0)
    private float lumenvisuals$equip(float equip) {
        LumenConfig c = LumenConfig.get();
        return (c.vm && c.vmNoEquip) ? 0f : equip;
    }
}
