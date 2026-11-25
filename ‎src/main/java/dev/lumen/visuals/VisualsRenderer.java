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
        if (cfg.customHud) drawHud(c, mc, cfg);
        if (cfg.handFire) drawHandFire(c, mc, cfg);
        TargetHud.render(c, mc, cfg);
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        if (ready) {
            Cosmetics.render(c, mc, cfg, sw, sh);
        }
        // Player Glow независим от ESP-модулей и использует размеры экрана,
        // объявленные до вызова рендера.
        if (cfg.playerGlow && !mc.options.getPerspective().isFirstPerson() && cfg.playerGlowStrength > 0) {
            drawLocalPlayerGlow(c, mc, cfg, sw, sh);
        }
        if (!ready || !(cfg.esp || cfg.tracers || cfg.nametags || cfg.tesp || cfg.predict)) return;
        boolean anyEsp = cfg.esp || cfg.tracers || cfg.nametags;
        boolean fillMode = cfg.esp && "fill".equals(cfg.espMode);
        EntityEsp.begin();

        TextRenderer font = mc.textRenderer;
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

    /** Настраиваемый glass HUD. Надписи READY/SPRINT намеренно не используются. */
    private static void drawHud(DrawContext c, MinecraftClient mc, LumenConfig cfg) {
        TextRenderer f = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        float scale = cfg.hudScale / 100.0f;
        MatrixStack matrices = c.getMatrices();
        matrices.push();
        matrices.translate(cfg.hudX, cfg.hudY, 0);
        matrices.scale(scale, scale, 1.0f);

        int color = 0xFF000000 | cfg.hudRgb;
        int top = Gfx.withA(Gfx.mixRgb(cfg.hudRgb, 0xFFFFFF, 0.25f), 0xE0);
        int bot = Gfx.withA(Gfx.mixRgb(cfg.hudRgb, 0x000000, 0.48f), 0xE8);
        int accent = 0xFFFFFFFF;
        int h = f.fontHeight + 10;
        StringBuilder line = new StringBuilder();
        if (cfg.hudLogo) line.append("LUMEN");
        if (cfg.hudCoords) appendHudPart(line, String.format("X %.1f  Y %.1f  Z %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        if (cfg.hudFps) appendHudPart(line, mc.getCurrentFps() + " FPS");
        if (cfg.hudServer) {
            String server = mc.getCurrentServerEntry() == null ? "Singleplayer" : mc.getCurrentServerEntry().address;
            if (server.length() > 22) server = server.substring(0, 21) + "…";
            appendHudPart(line, server);
        }
        if (cfg.hudClock) {
            long dayTime = mc.world.getTimeOfDay() % 24000L;
            appendHudPart(line, String.format("%02d:%02d", (dayTime / 1000 + 6) % 24, (dayTime % 1000) * 60 / 1000));
        }
        if (cfg.hudSpeed) {
            double vx = mc.player.getVelocity().x, vz = mc.player.getVelocity().z;
            appendHudPart(line, String.format("Speed %.2f m/s", Math.sqrt(vx * vx + vz * vz) * 20.0));
        }

        if (line.length() > 0) {
            String text = line.toString();
            float w = f.getWidth(text) + 20;
            Gfx.rrect(c, 0, 0, w, h, h / 2f, top, bot, Gfx.sf());
            Gfx.ring(c, 0, 0, w, h, h / 2f, Gfx.ringPx(Gfx.sf()), 0x88FFFFFF, Gfx.sf());
            c.drawText(f, text, 10, 5, accent, true);
        }
        matrices.pop();
    }

    private static void appendHudPart(StringBuilder line, String part) {
        if (line.length() > 0) line.append("  •  ");
        line.append(part);
    }

    /** Стилизованный цветной огонь вокруг позиции предмета в первой руке (HUD-слой). */
    private static void drawHandFire(DrawContext c, MinecraftClient mc, LumenConfig cfg) {
        if (mc.options.getPerspective() != net.minecraft.client.option.Perspective.FIRST_PERSON || cfg.handFireStrength <= 0) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int rgb = cfg.handFireRgb & 0xFFFFFF;
        int a = MathHelper.clamp(cfg.handFireStrength * 2, 0, 200);
        boolean left = mc.options.getMainArm().getValue() == net.minecraft.util.Arm.LEFT;
        float cx = sw * (left ? 0.36f : 0.64f);
        float base = sh * 0.88f;
        long time = System.currentTimeMillis();
        for (int i = 0; i < 7; i++) {
            float phase = (float) ((time / 150.0 + i * 1.73) % 6.28);
            float wobble = (float) Math.sin(phase) * 4f;
            float height = 10f + 9f * (0.5f + 0.5f * (float) Math.sin(phase * 1.4));
            float px = cx + (i - 3) * 5.5f + wobble;
            int alpha = (a * (150 + (i % 3) * 25) / 255) << 24;
            int col = alpha | rgb;
            c.fillGradient((int) px, (int) (base - height), (int) (px + 4), (int) base, col, 0x00FFFFFF);
            c.fill((int) px + 1, (int) (base - height * 0.55f), (int) px + 3, (int) base, col);
        }
    }

    private static void drawLocalPlayerGlow(DrawContext c, MinecraftClient mc, LumenConfig cfg, int sw, int sh) {
        PlayerEntity p = mc.player;
        double px = MathHelper.lerp(tickDelta, p.lastRenderX, p.getX());
        double py = MathHelper.lerp(tickDelta, p.lastRenderY, p.getY());
        double pz = MathHelper.lerp(tickDelta, p.lastRenderZ, p.getZ());
        if (!projectBox(p.getBoundingBox(), px - p.getX(), py - p.getY(), pz - p.getZ(), sw, sh)) return;
        int rgb = cfg.playerGlowRgb & 0xFFFFFF;
        int strength = cfg.playerGlowStrength;
        float x1 = BOX[0], y1 = BOX[1], x2 = BOX[2], y2 = BOX[3];
        for (int i = 4; i >= 1; i--) {
            int alpha = Math.min(100, strength * (6 - i) / 7);
            int color = (alpha << 24) | rgb;
            float pad = i * 2.2f;
            c.fill((int)(x1 - pad), (int)(y1 - pad), (int)(x2 + pad), (int)(y2 + pad), color);
        }
        int edge = (Math.min(220, strength * 2) << 24) | rgb;
        c.fill((int)x1 - 1, (int)y1, (int)x1 + 1, (int)y2, edge);
        c.fill((int)x2 - 1, (int)y1, (int)x2 + 1, (int)y2, edge);
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
