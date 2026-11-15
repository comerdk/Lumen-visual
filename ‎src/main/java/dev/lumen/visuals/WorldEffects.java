package dev.lumen.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.joml.Matrix4f;

/**
 * «Красивый мир»: время суток, затемнение/обесцвечивание, цветной оверлей, виньетка,
 * небо-плазма и собственные частицы (звёзды, снег, дым). Туман/цвет фона — в BackgroundRendererMixin (fogRgb()).
 */
public final class WorldEffects {
    private static final int[] AURORA = {0x2BFF9A, 0x35C7FF, 0xB05CFF};
    private static final int[] NEBULA = {0x7A3FE0, 0xFF5AD1, 0x4A6BFF};
    private static final int[] SOLAR = {0xFF7A18, 0xFF2E93, 0xFFD23F};
    private static final int[] TOXIC = {0x7CFF00, 0x00FFA3, 0xE6FF3F};

    private static float plasmaFade;
    private static long lastNs = System.nanoTime();

    private WorldEffects() {}

    /** Раз в кадр: фиксируем время суток (сервер его сбрасывает, поэтому каждый кадр). */
    public static void frame(MinecraftClient mc) {
        LumenConfig c = LumenConfig.get();
        if (!c.world || !c.worldTime || mc.world == null) return;
        mc.world.getLevelProperties().setTimeOfDay(c.worldTimeTicks);
    }

    /** Оставлено для совместимости: частицы теперь свои и рисуются в overlay(). */
    public static void tick(MinecraftClient mc) {
    }

    /** Оверлеи, небо-плазма и частицы. */
    public static void overlay(DrawContext c, MinecraftClient mc, LumenConfig cfg) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;
        if (!cfg.world) {
            plasmaFade = 0f;
            FxParticles.clear();
            return;
        }
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float sat = cfg.worldSat / 100f;

        // затемнение и обесцвечивание (приближение: серый оверлей уменьшает насыщенность и контраст)
        if (cfg.worldDark > 0) {
            c.fill(0, 0, sw, sh, ((int) (cfg.worldDark / 100f * 0.75f * 255) << 24));
        }
        if (cfg.worldSat < 100) {
            c.fill(0, 0, sw, sh, (((int) ((1f - sat) * 0.65f * 255)) << 24) | 0x808080);
        } else if (cfg.worldSat > 100) {
            float boost = (sat - 1f); // 0.0 .. 1.0
            int boostCol = Gfx.saturate(cfg.tintRgb, 1.5f);
            int alpha = Math.min(48, (int) (boost * 36));
            c.fill(0, 0, sw, sh, (alpha << 24) | boostCol);
        }
        if (cfg.tintAlpha > 0) {
            c.fill(0, 0, sw, sh, (cfg.tintAlpha << 24) | Gfx.saturate(cfg.tintRgb, sat));
        }
        if (cfg.vignette) {
            int h = sh / 5;
            c.fillGradient(0, 0, sw, h, 0x66100430, 0x00100430);
            c.fillGradient(0, sh - h, sw, sh, 0x00100430, 0x66100430);
        }

        plasma(c, mc, cfg, sw, sh, dt, sat);

