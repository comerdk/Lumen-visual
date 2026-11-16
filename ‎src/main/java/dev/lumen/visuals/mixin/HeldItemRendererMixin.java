package dev.lumen.visuals.mixin;

import dev.lumen.visuals.LumenConfig;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** View model + Wexside-style first-person swing transforms, adapted to 1.21.4. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique private Hand lumen$currentHand;
    @Unique private float lumen$currentEquip;
    @Unique private long lumen$rotateStartNanos;

    @Shadow protected abstract void applySwingOffset(MatrixStack matrices, Arm arm, float swingProgress);

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), require = 0)
    private void lumenvisuals$viewModel(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                        float swingProgress, ItemStack item, float equipProgress,
                                        MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                        CallbackInfo ci) {
        lumen$currentHand = hand;
        lumen$currentEquip = equipProgress;
        LumenConfig c = LumenConfig.get();
        if (!c.vm) return;
        float side = hand == Hand.MAIN_HAND ? 1f : -1f;
        matrices.translate(c.vmX / 100f * side, c.vmY / 100f, c.vmZ / 100f);
        if (c.vmScale != 100) {
            float scale = c.vmScale / 100f;
            matrices.scale(scale, scale, scale);
        }
        if (c.vmRotX != 0) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(c.vmRotX));
        if (c.vmRotY != 0) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(c.vmRotY * side));
        if (c.vmRotZ != 0) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(c.vmRotZ * side));
    }

    /** Replace vanilla applySwingOffset at the exact point Wexside replaces it. */
    @Redirect(method = "swingArm", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applySwingOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"), require = 0)
    private void lumenvisuals$replaceSwing(HeldItemRenderer renderer, MatrixStack matrices, Arm arm, float progress) {
        LumenConfig c = LumenConfig.get();
        if (!c.vm || !c.vmAnimation || lumen$currentHand == null || lumen$currentHand != Hand.MAIN_HAND) {
            this.applySwingOffset(matrices, arm, progress);
            return;
        }
        AbstractClientPlayerEntity player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (player == null) { this.applySwingOffset(matrices, arm, progress); return; }
        ItemStack stack = player.getMainHandStack();
        if (stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof BowItem || stack.getItem() instanceof TridentItem
                || "vanilla".equals(c.vmSwing)) {
            this.applySwingOffset(matrices, arm, progress);
            return;
        }
        float p = MathHelper.clamp(progress * c.vmSwingSpeed / 100f, 0f, 1f);
        float sin = MathHelper.sin(p * (float)Math.PI);
        float sqrtSin = MathHelper.sin(MathHelper.sqrt(p) * (float)Math.PI);
        int side = player.getMainArm() == Arm.RIGHT ? 1 : -1;
        switch (c.vmSwing) {
            case "none" -> translate(matrices, side * .56f, -.52f + lumen$currentEquip * -.6f, -.72f);
            case "mode2" -> mode2(matrices, side, sin);
            case "mode3" -> mode3(matrices, side, sin);
            case "mode4" -> mode4(matrices, side, sqrtSin);
            case "mode5" -> mode5(matrices, side, sqrtSin);
            case "mode6" -> mode6(matrices, side, sin);
            case "mode7" -> mode7(matrices, side, sqrtSin);
            default -> smooth(matrices, side, p, lumen$currentEquip, sqrtSin);
        }
        if (c.vmRotate && p > 0f) {
            if (lumen$rotateStartNanos == 0L) lumen$rotateStartNanos = System.nanoTime();
            long elapsed = (System.nanoTime() - lumen$rotateStartNanos) / 1_000_000L;
            int degrees = (int)(elapsed * 2.4 / Math.max(1, c.vmRotateSpeed) % 720);
            if (degrees >= 360) degrees -= 720;
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
        } else {
            lumen$rotateStartNanos = System.nanoTime();
        }
    }

    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 3, require = 0)
    private float lumenvisuals$equip(float equip) {
        LumenConfig c = LumenConfig.get();
        return c.vm && c.vmNoEquip ? 0f : equip;
    }

    @Unique private static void translate(MatrixStack m, float x, float y, float z) { m.translate(x, y, z); }
    @Unique private static void rx(MatrixStack m, float a) { m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(a)); }
    @Unique private static void ry(MatrixStack m, float a) { m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(a)); }
    @Unique private static void rz(MatrixStack m, float a) { m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(a)); }
    @Unique private static void base(MatrixStack m, int side, float y) { translate(m, side*.56f, y, -.72f); m.scale(.8f,.8f,.8f); }
    @Unique private static void mode2(MatrixStack m,int s,float v){base(m,s,-.35f);rx(m,s*80);ry(m,s*-57);rz(m,-80-70*v);}
    @Unique private static void mode3(MatrixStack m,int s,float v){base(m,s,-.35f);rx(m,s*80);ry(m,s*-35);rz(m,-70-70*v);}
    @Unique private static void mode4(MatrixStack m,int s,float v){base(m,s,-.4f);rz(m,50);rx(m,s*-60);ry(m,s*(110+25*v));}
    @Unique private static void mode5(MatrixStack m,int s,float v){translate(m,s*.56f,-.52f,-.72f);rz(m,-60*v);ry(m,s*-25*v);}
    @Unique private static void mode6(MatrixStack m,int s,float v){base(m,s,-.35f);rx(m,s*25);ry(m,s*-15);rz(m,-70-100*v);}
    @Unique private static void mode7(MatrixStack m,int s,float v){base(m,s,-.35f);rx(m,s*80);ry(m,s*-15);rz(m,-100-70*v);}
    @Unique private static void smooth(MatrixStack m,int s,float p,float equip,float v){
        translate(m,s*.56f,-.52f+equip*-.25f,-.72f);
        rx(m,s*(45+MathHelper.sin(p*p*(float)Math.PI)*-20)); ry(m,s*v*-20); rz(m,v*-80); rx(m,s*-45);
    }
}
