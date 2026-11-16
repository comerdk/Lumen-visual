package dev.lumen.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Prediction: рисует траекторию метательных предметов
 * (лук, арбалет, трезубец, зелья, эндер-пёрл) пока держишь их в руке.
 */
public final class TrajectoryPredictor {
    private static final int STEPS = 80;
    private static final double GRAVITY = 0.05;
    private static final double DRAG = 0.99;

    private TrajectoryPredictor() {}

    public static void render(DrawContext c, MinecraftClient mc, LumenConfig cfg, int sw, int sh) {
        if (!cfg.predict || mc.player == null || mc.world == null) return;
        PlayerEntity p = mc.player;
        ItemStack stack = p.getMainHandStack();
        if (stack.isEmpty()) stack = p.getOffHandStack();
        if (stack.isEmpty()) return;

        Item item = stack.getItem();
        float velocity;
        float gravity;
        boolean isArrow = false;

        if (item instanceof BowItem) {
            if (!p.isUsingItem()) return;
            int use = p.getItemUseTime();
            float pull = BowItem.getPullProgress(use);
            if (pull < 0.1f) return;
            velocity = pull * 3.0f;
            gravity = 0.05f;
            isArrow = true;
        } else if (item instanceof CrossbowItem) {
            if (!CrossbowItem.isCharged(stack) && !p.isUsingItem()) return;
            velocity = 3.15f;
            gravity = 0.05f;
            isArrow = true;
        } else if (item instanceof TridentItem) {
            if (!p.isUsingItem()) return;
            velocity = 2.5f;
            gravity = 0.05f;
        } else if (item instanceof EnderPearlItem) {
            velocity = 1.5f;
            gravity = 0.03f;
        } else if (item instanceof ThrowablePotionItem || item instanceof ExperienceBottleItem
                || item instanceof SnowballItem || item instanceof EggItem) {
            velocity = 0.5f;
            gravity = 0.05f;
        } else {
            return;
        }

        float pitch = p.getPitch();
        float yaw = p.getYaw();
        double radYaw = Math.toRadians(yaw);
        double radPitch = Math.toRadians(pitch);

        Vec3d eye = p.getEyePos();
        // небольшое смещение от глаза как у ванильного броска
        double ox = -MathHelper.sin((float) radYaw) * 0.16;
        double oz = MathHelper.cos((float) radYaw) * 0.16;
        double startX = eye.x + ox;
        double startY = eye.y - 0.1;
        double startZ = eye.z + oz;

        double vx = -Math.sin(radYaw) * Math.cos(radPitch) * velocity;
        double vy = -Math.sin(radPitch) * velocity;
        double vz = Math.cos(radYaw) * Math.cos(radPitch) * velocity;

        int rgb = cfg.predictColor & 0xFFFFFF;
        int col = 0xAA000000 | rgb;
        int colDim = 0x55000000 | rgb;
        int impact = 0xEE000000 | rgb;

        float prevSx = Float.NaN, prevSy = Float.NaN;
        double x = startX, y = startY, z = startZ;
        boolean hit = false;

        for (int i = 0; i < STEPS; i++) {
            double nx = x + vx;
            double ny = y + vy;
            double nz = z + vz;

            // коллизия с блоками
            HitResult hr = mc.world.raycast(new RaycastContext(
                    new Vec3d(x, y, z), new Vec3d(nx, ny, nz),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            if (hr.getType() != HitResult.Type.MISS) {
                nx = hr.getPos().x;
                ny = hr.getPos().y;
                nz = hr.getPos().z;
                hit = true;
            }

            boolean ok = VisualsRenderer.project(nx, ny, nz, sw, sh);
            if (ok) {
                float sx = VisualsRenderer.SCR[0];
                float sy = VisualsRenderer.SCR[1];
                if (!Float.isNaN(prevSx)) {
                    int lineCol = (i % 2 == 0) ? col : colDim;
                    line(c, prevSx, prevSy, sx, sy, lineCol, 2);
                }
                prevSx = sx;
                prevSy = sy;
            } else {
                prevSx = Float.NaN;
            }

            if (hit || ny < mc.world.getBottomY() - 16) {
                if (ok) {
                    // точка попадания
                    int s = 4;
                    c.fill((int) prevSx - s, (int) prevSy - 1, (int) prevSx + s + 1, (int) prevSy + 2, impact);
                    c.fill((int) prevSx - 1, (int) prevSy - s, (int) prevSx + 2, (int) prevSy + s + 1, impact);
                }
                break;
            }

            x = nx; y = ny; z = nz;
            vx *= DRAG;
            vy = vy * DRAG - gravity;
            vz *= DRAG;
            if (isArrow) {
                // стрелы чуть сильнее тормозятся по Y в ванили, но для превью достаточно
            }
        }
    }

    private static void line(DrawContext c, float x1, float y1, float x2, float y2, int color, int w) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = MathHelper.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        int steps = Math.max(1, (int) len);
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int px = Math.round(x1 + dx * t);
            int py = Math.round(y1 + dy * t);
            c.fill(px, py, px + w, py + w, color);
        }
    }
}