        if (VisualsRenderer.ready && mc.player != null && mc.world != null && !mc.isPaused()) {
            FxParticles.update(dt, cfg);
            FxParticles.render(c, cfg, sw, sh);
        }
    }

    /**
     * Небо-плазма: большие светящиеся «облака плазмы» закреплены на небесной сфере
     * (проецируются через камеру, поэтому двигаются вместе с миром), цвета перетекают друг в друга.
     */
    private static void plasma(DrawContext c, MinecraftClient mc, LumenConfig cfg, int sw, int sh, float dt, float sat) {
        boolean on = plasmaActive(cfg) && VisualsRenderer.ready
                && mc.world != null && mc.player != null
                && mc.world.getRegistryKey() == World.OVERWORLD
                && mc.world.isSkyVisible(mc.player.getBlockPos());
        plasmaFade += ((on ? 1f : 0f) - plasmaFade) * Math.min(1f, dt * 3f);
        if (plasmaFade < 0.02f) return;

        float focal = VisualsRenderer.focalPx(sw);
        double t = (System.currentTimeMillis() % 3_600_000L) / 1000.0 * (cfg.plasmaSpeed / 100.0);
        float k = plasmaFade * cfg.plasmaIntensity / 100f;
        Matrix4f m = c.getMatrices().peek().getPositionMatrix();
        double cx = VisualsRenderer.camX, cy = VisualsRenderer.camY, cz = VisualsRenderer.camZ;

        Draw2D.beginTex(c, FxParticles.TEX_GLOW, true);
        int big = 16;
        for (int i = 0; i < big; i++) {
            double az = i * (2.0 * Math.PI / big) + t * 0.05 + 0.6 * Math.sin(t * 0.13 + i * 1.7);
            double el = Math.toRadians(12.0 + 40.0 * (0.5 + 0.5 * Math.sin(t * 0.21 + i * 2.3)));
            if (!skyPoint(cx, cy, cz, az, el, sw, sh)) continue;
            float rad = focal * (0.30f + 0.12f * (float) Math.sin(t * 0.17 + i));
            float tt = 0.5f + 0.5f * (float) Math.sin(t * 0.3 + i * 0.9);
            int rgb = Gfx.saturate(plasmaRgb(cfg.plasma, tt), sat);
            float a = k * (0.10f + 0.12f * (0.5f + 0.5f * (float) Math.sin(t * 0.4 + i)));
            Draw2D.sprite(m, VisualsRenderer.SCR[0], VisualsRenderer.SCR[1], rad, (float) (t * 0.05 + i),
                    (Math.min(255, (int) (a * 255)) << 24) | rgb);
        }
        // яркие «жилки» плазмы, быстро кружащие вокруг больших облаков
        int small = 24;
        for (int i = 0; i < small; i++) {
            double az = i * (2.0 * Math.PI / small) + t * 0.18 + 0.9 * Math.sin(t * 0.31 + i * 1.3);
            double el = Math.toRadians(10.0 + 46.0 * (0.5 + 0.5 * Math.sin(t * 0.37 + i * 1.9)));
            if (!skyPoint(cx, cy, cz, az, el, sw, sh)) continue;
            float rad = focal * (0.06f + 0.05f * (float) Math.sin(t * 0.9 + i * 0.7) * (float) Math.sin(t * 0.9 + i * 0.7));
            float tt = 0.5f + 0.5f * (float) Math.sin(t * 0.5 + i * 1.3);
            int rgb = Gfx.saturate(plasmaRgb(cfg.plasma, 1f - tt), sat);
            float a = k * 0.28f * (0.5f + 0.5f * (float) Math.sin(t * 1.3 + i * 2.0));
            Draw2D.sprite(m, VisualsRenderer.SCR[0], VisualsRenderer.SCR[1], rad, (float) (t + i),
                    (Math.min(255, (int) (a * 255)) << 24) | rgb);
        }
        Draw2D.endTex();
    }

    private static boolean skyPoint(double cx, double cy, double cz, double az, double el, int sw, int sh) {
        double dx = Math.cos(el) * -Math.sin(az), dy = Math.sin(el), dz = Math.cos(el) * Math.cos(az);
        return VisualsRenderer.project(cx + dx * 300.0, cy + dy * 300.0, cz + dz * 300.0, sw, sh);
    }

    public static boolean plasmaActive(LumenConfig c) {
        return !"off".equals(c.plasma) && c.plasmaIntensity > 0;
    }

    /** Итоговый цвет тумана/фона: заданный цвет с учётом насыщенности и «дыхания» плазмы. */
    public static int fogRgb(LumenConfig c) {
        float sat = c.worldSat / 100f;
        int base = Gfx.saturate(c.fogRgb, sat);
        if (plasmaActive(c)) {
            double t = (System.currentTimeMillis() % 3_600_000L) / 1000.0 * (c.plasmaSpeed / 100.0);
            float tt = 0.5f + 0.5f * (float) Math.sin(t * 0.35);
            int p = Gfx.saturate(plasmaRgb(c.plasma, tt), sat);
            base = Gfx.mixRgb(base, p, 0.65f * c.plasmaIntensity / 100f);
        }
        return base;
    }

    private static int plasmaRgb(String variant, float t) {
        int[] p = switch (variant) {
            case "aurora" -> AURORA;
            case "solar" -> SOLAR;
            case "toxic" -> TOXIC;
            default -> NEBULA;
        };
        return t < 0.5f ? Gfx.mixRgb(p[0], p[1], t * 2f) : Gfx.mixRgb(p[1], p[2], (t - 0.5f) * 2f);
    }
}
