package dev.lumen.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

/**
 * «Красивый мир»: время суток, затемнение/обесцвечивание, цветной оверлей, виньетка,
 * цвет настоящего небосвода задаётся через SkyColorMixin; частицы рисуются отдельно.
 */
public final class WorldEffects {
    private static final int[] AURORA = {0x2BFF9A, 0x35C7FF, 0xB05CFF};
    private static final int[] NEBULA = {0x7A3FE0, 0xFF5AD1, 0x4A6BFF};
    private static final int[] SOLAR = {0xFF7A18, 0xFF2E93, 0xFFD23F};
    private static final int[] TOXIC = {0x7CFF00, 0x00FFA3, 0xE6FF3F};

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

    /** Оверлеи атмосферы и частицы. Небо не рисуется поверх HUD/блоков. */
    public static void overlay(DrawContext c, MinecraftClient mc, LumenConfig cfg) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;
        if (!cfg.world) {
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
        }
        if (cfg.tintAlpha > 0) {
            c.fill(0, 0, sw, sh, (cfg.tintAlpha << 24) | Gfx.saturate(cfg.tintRgb, sat));
        }
        if (cfg.vignette) {
            int h = sh / 5;
            c.fillGradient(0, 0, sw, h, 0x66100430, 0x00100430);
            c.fillGradient(0, sh - h, sw, sh, 0x00100430, 0x66100430);
        }


        if (VisualsRenderer.ready && mc.player != null && mc.world != null && !mc.isPaused()) {
            FxParticles.update(dt, cfg);
            FxParticles.render(c, cfg, sw, sh);
        }
    }

    public static boolean plasmaActive(LumenConfig c) {
        // moonlit рисуется своим GL-шейдером, ванильный tint не нужен
        if ("moonlit".equals(c.plasma)) return false;
        return !"off".equals(c.plasma) && c.plasmaIntensity > 0;
    }

    /** Color used by ClientWorld.getSkyColor: real sky dome tint, not a 2D screen effect. */
    public static int plasmaSkyRgb(LumenConfig c) {
        double t = (System.currentTimeMillis() % 3_600_000L) / 1000.0 * (c.plasmaSpeed / 100.0);
        float phase = 0.5f + 0.5f * (float) Math.sin(t * 0.22);
        int a = plasmaRgb(c.plasma, phase);
        int b = plasmaRgb(c.plasma, 1f - phase);
        int animated = Gfx.mixRgb(a, b, 0.35f + 0.25f * (float) Math.sin(t * 0.13));
        return Gfx.mixRgb(animated, c.plasmaRgb, 0.78f);
    }

    /** Итоговый цвет тумана/фона: заданный цвет с учётом насыщенности и «дыхания» плазмы. */
    public static int fogRgb(LumenConfig c) {
        float sat = c.worldSat / 100f;
        int base = Gfx.saturate(c.fogRgb, sat);
        if (plasmaActive(c)) {
            double t = (System.currentTimeMillis() % 3_600_000L) / 1000.0 * (c.plasmaSpeed / 100.0);
            float tt = 0.5f + 0.5f * (float) Math.sin(t * 0.35);
            int p = Gfx.saturate(Gfx.mixRgb(plasmaRgb(c.plasma, tt), c.plasmaRgb, 0.78f), sat);
            base = Gfx.mixRgb(base, p, 0.65f * c.plasmaIntensity / 100f);
        }
        return base;
    }

    private static int plasmaRgb(String variant, float t) {
        if ("moonlit".equals(variant)) return 0x0A1535;
        int[] p = switch (variant) {
            case "aurora" -> AURORA;
            case "solar" -> SOLAR;
            case "toxic" -> TOXIC;
            default -> NEBULA;
        };
        return t < 0.5f ? Gfx.mixRgb(p[0], p[1], t * 2f) : Gfx.mixRgb(p[1], p[2], (t - 0.5f) * 2f);
    }
}
