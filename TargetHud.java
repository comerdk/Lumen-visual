package dev.lumen.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

/** Target HUD в стиле liquid glass: голова, ник, плавное HP и дистанция. Настраивается из меню и чата. */
public final class TargetHud {
    private static final int W = 140, H = 46;

    private static float anim, shownHp;
    private static long lastNs = System.nanoTime();
    private static LivingEntity last;

    private TargetHud() {}

    public static void render(DrawContext c, MinecraftClient mc, LumenConfig cfg) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;

        LivingEntity t = cfg.thud ? TargetTracker.get() : null;
        if (t != null) {
            if (t != last) {
                last = t;
                shownHp = t.getHealth();
            }
            anim = Math.min(1f, anim + dt * 6f);
        } else {
            anim = Math.max(0f, anim - dt * 6f);
            if (anim < 0.05f) {
                last = null;
                return;
            }
        }
        if (last == null || anim < 0.05f) return;

        LivingEntity e = last;
        float k = anim;
        float max = Math.max(1f, e.getMaxHealth());
        shownHp += (e.getHealth() - shownHp) * Math.min(1f, dt * 8f);
        float ratio = MathHelper.clamp(shownHp / max, 0f, 1f);

        TextRenderer f = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int accent = cfg.thudArgb;
        float s = cfg.thudScale * (0.9f + 0.1f * k);
        double ps = Gfx.sf() * s;
        int ringPx = Gfx.ringPx(ps);

        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(sw / 2f + cfg.thudX, sh / 2f + cfg.thudY, 0f);
        m.scale(s, s, 1f);

        // панель
        if (!"flat".equals(cfg.thudStyle)) {
            Gfx.rrect(c, -2, -1, W + 4, H + 5, 13, a(0x2E000000, k), a(0x2E000000, k), ps);
            Gfx.rrect(c, 0, 0, W, H, 11, a(Gfx.withA(cfg.themeLight, 0xA8), k), a(Gfx.withA(cfg.themeDark, 0xD2), k), ps);
            Gfx.rrect(c, 1, 1, W - 2, H * 0.45f, 10, a(0x3CFFFFFF, k), a(0x04FFFFFF, k), ps);
            Gfx.ring(c, 0, 0, W, H, 11, ringPx, a(0x78FFFFFF, k), ps);
        } else {
            Gfx.rrect(c, 0, 0, W, H, 8, a(0xE014082E, k), a(0xE014082E, k), ps);
            Gfx.rrect(c, 0, 5, 3, H - 10, 1.5f, a(accent, k), a(accent, k), ps);
        }

        // голова / буква
        int fx = 7, fy = 7, fs = 32;
        Gfx.rrect(c, fx - 1.5f, fy - 1.5f, fs + 3, fs + 3, 5, a(accent, k), a(accent, k), ps);
        String name = e.getName().getString();
        if (cfg.thudSkin && e instanceof AbstractClientPlayerEntity p) {
            PlayerSkinDrawer.draw(c, p.getSkinTextures(), fx, fy, fs);
        } else {
            Gfx.rrect(c, fx, fy, fs, fs, 4, a(0xFF3A1C85, k), a(0xFF1E0A4A, k), ps);
            String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            c.drawCenteredTextWithShadow(f, letter, fx + fs / 2, fy + fs / 2 - 4, a(0xFFFFFFFF, k));
        }

        // ник
        if (name.length() > 14) name = name.substring(0, 13) + "…";
        c.drawText(f, name, 46, 8, a(0xFFFFFFFF, k), true);

        // HP бар (пилюля)
        float bx = 46, by = 21, bw = W - 54, bh = 7;
        Gfx.rrect(c, bx, by, bw, bh, bh / 2f, a(0x77000000, k), a(0x55000000, k), ps);
        if (ratio > 0.01f) {
            int hpRgb = MathHelper.hsvToRgb(ratio / 3f, 0.8f, 1f);
            int hpDark = MathHelper.hsvToRgb(ratio / 3f, 0.9f, 0.75f);
            Gfx.rrect(c, bx, by, Math.max(bh, bw * ratio), bh, bh / 2f,
                    a(0xFF000000 | hpRgb, k), a(0xFF000000 | hpDark, k), ps);
        }

        // HP / дистанция
        int hp = (int) Math.ceil(e.getHealth());
        int absorb = (int) Math.ceil(e.getAbsorptionAmount());
        String hpText = hp + "/" + (int) max + (absorb > 0 ? " +" + absorb : "");
        c.drawText(f, hpText, 46, 32, a(0xFFE6D2FF, k), false);
        String dist = (Math.round(mc.player.distanceTo(e) * 10) / 10f) + "m";
        c.drawText(f, dist, W - 8 - f.getWidth(dist), 32, a(0xFFD9B8FF, k), false);

        m.pop();
    }

    private static int a(int argb, float k) {
        return Gfx.alpha(argb, k);
    }
}
