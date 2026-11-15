package dev.lumen.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.util.Random;

/** Настраиваемая косметика: нимб над головой, звёзды-аура вокруг игрока и звёздный шлейф. Нимб и аура видны от 3-го лица (F5). */
public final class Cosmetics {
    private static final Random R = new Random();
    private static double lastX, lastY, lastZ;
    private static boolean hasLast;

    private Cosmetics() {}

    static void render(DrawContext c, MinecraftClient mc, LumenConfig cfg, int sw, int sh) {
        if (!VisualsRenderer.ready || mc.player == null || mc.world == null) return;
        if (!(cfg.cosHalo || cfg.cosAura || cfg.cosTrail)) return;

        ClientPlayerEntity p = mc.player;
        float td = VisualsRenderer.tickDelta;
        double px = MathHelper.lerp(td, p.lastRenderX, p.getX());
        double py = MathHelper.lerp(td, p.lastRenderY, p.getY());
        double pz = MathHelper.lerp(td, p.lastRenderZ, p.getZ());
        float h = p.getHeight();
        int rgb = cfg.cosRgb & 0xFFFFFF;
        float size = cfg.cosSize / 100f;
        double t = (System.currentTimeMillis() % 3_600_000L) / 1000.0 * (cfg.cosSpeed / 100.0);

        // шлейф: рождаем звёзды, пока игрок двигается
        if (cfg.cosTrail) {
            if (hasLast) {
                double dx = px - lastX, dy = py - lastY, dz = pz - lastZ;
                if (dx * dx + dy * dy + dz * dz > 0.0004) {
                    for (int i = 0; i < 2; i++) {
                        FxParticles.spawnTrail(px + (R.nextDouble() - 0.5) * 0.4, py + 0.1 + R.nextDouble() * h * 0.9,
                                pz + (R.nextDouble() - 0.5) * 0.4, rgb, 0.07f * size);
                    }
                }
            }
            lastX = px;
            lastY = py;
            lastZ = pz;
            hasLast = true;
        } else {
            hasLast = false;
        }

        boolean third = !mc.options.getPerspective().isFirstPerson();
        if (!third) return;
        Matrix4f m = c.getMatrices().peek().getPositionMatrix();

        if (cfg.cosHalo) {
            double cy = py + h + 0.28 + 0.03 * Math.sin(t * 2.0);
            double rad = 0.28 * size;
            int seg = 40;
            Draw2D.beginTris(c);
            float lx = 0, ly = 0;
            boolean prev = false;
            for (int i = 0; i <= seg; i++) {
                double a = i * (2.0 * Math.PI / seg) + t * 0.6;
                boolean ok = VisualsRenderer.project(px + Math.cos(a) * rad, cy, pz + Math.sin(a) * rad, sw, sh);
                float x = VisualsRenderer.SCR[0], y = VisualsRenderer.SCR[1];
                if (ok && prev) {
                    Draw2D.line(m, lx, ly, x, y, 3.2f, (0x2A << 24) | rgb);
                    Draw2D.line(m, lx, ly, x, y, 1.1f, 0xF0000000 | rgb);
                }
                lx = x;
                ly = y;
                prev = ok;
            }
            Draw2D.endTris();
        }

        if (cfg.cosAura) {
            float focal = VisualsRenderer.focalPx(sw);
            Draw2D.beginTex(c, FxParticles.TEX_STAR, true);
            int count = 9;
            for (int i = 0; i < count; i++) {
                double a = t * 1.1 + i * (2.0 * Math.PI / count);
                double y = py + h * (0.12 + 0.78 * (0.5 + 0.5 * Math.sin(t * 0.9 + i * 1.7)));
                double r = 0.85 * size + 0.1 * Math.sin(t * 1.3 + i);
                if (!VisualsRenderer.project(px + Math.cos(a) * r, y, pz + Math.sin(a) * r, sw, sh)) continue;
                float depth = VisualsRenderer.lastDepth;
                if (depth < 0.4f) continue;
                float rp = MathHelper.clamp(focal * 0.16f * size / depth, 2f, 26f);
                float tw = 0.65f + 0.35f * (float) Math.sin(t * 4.0 + i * 2.1);
                Draw2D.sprite(m, VisualsRenderer.SCR[0], VisualsRenderer.SCR[1], rp, (float) (t * 0.8 + i), (Math.min(255, (int) (230 * tw)) << 24) | rgb);
            }
            Draw2D.endTex();
        }
    }
}
