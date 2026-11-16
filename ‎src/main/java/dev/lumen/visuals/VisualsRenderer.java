package dev.lumen.visuals;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/**
 * 2D-визуалы: сущности проецируются в экранные координаты (без кастомных render layer'ов,
 * без лишних аллокаций в цикле) и рисуются через DrawContext в HUD.
 */
public final class VisualsRenderer {
    private static final int SHADOW = 0xAA000000;
    private static final List<Entity> NO_ENTITIES = List.of();

    private static final Matrix4f PROJ = new Matrix4f();
    private static final Quaternionf ROT_INV = new Quaternionf();
    private static final Vector3f TMP3 = new Vector3f();
    private static final Vector4f TMP4 = new Vector4f();
    static final float[] SCR = new float[2];
    static float lastDepth;
    private static final float[] BOX = new float[4]; // minX, minY, maxX, maxY

    static double camX, camY, camZ;
    static float tickDelta;
    private static float camYaw;
    static boolean ready;

    private VisualsRenderer() {}

    /** Раз в кадр забираем матрицу проекции и поворот камеры. */
    public static void capture(WorldRenderContext ctx) {
        Matrix4f p = ctx.projectionMatrix();
        if (p == null) return;
        PROJ.set(p);
        ROT_INV.set(ctx.camera().getRotation()).conjugate();
        Vec3d pos = ctx.camera().getPos();
        camX = pos.x;
        camY = pos.y;
        camZ = pos.z;
        tickDelta = ctx.tickCounter().getTickDelta(false);
        camYaw = ctx.camera().getYaw();
        ready = true;
    }

    private static boolean safeMode;

    public static void render(DrawContext c, RenderTickCounter counter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (safeMode) {
            // аварийный режим: если красивый HUD упал, показываем простую плашку, чтобы было видно, что мод жив
            if (mc.player != null && mc.world != null && mc.textRenderer != null && !mc.options.hudHidden) {
                c.fill(4, 4, 84, 18, 0xCC3A1C85);
                c.drawText(mc.textRenderer, "Lumen (safe)", 8, 8, 0xFFFFFFFF, true);
            }
            return;
        }
        try {
            renderInner(c, counter);
        } catch (Throwable t) {
            safeMode = true;
            LumenVisualsClient.report("HUD", t);
        }
    }

    private static void renderInner(DrawContext c, RenderTickCounter counter) {
        LumenConfig cfg = LumenConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || mc.options.hudHidden) return;

        WorldEffects.overlay(c, mc, cfg);
        if (cfg.hud) drawHud(c, mc, cfg);
        TargetHud.render(c, mc, cfg);
        if (ready) {
            Cosmetics.render(c, mc, cfg, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
        }
        if (!ready || !(cfg.esp || cfg.tracers || cfg.nametags || cfg.tesp || cfg.predict)) return;
        boolean anyEsp = cfg.esp || cfg.tracers || cfg.nametags;
        boolean fillMode = cfg.esp && "fill".equals(cfg.espMode);
        EntityEsp.begin();

        TextRenderer font = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        double rangeSq = (double) cfg.range * cfg.range;
        float tracerX = sw / 2f;
        float tracerY = cfg.tracersFromCrosshair ? sh / 2f : sh;

        for (Entity e : anyEsp ? mc.world.getEntities() : NO_ENTITIES) {
            if (e == mc.player || !(e instanceof LivingEntity le) || e instanceof ArmorStandEntity) continue;
            if (!cfg.showInvisible && e.isInvisible()) continue;

            int color = colorFor(e, cfg);
            if (color == 0) continue;

            double px = MathHelper.lerp(tickDelta, e.lastRenderX, e.getX());
            double py = MathHelper.lerp(tickDelta, e.lastRenderY, e.getY());
            double pz = MathHelper.lerp(tickDelta, e.lastRenderZ, e.getZ());

            double dx = px - camX, dy = py - camY, dz = pz - camZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > rangeSq) continue;

            if (!projectBox(e.getBoundingBox(), px - e.getX(), py - e.getY(), pz - e.getZ(), sw, sh)) continue;

            int x1 = (int) BOX[0], y1 = (int) BOX[1], x2 = (int) BOX[2], y2 = (int) BOX[3];
            if (x2 < 0 || y2 < 0 || x1 > sw || y1 > sh) continue;

            if (fillMode) {
                EntityEsp.add(le, px, py, pz, distSq, color);
            } else if (cfg.esp) {
                outline(c, x1, y1, x2, y2, color);
                float max = Math.max(1f, le.getMaxHealth());
                float ratio = MathHelper.clamp(le.getHealth() / max, 0f, 1f);
                int bx = x1 - 5;
                int filled = (int) ((y2 - y1) * ratio);
                c.fill(bx - 1, y1 - 1, bx + 3, y2 + 1, SHADOW);
                c.fill(bx, y2 - filled, bx + 2, y2, 0xFF000000 | MathHelper.hsvToRgb(ratio / 3f, 1f, 1f));
            }

            if (cfg.tracers) {
                line(c, tracerX, tracerY, (x1 + x2) / 2f, y2, (color & 0x00FFFFFF) | 0xCC000000);
            }

            if (cfg.nametags) {
                int hp = (int) Math.ceil(le.getHealth());
                int abs = (int) Math.ceil(le.getAbsorptionAmount());
                StringBuilder sb = new StringBuilder(e.getName().getString()).append(' ').append(hp);
                if (abs > 0) sb.append('+').append(abs);
                if (cfg.showDistance) sb.append(" [").append((int) Math.sqrt(distSq)).append("m]");
                String label = sb.toString();
                int tx = (int) ((x1 + x2) / 2f - font.getWidth(label) / 2f);
                int ty = y1 - font.fontHeight - 3;
                c.fill(tx - 2, ty - 1, tx + font.getWidth(label) + 2, ty + font.fontHeight, 0x66000000);
                c.drawText(font, label, tx, ty, 0xFFFFFFFF, true);
            }
        }

        if (fillMode) EntityEsp.flush(c, cfg, sw, sh);
        if (cfg.tesp) drawTargetEsp(c, mc, cfg, sw, sh);
        if (cfg.predict) TrajectoryPredictor.render(c, mc, cfg, sw, sh);
    }

    /** Фокусное расстояние в GUI-пикселях (для размера частиц по расстоянию). */
    static float focalPx(int sw) {
        return PROJ.m00() * sw * 0.5f;
    }

    /** Экранная Y-координата линии горизонта (NaN, если ещё нет кадра). Нужна небу-плазме. */
    static float horizonY(int sw, int sh) {
        if (!ready) return Float.NaN;
        double yaw = Math.toRadians(camYaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        if (!project(camX + fx * 200.0, camY, camZ + fz * 200.0, sw, sh)) return Float.NaN;
        return SCR[1];
    }

    /** Watermark слева сверху + список включённых модулей справа сверху (liquid glass). */
    private static void drawHud(DrawContext c, MinecraftClient mc, LumenConfig cfg) {
        TextRenderer f = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        double ps = Gfx.sf();
        int ring = Gfx.ringPx(ps);
        int top = Gfx.withA(cfg.themeLight, 0xA0);
        int bot = Gfx.withA(cfg.themeDark, 0xCC);

        float w = f.getWidth("Lumen") + 16, h = f.fontHeight + 8;
        Gfx.rrect(c, 4, 4, w, h, h / 2f, top, bot, ps);
        Gfx.ring(c, 4, 4, w, h, h / 2f, ring, 0x66FFFFFF, ps);
        c.drawText(f, "Lumen", 12, 8, 0xFFFFFFFF, true);

        String[] names = {"ESP", "Tracers", "Nametags", "Fullbright", "Target ESP", "Target HUD", "World"};
        boolean[] on = {cfg.esp, cfg.tracers, cfg.nametags, cfg.fullbright, cfg.tesp, cfg.thud, cfg.world};
        float y = 4;
        float rh = f.fontHeight + 6;
        for (int i = 0; i < names.length; i++) {
            if (!on[i]) continue;
            float rw = f.getWidth(names[i]) + 20;
            float x = sw - rw - 4;
            Gfx.rrect(c, x, y, rw, rh, rh / 2f, top, bot, ps);
            Gfx.ring(c, x, y, rw, rh, rh / 2f, ring, 0x55FFFFFF, ps);
            Gfx.rrect(c, x + 6, y + rh / 2f - 2f, 4, 4, 2, 0xFFFFFFFF, 0xFFE4DAFF, ps);
            c.drawText(f, names[i], (int) x + 13, (int) y + 4, 0xFFFFFFFF, true);
            y += rh + 2;
        }
    }

    // ---------- Target ESP (кольцо / 3D-бокс через экранную проекцию) ----------
    private static final int SEG = 36;
    private static final float[] RX = new float[SEG + 1];
    private static final float[] RY = new float[SEG + 1];
    private static final boolean[] RV = new boolean[SEG + 1];
    private static final float[] CX = new float[8];
    private static final float[] CY = new float[8];
    private static final boolean[] CV = new boolean[8];
    private static final int[][] EDGES = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private static void drawTargetEsp(DrawContext c, MinecraftClient mc, LumenConfig cfg, int sw, int sh) {
        LivingEntity t = TargetTracker.get();
        if (t == null || t == mc.player) return;

        double px = MathHelper.lerp(tickDelta, t.lastRenderX, t.getX());
        double py = MathHelper.lerp(tickDelta, t.lastRenderY, t.getY());
        double pz = MathHelper.lerp(tickDelta, t.lastRenderZ, t.getZ());
        double dx = px - camX, dy = py - camY, dz = pz - camZ;
        if (dx * dx + dy * dy + dz * dz > 128.0 * 128.0) return;

        float w = t.getWidth(), h = t.getHeight();
        int col = cfg.tespArgb;
        int glow = (col & 0x00FFFFFF) | 0x40000000;
        String style = cfg.tespStyle;

        if ("crystal".equals(style)) {
            drawCrystals(c, px, py, pz, w, h, cfg, col, sw, sh);
            return;
        }

        if (!"box".equals(style)) {
            double r = w * 0.75 + 0.2;
            double phase = (Math.sin(System.currentTimeMillis() / 450.0) + 1.0) / 2.0;
            ring(c, px, py + phase * h, pz, r, col, glow, sw, sh);
            ring(c, px, py + 0.03, pz, r, (col & 0x00FFFFFF) | 0x77000000, 0, sw, sh);
        }
        if (!"ring".equals(style)) {
            double hw = w / 2.0;
            for (int i = 0; i < 8; i++) {
                double x = px + ((i & 1) == 0 ? -hw : hw);
                double y = py + ((i & 2) == 0 ? 0 : h);
                double z = pz + ((i & 4) == 0 ? -hw : hw);
                CV[i] = project(x, y, z, sw, sh);
                CX[i] = SCR[0];
                CY[i] = SCR[1];
            }
            for (int[] e : EDGES) {
                if (!CV[e[0]] || !CV[e[1]]) continue;
                line(c, CX[e[0]], CY[e[0]], CX[e[1]], CY[e[1]], glow, 4);
                line(c, CX[e[0]], CY[e[0]], CX[e[1]], CY[e[1]], col, 2);
            }
        }
    }

    /** Кристаллы-октаэдры, вращающиеся вокруг цели. */
    private static void drawCrystals(DrawContext c, double px, double py, double pz,
                                     float w, float h, LumenConfig cfg, int col, int sw, int sh) {
        int n = cfg.crystalCount;
        double radius = cfg.crystalRadius * (0.55 + w * 0.35);
        double size = 0.18 * (cfg.crystalSize / 100.0);
        double speed = cfg.crystalSpeed / 100.0;
        double time = System.currentTimeMillis() / 1000.0 * speed;
        int fill = (col & 0x00FFFFFF) | 0x55000000;
        int edge = (col & 0x00FFFFFF) | 0xEE000000;
        int glow = (col & 0x00FFFFFF) | 0x33000000;
        double midY = py + h * 0.55;

        for (int i = 0; i < n; i++) {
            double a = time * 1.4 + i * (Math.PI * 2.0 / n);
            double bob = Math.sin(time * 2.2 + i * 0.9) * 0.12;
            double cx = px + Math.cos(a) * radius;
            double cy = midY + bob;
            double cz = pz + Math.sin(a) * radius;
            // лёгкий наклон по орбите
            double spin = time * 2.0 + i;
            drawOctahedron(c, cx, cy, cz, size, spin, fill, edge, glow, sw, sh);
        }
        // тонкое кольцо-база
        ring(c, px, py + 0.04, pz, radius * 0.92, (col & 0x00FFFFFF) | 0x44000000, 0, sw, sh);
    }

    /** Октаэдр (кристалл): верх/низ + экватор из 4 точек. */
    private static void drawOctahedron(DrawContext c, double x, double y, double z, double s,
                                       double spin, int fill, int edge, int glow, int sw, int sh) {
        double cs = Math.cos(spin), sn = Math.sin(spin);
        // вершины в локальных координатах
        double[][] v = {
                {0, s * 1.35, 0},          // top
                {0, -s * 1.35, 0},         // bottom
                {s * cs, 0, s * sn},
                {-s * sn, 0, s * cs},
                {-s * cs, 0, -s * sn},
                {s * sn, 0, -s * cs}
        };
        float[] sx = new float[6], sy = new float[6];
        boolean[] ok = new boolean[6];
        for (int i = 0; i < 6; i++) {
            ok[i] = project(x + v[i][0], y + v[i][1], z + v[i][2], sw, sh);
            sx[i] = SCR[0];
            sy[i] = SCR[1];
        }
        // рёбра: top/bottom к экватору + кольцо экватора
        int[][] edges = {
                {0, 2}, {0, 3}, {0, 4}, {0, 5},
                {1, 2}, {1, 3}, {1, 4}, {1, 5},
                {2, 3}, {3, 4}, {4, 5}, {5, 2}
        };
        for (int[] e : edges) {
            if (!ok[e[0]] || !ok[e[1]]) continue;
            line(c, sx[e[0]], sy[e[0]], sx[e[1]], sy[e[1]], glow, 3);
            line(c, sx[e[0]], sy[e[0]], sx[e[1]], sy[e[1]], edge, 1);
        }
        // лёгкая «заливка» — крест через центр
        if (ok[0] && ok[1]) line(c, sx[0], sy[0], sx[1], sy[1], fill, 2);
        if (ok[2] && ok[4]) line(c, sx[2], sy[2], sx[4], sy[4], fill, 1);
        if (ok[3] && ok[5]) line(c, sx[3], sy[3], sx[5], sy[5], fill, 1);
    }

    private static void ring(DrawContext c, double cx, double y, double cz, double r, int col, int glow, int sw, int sh) {
        for (int i = 0; i <= SEG; i++) {
            double a = i * (2.0 * Math.PI / SEG);
            RV[i] = project(cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, sw, sh);
            RX[i] = SCR[0];
            RY[i] = SCR[1];
        }
        for (int i = 0; i < SEG; i++) {
            if (!RV[i] || !RV[i + 1]) continue;
            if (glow != 0) line(c, RX[i], RY[i], RX[i + 1], RY[i + 1], glow, 4);
            line(c, RX[i], RY[i], RX[i + 1], RY[i + 1], col, 2);
        }
    }

    private static int colorFor(Entity e, LumenConfig cfg) {
        if (e instanceof PlayerEntity) return cfg.espPlayers ? cfg.playerArgb : 0;
        if (e instanceof Monster) return cfg.espHostile ? cfg.hostileArgb : 0;
        return cfg.espPassive ? cfg.passiveArgb : 0;
    }

    static boolean project(double x, double y, double z, int sw, int sh) {
        TMP3.set((float) (x - camX), (float) (y - camY), (float) (z - camZ));
        ROT_INV.transform(TMP3);
        TMP4.set(TMP3.x, TMP3.y, TMP3.z, 1f);
        PROJ.transform(TMP4);
        lastDepth = TMP4.w;
        if (TMP4.w <= 0.01f) return false;
        float nx = TMP4.x / TMP4.w;
        float ny = TMP4.y / TMP4.w;
        SCR[0] = (nx * 0.5f + 0.5f) * sw;
        SCR[1] = (1f - (ny * 0.5f + 0.5f)) * sh;
        return true;
    }

    private static boolean projectBox(Box b, double ox, double oy, double oz, int sw, int sh) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = ((i & 1) == 0 ? b.minX : b.maxX) + ox;
            double y = ((i & 2) == 0 ? b.minY : b.maxY) + oy;
            double z = ((i & 4) == 0 ? b.minZ : b.maxZ) + oz;
            if (!project(x, y, z, sw, sh)) return false;
            if (SCR[0] < minX) minX = SCR[0];
            if (SCR[0] > maxX) maxX = SCR[0];
            if (SCR[1] < minY) minY = SCR[1];
            if (SCR[1] > maxY) maxY = SCR[1];
        }
        BOX[0] = minX;
        BOX[1] = minY;
        BOX[2] = maxX;
        BOX[3] = maxY;
        return true;
    }

    private static void outline(DrawContext c, int x1, int y1, int x2, int y2, int color) {
        // тень
        c.fill(x1 - 1, y1 - 1, x2 + 2, y1 + 2, SHADOW);
        c.fill(x1 - 1, y2 - 1, x2 + 2, y2 + 2, SHADOW);
        c.fill(x1 - 1, y1 - 1, x1 + 2, y2 + 2, SHADOW);
        c.fill(x2 - 1, y1 - 1, x2 + 2, y2 + 2, SHADOW);
        // цвет
        c.fill(x1, y1, x2 + 1, y1 + 1, color);
        c.fill(x1, y2, x2 + 1, y2 + 1, color);
        c.fill(x1, y1, x1 + 1, y2 + 1, color);
        c.fill(x2, y1, x2 + 1, y2 + 1, color);
    }

    private static void line(DrawContext c, float x1, float y1, float x2, float y2, int color) {
        line(c, x1, y1, x2, y2, color, 1);
    }

    private static void line(DrawContext c, float x1, float y1, float x2, float y2, int color, int th) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x1, y1, 0f);
        m.multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(dy, dx)));
        c.fill(0, -(th / 2), (int) len, th - th / 2, color);
        m.pop();
    }
}
